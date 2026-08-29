package rpg.core.statistics;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;

/**
 * Schema für {@code statistics.yml} (contracts/stats-config.md).
 *
 * <p><b>Jede Meldung nennt Datei, Schlüssel und Grund</b> (FR-066) — nach dem Muster von
 * {@code MobConfigSchema} und {@code ItemConfigSchema}. Eine Meldung, die nur sagt, dass etwas
 * falsch ist, verlangt vom Leser, die Datei selbst zu durchsuchen, und zwar an dem Tag, an dem der
 * Server nicht startet.
 *
 * <h2>Drei Prüfungen sind Regeln, keine Zahlenwahl</h2>
 *
 * <p>Sie sichern Zusagen, die sonst durch eine spätere Balancing-Änderung still verloren gingen —
 * dasselbe Vorgehen, mit dem B11 die Ordnung „ein Tod wiegt schwerer als viele Treffer" zur
 * Startprüfung gemacht hat:
 *
 * <ul>
 *   <li><b>Die Schwelle liegt echt zwischen null und eins.</b> Bei null bekäme jeder Streiftreffer
 *       einen Bosskill gutgeschrieben, über eins griffe sie nie (FR-007d).
 *   <li><b>Der Saisonkalender ist lückenlos und überschneidungsfrei</b>, geprüft in
 *       {@link SeasonCalendar} (FR-049).
 *   <li><b>Die Gewichtung nennt mindestens eine bekannte, öffentliche Aggregation</b> (FR-050c,
 *       FR-050d). Ohne Eintrag hätte jedes Konto null Punkte, und die Saison kürte den
 *       Erstbesten.
 * </ul>
 *
 * <h2>Was hier absichtlich NICHT geprüft wird</h2>
 *
 * <p>Ob eine Vorlagen-ID in einer Belohnung bei B11 existiert und ob die Hologrammwelt geladen ist.
 * Beides braucht Wissen, das dieses Paket nicht hat. Die Prüfungen laufen beim Start in
 * {@link StatisticsModule} und brechen ihn genauso ab — dieselbe Aufteilung, die
 * {@code ItemConfigSchema} trifft.
 *
 * <h2>Dauern stehen als Zahl mit Einheit im Schlüssel</h2>
 *
 * <p>{@code idle-after-seconds: 300}, nicht {@code idle-after: 5m}. Der Entwurf des Vertrags hatte
 * die zweite Form; jede andere Konfiguration dieses Projekts benutzt die erste
 * ({@code cooldown-ms}, {@code cleanup-after-seconds}, {@code respawn-minutes}). Ein zweiter
 * Dauernbegriff für eine einzige Datei wäre eine Ausnahme, die jeder Leser einmal nachschlagen
 * muss.
 */
public final class StatisticsConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private static final String FILE = "statistics.yml";

    private StatisticsConfigSchema() {}

    public static ConfigSchema<StatisticsConfig> schema() {
        ConfigSchema.Builder<StatisticsConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        builder.required("capture", FieldType.MAP);
        builder.required("leaderboards", FieldType.MAP);
        builder.required("seasons", FieldType.LIST);
        builder.required("score", FieldType.MAP);
        builder.optional("rewards", FieldType.MAP, Map.of());
        // Vorgabe ist die leere Abbildung, nicht null: MapConfigView wirft beim Lesen eines
        // null-Wertes, und "der Abschnitt fehlt" ist hier kein Fehler, sondern der Normalfall.
        builder.optional("hologram", FieldType.MAP, Map.of());
        return builder.boundTo(StatisticsConfigSchema::bind).build();
    }

    private static StatisticsConfig bind(ConfigView view) {
        return new StatisticsConfig(
                readCapture(view.getMap("capture")),
                readLeaderboards(view.getMap("leaderboards")),
                readSeasons(view.getList("seasons")),
                readScore(view.getMap("score")),
                readRewards(view.getMap("rewards")),
                readHologram(view.getMap("hologram")));
    }

    // ------------------------------------------------------------------ capture

    private static StatisticsConfig.Capture readCapture(Map<?, ?> body) {
        double share = requireDouble(body, "kill-credit-share", "capture");
        if (share <= 0 || share >= 1) {
            throw new IllegalArgumentException(
                    FILE
                            + ": capture.kill-credit-share ist "
                            + share
                            + " - erlaubt ist echt zwischen 0 und 1. Eine Null gaebe jedem"
                            + " Streiftreffer einen Kill, ein Wert ab 1 griffe nie (FR-007d)");
        }
        return new StatisticsConfig.Capture(
                share, requireDuration(body, "idle-after-seconds", "capture"));
    }

    // ------------------------------------------------------------- leaderboards

    private static StatisticsConfig.Leaderboards readLeaderboards(Map<?, ?> body) {
        int places = requireInt(body, "places", "leaderboards");
        if (places <= 0) {
            throw new IllegalArgumentException(
                    FILE + ": leaderboards.places ist " + places + " - erlaubt ist groesser als 0");
        }
        return new StatisticsConfig.Leaderboards(
                requireDuration(body, "refresh-interval-seconds", "leaderboards"), places);
    }

    // ------------------------------------------------------------------ seasons

    private static SeasonCalendar readSeasons(List<?> raw) {
        List<SeasonCalendar.Season> seasons = new ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> body)) {
                throw new IllegalArgumentException(
                        FILE + ": seasons enthaelt einen Eintrag, der keine Abbildung ist");
            }
            String key = requireString(body, "key", "seasons");
            seasons.add(
                    new SeasonCalendar.Season(
                            key,
                            requireDate(body, "from", "seasons[" + key + "]"),
                            requireDate(body, "to", "seasons[" + key + "]")));
        }
        return SeasonCalendar.of(seasons);
    }

    // -------------------------------------------------------------------- score

    private static StatisticsConfig.Score readScore(Map<?, ?> body) {
        Object raw = body.get("weights");
        if (!(raw instanceof Map<?, ?> weightBody) || weightBody.isEmpty()) {
            throw new IllegalArgumentException(
                    FILE
                            + ": score.weights fehlt oder ist leer - ohne einen einzigen Eintrag"
                            + " haette jedes Konto null Punkte, und die Saison kuerte den"
                            + " Erstbesten (FR-050d)");
        }

        Map<Aggregation, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : weightBody.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Aggregation aggregation =
                    Aggregation.byKey(key)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    FILE
                                                            + ": score.weights."
                                                            + key
                                                            + " ist keine bekannte Rangliste -"
                                                            + " bekannt sind "
                                                            + Aggregation.all().keySet()));
            if (!aggregation.scoreable()) {
                throw new IllegalArgumentException(
                        FILE
                                + ": score.weights."
                                + key
                                + (aggregation.isState()
                                        ? " ist ein Zustandswert - er truege den Fortschritt"
                                                + " vergangener Saisons in die laufende, und eine"
                                                + " Saison hoerte auf, ein Neuanfang zu sein"
                                        : " ist nicht oeffentlich - eine Platzierung darf sich"
                                                + " nicht aus Zahlen begruenden, die niemand"
                                                + " nachsehen kann")
                                + " (FR-050c, ADR-046)");
            }
            double weight = toNumber(entry.getValue(), "score.weights." + key).doubleValue();
            if (weight < 0) {
                throw new IllegalArgumentException(
                        FILE + ": score.weights." + key + " ist " + weight + " - negativ ist unzulaessig");
            }
            weights.put(aggregation, weight);
        }
        return new StatisticsConfig.Score(weights);
    }

    // ------------------------------------------------------------------ rewards

    private static Map<Integer, StatisticsConfig.Reward> readRewards(Map<?, ?> body) {
        Map<Integer, StatisticsConfig.Reward> rewards = new LinkedHashMap<>();
        if (body == null) {
            return rewards;
        }
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            int place = toNumber(entry.getKey(), "rewards").intValue();
            if (place <= 0) {
                throw new IllegalArgumentException(
                        FILE + ": rewards." + place + " - ein Platz ist groesser als 0");
            }
            if (!(entry.getValue() instanceof Map<?, ?> rewardBody)) {
                throw new IllegalArgumentException(
                        FILE + ": rewards." + place + " ist keine Abbildung");
            }
            rewards.put(place, readReward(rewardBody, place));
        }
        return rewards;
    }

    private static StatisticsConfig.Reward readReward(Map<?, ?> body, int place) {
        long coins = body.containsKey("coins") ? requireLong(body, "coins", "rewards." + place) : 0L;
        if (coins < 0) {
            throw new IllegalArgumentException(
                    FILE + ": rewards." + place + ".coins ist negativ");
        }

        List<StatisticsConfig.ItemGrant> items = new ArrayList<>();
        if (body.get("items") instanceof List<?> rawItems) {
            for (Object item : rawItems) {
                if (!(item instanceof Map<?, ?> itemBody)) {
                    throw new IllegalArgumentException(
                            FILE + ": rewards." + place + ".items enthaelt keine Abbildung");
                }
                String template = requireString(itemBody, "template", "rewards." + place + ".items");
                int amount =
                        itemBody.containsKey("amount")
                                ? requireInt(itemBody, "amount", "rewards." + place + ".items")
                                : 1;
                if (amount <= 0) {
                    throw new IllegalArgumentException(
                            FILE
                                    + ": rewards."
                                    + place
                                    + ".items."
                                    + template
                                    + ".amount ist "
                                    + amount
                                    + " - erlaubt ist groesser als 0");
                }
                items.add(new StatisticsConfig.ItemGrant(template, amount));
            }
        }

        if (coins == 0 && items.isEmpty()) {
            throw new IllegalArgumentException(
                    FILE
                            + ": rewards."
                            + place
                            + " vergibt weder Coins noch Gegenstaende - ein leerer Anspruch waere"
                            + " eine Zusage, die beim Einloesen nichts tut");
        }
        return new StatisticsConfig.Reward(coins, items);
    }

    // ----------------------------------------------------------------- hologram

    private static Optional<StatisticsConfig.Hologram> readHologram(Map<?, ?> body) {
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        String boardKey = requireString(body, "board", "hologram");
        Aggregation board =
                Aggregation.byKey(boardKey)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                FILE
                                                        + ": hologram.board '"
                                                        + boardKey
                                                        + "' ist keine bekannte Rangliste -"
                                                        + " bekannt sind "
                                                        + Aggregation.all().keySet()));
        if (board.visibility() != MetricVisibility.PUBLIC) {
            throw new IllegalArgumentException(
                    FILE
                            + ": hologram.board '"
                            + boardKey
                            + "' ist privat - ein Hologramm im Hub zeigt sie allen (FR-037)");
        }

        String periodKey = requireString(body, "period", "hologram");
        Period period = parsePeriod(periodKey);
        if (!period.fits(board.source().kind())) {
            throw new IllegalArgumentException(
                    FILE + ": hologram.period '" + periodKey + "' passt nicht zu '" + boardKey + "'");
        }

        int places = body.containsKey("places") ? requireInt(body, "places", "hologram") : 10;
        if (places <= 0) {
            throw new IllegalArgumentException(
                    FILE + ": hologram.places ist " + places + " - erlaubt ist groesser als 0");
        }

        return Optional.of(
                new StatisticsConfig.Hologram(
                        requireString(body, "world", "hologram"),
                        requireDouble(body, "x", "hologram"),
                        requireDouble(body, "y", "hologram"),
                        requireDouble(body, "z", "hologram"),
                        board,
                        period,
                        places));
    }

    private static Period parsePeriod(String raw) {
        for (Period period : Period.values()) {
            if (period.name().equalsIgnoreCase(raw.replace('-', '_'))) {
                return period;
            }
        }
        throw new IllegalArgumentException(
                FILE
                        + ": hologram.period '"
                        + raw
                        + "' ist kein Zeitraum - bekannt sind day, week, season, all_time");
    }

    // ------------------------------------------------------------------ helpers

    private static String requireString(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(FILE + ": " + where + "." + field + " fehlt");
        }
        return String.valueOf(value);
    }

    private static LocalDate requireDate(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(FILE + ": " + where + "." + field + " fehlt");
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        // SnakeYAML erkennt 2026-07-01 selbst als Datum und liefert java.util.Date - nicht die
        // Zeichenkette, die hier zu erwarten waere. Ohne diesen Zweig scheiterte ausgerechnet die
        // AUSGELIEFERTE Datei, waehrend jeder Schematest mit seinem selbstgebauten Dokument
        // durchliefe.
        if (value instanceof java.util.Date legacy) {
            return legacy.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException malformed) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + " ist '"
                            + value
                            + "' - erwartet wird ein Datum der Form 2026-07-01",
                    malformed);
        }
    }

    private static Duration requireDuration(Map<?, ?> body, String field, String where) {
        long seconds = requireLong(body, field, where);
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + " ist "
                            + seconds
                            + " - erlaubt ist groesser als 0");
        }
        return Duration.ofSeconds(seconds);
    }

    private static int requireInt(Map<?, ?> body, String field, String where) {
        return (int) requireLong(body, field, where);
    }

    private static long requireLong(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).longValue();
    }

    private static double requireDouble(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).doubleValue();
    }

    private static Number requireNumber(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(FILE + ": " + where + "." + field + " fehlt");
        }
        return toNumber(value, where + "." + field);
    }

    private static Number toNumber(Object value, String where) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    FILE + ": " + where + " ist '" + value + "' - erwartet wird eine Zahl",
                    notANumber);
        }
    }
}

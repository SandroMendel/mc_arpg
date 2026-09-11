package rpg.plugin.command.framework;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import rpg.core.item.Items;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.session.CharacterClass;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.MetricVisibility;
import rpg.core.statistics.Period;

/**
 * Die Argumenttypen — <b>jeder prüft und schlägt vor</b> (T016, FR-002).
 *
 * <p>Eine Fabrik, keine Aufzählung: {@code LEVEL} braucht B06s Höchststufe, {@code AMOUNT} seine
 * Grenzen. Ein {@code enum} könnte beides nicht tragen, ohne die Werte irgendwo festzuschreiben —
 * und festgeschriebene Zahlen sind genau das, was Prinzip V verbietet.
 *
 * <p>Die registrygebundenen Typen ({@code PLAYER}, {@code ITEM_TEMPLATE}, {@code MOB_KIND},
 * {@code CHARACTER_CLASS}) kommen in T017 und T018 dazu.
 */
public final class Arguments {

    private Arguments() {}

    /**
     * Eine ganze Zahl mit Ober- und Untergrenze.
     *
     * <p>Keine Vorschläge — eine Zahl vorzuschlagen hieße raten, welche gemeint ist.
     */
    public static ArgumentType<Long> amount(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " liegt ueber max " + max);
        }
        return new ArgumentType<>() {

            @Override
            public Long parse(String raw) throws ArgumentRejected {
                long value;
                try {
                    value = Long.parseLong(raw.trim());
                } catch (NumberFormatException notANumber) {
                    throw ArgumentRejected.invalid("amount", raw, expected());
                }
                if (value < min || value > max) {
                    throw ArgumentRejected.outOfRange(
                            "amount", raw, String.valueOf(min), String.valueOf(max));
                }
                return value;
            }

            @Override
            public List<String> suggest(String partial) {
                return List.of();
            }

            @Override
            public String expected() {
                return min + "-" + max;
            }
        };
    }

    /**
     * Eine ganze Zahl ohne Obergrenze — <b>„mindestens n", nicht „zwischen n und 2^63−1"</b>.
     *
     * <p>Auf dem Server gefunden (2026-09-06): {@code xp give Ticoo 0} antwortete
     * <em>„amount must be between 1 and 9223372036854775807"</em>. Die Zahl ist richtig und
     * vollkommen nutzlos — sie erzwingt, dass der Betreiber sie liest, um festzustellen, dass sie
     * nichts bedeutet. FR-004 will den <b>erlaubten Bereich</b> genannt haben, und der ist hier
     * „ab 1".
     *
     * <p>Deshalb ein eigener Typ statt {@code amount(1, Long.MAX_VALUE)}: eine Obergrenze, die es
     * nicht gibt, soll auch nicht dastehen.
     */
    public static ArgumentType<Long> atLeast(String name, long min) {
        Objects.requireNonNull(name, "name");
        return new ArgumentType<>() {

            @Override
            public Long parse(String raw) throws ArgumentRejected {
                long value;
                try {
                    value = Long.parseLong(raw.trim());
                } catch (NumberFormatException notANumber) {
                    throw ArgumentRejected.invalid(name, raw, expected());
                }
                if (value < min) {
                    throw ArgumentRejected.invalid(name, raw, expected());
                }
                return value;
            }

            @Override
            public List<String> suggest(String partial) {
                return List.of();
            }

            @Override
            public String expected() {
                return min + "+";
            }
        };
    }

    /**
     * Eine Stufe — <b>1 bis zu B06s Höchststufe, gefragt statt festgeschrieben</b>.
     *
     * <p>{@code tasks.md} T016 schreibt „{@code LEVEL} (1..60)". Die 60 steht aber nirgends im Code:
     * {@code Progression.maxLevel()} kommt aus {@code progression.yml} über {@code XpCurve}. Eine
     * fest verdrahtete 60 wäre ein Prinzip-V-Verstoß und — schlimmer — nach der ersten Änderung an
     * der Kurve <b>still falsch</b>: die Vervollständigung nähme Stufe 61 nicht an, das Spiel schon.
     *
     * @param maxLevel gefragt bei jedem Aufruf, damit ein {@code /rpg reload} sofort durchschlägt
     */
    public static ArgumentType<Integer> level(IntSupplier maxLevel) {
        Objects.requireNonNull(maxLevel, "maxLevel");
        return new ArgumentType<>() {

            @Override
            public Integer parse(String raw) throws ArgumentRejected {
                int max = maxLevel.getAsInt();
                int value;
                try {
                    value = Integer.parseInt(raw.trim());
                } catch (NumberFormatException notANumber) {
                    throw ArgumentRejected.invalid("level", raw, "1-" + max);
                }
                if (value < 1 || value > max) {
                    throw ArgumentRejected.outOfRange("level", raw, "1", String.valueOf(max));
                }
                return value;
            }

            @Override
            public List<String> suggest(String partial) {
                return List.of();
            }

            @Override
            public String expected() {
                return "1-" + maxLevel.getAsInt();
            }
        };
    }

    /**
     * Ein Zeitraum, wie {@code /stats} und {@code /top} ihn heute kennen.
     *
     * <p><b>Der Fall, für den FR-002 geschrieben wurde.</b> {@code StatisticsCommand} prüft heute in
     * {@code periodOf(raw)} und schlägt in {@code onTabComplete} über eine zweite Schleife vor.
     * Hier ist es eine Aufzählung und zwei Methoden daneben, die beide aus ihr lesen.
     */
    public static ArgumentType<Period> period() {
        return fromEnum("period", Period.class);
    }

    /**
     * Eine Zeitspanne für die Audit-Abfrage — {@code 30m}, {@code 12h}, {@code 7d}.
     *
     * <p>Eigenes Format statt ISO-8601: niemand tippt {@code PT12H} in eine Serverkonsole.
     */
    public static ArgumentType<Duration> duration(Duration max) {
        Objects.requireNonNull(max, "max");
        return new ArgumentType<>() {

            @Override
            public Duration parse(String raw) throws ArgumentRejected {
                String text = raw.trim().toLowerCase(Locale.ROOT);
                if (text.length() < 2) {
                    throw ArgumentRejected.invalid("duration", raw, expected());
                }
                char unit = text.charAt(text.length() - 1);
                long count;
                try {
                    count = Long.parseLong(text.substring(0, text.length() - 1));
                } catch (NumberFormatException notANumber) {
                    throw ArgumentRejected.invalid("duration", raw, expected());
                }
                if (count <= 0) {
                    throw ArgumentRejected.invalid("duration", raw, expected());
                }
                Duration parsed =
                        switch (unit) {
                            case 'm' -> Duration.ofMinutes(count);
                            case 'h' -> Duration.ofHours(count);
                            case 'd' -> Duration.ofDays(count);
                            default -> null;
                        };
                if (parsed == null) {
                    throw ArgumentRejected.invalid("duration", raw, expected());
                }
                if (parsed.compareTo(max) > 0) {
                    throw ArgumentRejected.outOfRange("duration", raw, "1m", format(max));
                }
                return parsed;
            }

            @Override
            public List<String> suggest(String partial) {
                return filtered(List.of("30m", "1h", "6h", "12h", "1d", "7d"), partial);
            }

            @Override
            public String expected() {
                return "30m|1h|6h|12h|1d|7d";
            }
        };
    }

    /**
     * Ein Spieler — <b>online oder offline</b> (T017).
     *
     * <p>Der wichtigste Typ, und der einzige, bei dem ein Tippfehler früher stillschweigend
     * durchging. {@code getOfflinePlayer(name)} gibt <em>immer</em> etwas zurück, auch für
     * {@code "Steave"}: ein leeres Profil mit frisch erfundener UUID. Wer darauf einen Kontostand
     * setzt, hat ihn einem Spieler gegeben, den es nicht gibt, und niemand erfährt davon.
     *
     * <p>{@code hasPlayedBefore() || isOnline()} trennt die zwei Fälle. Das Muster hat B12 gefunden;
     * es stand seither <b>einmal</b> in {@code RpgPlugin}, während {@code CoinsCommand} daneben
     * {@code getPlayerExact} benutzte und damit jeden Offline-Spieler ablehnte. Hier steht es
     * einmal für alle.
     *
     * <p>Vorgeschlagen werden nur die <b>online</b> Anwesenden: der Namens-Cache eines gewachsenen
     * Servers hat Zehntausende Einträge, und die schickt niemand je Tastendruck durch die Leitung.
     * Wer einen Offline-Spieler meint, tippt seinen Namen aus — angenommen wird er trotzdem.
     */
    public static ArgumentType<OfflinePlayer> player(Server server) {
        Objects.requireNonNull(server, "server");
        return new ArgumentType<>() {

            @Override
            public OfflinePlayer parse(String raw) throws ArgumentRejected {
                String name = raw.trim();
                Player online = server.getPlayerExact(name);
                if (online != null) {
                    return online;
                }
                OfflinePlayer known = server.getOfflinePlayer(name);
                if (known.hasPlayedBefore() || known.isOnline()) {
                    return known;
                }
                throw ArgumentRejected.unknownPlayer(name);
            }

            @Override
            public List<String> suggest(String partial) {
                List<String> names = new ArrayList<>();
                for (Player player : server.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filtered(names, partial);
            }

            @Override
            public String expected() {
                return "player";
            }
        };
    }

    /**
     * Entweder ein Zeitraum oder ein Spielername — das erste Argument von {@code /stats} (T035).
     *
     * <p><b>Die Mehrdeutigkeit ist vorhanden und bleibt es</b> (B12-FR-044): {@code /stats week}
     * meint den eigenen Zeitraum, {@code /stats Sandro} das fremde Profil. Wer sie auflöst, braucht
     * <em>eine</em> Regel, und die lautet seit B12: <b>ein Wort, das ein Zeitraum ist, ist ein
     * Zeitraum</b> — sonst suchte {@code /stats week} nach einem Spieler namens „week".
     *
     * <p>Warum das ein <em>Typ</em> ist und keine zwei Zweige im Baum: zwei Geschwisterknoten, die
     * beide ein Wort entgegennehmen, sind für Brigadier nicht unterscheidbar. Es nähme den ersten,
     * der parst — das wäre eine Reihenfolge im Baum statt einer Regel, und niemand sähe ihr an, dass
     * sie eine ist. Als Typ steht die Regel an einer Stelle, und die Vorschläge kommen aus derselben.
     */
    public static ArgumentType<PeriodOrPlayer> periodOrPlayer(Server server) {
        Objects.requireNonNull(server, "server");
        ArgumentType<Period> periods = period();
        ArgumentType<OfflinePlayer> players = player(server);
        return new ArgumentType<>() {

            @Override
            public PeriodOrPlayer parse(String raw) throws ArgumentRejected {
                try {
                    return new PeriodOrPlayer(periods.parse(raw), null);
                } catch (ArgumentRejected notAPeriod) {
                    // Absichtlich verschluckt: dass es kein Zeitraum ist, ist hier kein Fehler,
                    // sondern die halbe Antwort. Ist es auch kein Spieler, meldet der zweite
                    // Versuch das - als "unbekannter Spieler", was der Lage entspricht.
                    return new PeriodOrPlayer(null, players.parse(raw));
                }
            }

            @Override
            public List<String> suggest(String partial) {
                List<String> all = new ArrayList<>(periods.suggest(partial));
                all.addAll(players.suggest(partial));
                return filtered(all, partial);
            }

            @Override
            public String expected() {
                return periods.expected() + "|player";
            }
        };
    }

    /**
     * Das Ergebnis von {@link #periodOrPlayer} — genau eines von beiden ist gesetzt.
     *
     * @param period der Zeitraum, oder {@code null}
     * @param player der Spieler, oder {@code null}
     */
    public record PeriodOrPlayer(Period period, OfflinePlayer player) {

        /** Ob ein Zeitraum gemeint war. */
        public boolean isPeriod() {
            return period != null;
        }
    }

    /**
     * Eine <b>öffentliche</b> Rangliste oder die Saisonwertung — das erste Argument von
     * {@code /top} (T036).
     *
     * <p>{@code score} ist keine Rangliste, sondern ein eigenes Fenster mit einer eigenen Einheit
     * (Punkte). Es unter die Ranglisten zu mischen hieße, es zu beschriften, als wäre es eine
     * Metrik unter anderen (B12-FR-050e) — deshalb steht es hier daneben und nicht darin.
     *
     * <p><b>Private Ranglisten werden abgelehnt, nicht stillschweigend ersetzt.</b> Bisher fiel
     * {@code boardOf} für einen unbekannten <em>und</em> für einen privaten Schlüssel auf
     * {@code MOB_KILLS} zurück: wer {@code /top playtime_online} tippte, bekam die Kill-Tafel und
     * erfuhr nie, warum. Das ist die Sorte Antwort, die dieser Block abschafft (FR-004).
     */
    public static ArgumentType<BoardChoice> leaderboard() {
        return new ArgumentType<>() {

            @Override
            public BoardChoice parse(String raw) throws ArgumentRejected {
                String wanted = raw.trim().toLowerCase(Locale.ROOT);
                if (SEASON_SCORE.equals(wanted)) {
                    return new BoardChoice(null, true);
                }
                return Aggregation.byKey(wanted)
                        .filter(board -> board.visibility() == MetricVisibility.PUBLIC)
                        .map(board -> new BoardChoice(board, false))
                        .orElseThrow(() -> ArgumentRejected.unknownKey(raw.trim()));
            }

            @Override
            public List<String> suggest(String partial) {
                List<String> all = new ArrayList<>(publicBoardKeys());
                all.add(SEASON_SCORE);
                return filtered(all, partial);
            }

            @Override
            public String expected() {
                return String.join("|", suggest(""));
            }
        };
    }

    /** Das Wort, das die Saisonwertung meint statt einer Rangliste. */
    public static final String SEASON_SCORE = "score";

    private static List<String> publicBoardKeys() {
        List<String> keys = new ArrayList<>();
        for (Aggregation board : Aggregation.all().values()) {
            if (board.visibility() == MetricVisibility.PUBLIC) {
                keys.add(board.key());
            }
        }
        return keys;
    }

    /**
     * Was {@link #leaderboard} liefert — eine Rangliste oder die Saisonwertung.
     *
     * @param board die Tafel, oder {@code null} bei der Saisonwertung
     * @param seasonScore ob die Saisonwertung gemeint war
     */
    public record BoardChoice(Aggregation board, boolean seasonScore) {}

    /** Eine Vorlage aus {@code items.yml} (T018). */
    public static ArgumentType<String> itemTemplate(Items items) {
        Objects.requireNonNull(items, "items");
        return fromKeys("template", items::templateKeys);
    }

    /** Eine Art aus {@code mobs.yml} (T018). */
    public static ArgumentType<String> mobKind(MobKinds kinds) {
        Objects.requireNonNull(kinds, "kinds");
        return fromKeys(
                "kind",
                () -> kinds.all().stream().map(MobKind::key).toList());
    }

    /** Eine Klasse (T018). */
    public static ArgumentType<CharacterClass> characterClass() {
        return fromEnum("class", CharacterClass.class);
    }

    /**
     * Ein Schlüssel aus einer Konfiguration — die Menge wird bei <b>jedem</b> Aufruf gefragt.
     *
     * <p>Nicht einmal beim Bauen eingesammelt, und das ist der Punkt: nach einem {@code /rpg reload}
     * gibt es andere Vorlagen. Eine beim Start gemerkte Liste schlüge Vorlagen vor, die es nicht
     * mehr gibt, und lehnte welche ab, die es gibt — der Nachladepfad ist genau das, was dieser
     * Block erstmals benutzbar macht.
     */
    private static ArgumentType<String> fromKeys(
            String name, Supplier<Collection<String>> keys) {
        return new ArgumentType<>() {

            @Override
            public String parse(String raw) throws ArgumentRejected {
                String wanted = raw.trim();
                for (String key : keys.get()) {
                    if (key.equalsIgnoreCase(wanted)) {
                        return key;
                    }
                }
                throw ArgumentRejected.unknownKey(wanted);
            }

            @Override
            public List<String> suggest(String partial) {
                return filtered(new ArrayList<>(keys.get()), partial);
            }

            @Override
            public String expected() {
                return name;
            }
        };
    }

    /**
     * Ein Wert aus einer Aufzählung, kleingeschrieben und mit Bindestrich.
     *
     * <p>{@code ALL_TIME} wird zu {@code all-time} — dieselbe Schreibweise, die {@link Period} in
     * den Nachrichtenschlüsseln trägt, und die einzige, die ein Spieler tippen kann. Der Unterstrich
     * hat in B11 die Attributnamen und in B12 die Ranglistennamen erwischt; hier steht die Umwandlung
     * einmal.
     */
    static <E extends Enum<E>> ArgumentType<E> fromEnum(String name, Class<E> type) {
        E[] values = type.getEnumConstants();
        return new ArgumentType<>() {

            @Override
            public E parse(String raw) throws ArgumentRejected {
                String wanted = raw.trim().toLowerCase(Locale.ROOT);
                for (E value : values) {
                    if (label(value).equals(wanted)) {
                        return value;
                    }
                }
                throw ArgumentRejected.invalid(name, raw, expected());
            }

            @Override
            public List<String> suggest(String partial) {
                List<String> all = new ArrayList<>(values.length);
                for (E value : values) {
                    all.add(label(value));
                }
                return filtered(all, partial);
            }

            @Override
            public String expected() {
                StringBuilder joined = new StringBuilder();
                for (E value : values) {
                    if (joined.length() > 0) {
                        joined.append('|');
                    }
                    joined.append(label(value));
                }
                return joined.toString();
            }
        };
    }

    /**
     * {@code ALL_TIME} → {@code all-time}.
     *
     * <p>Öffentlich, weil ein Kommando denselben Namen zurückgeben muss, den es entgegennimmt:
     * {@code /coins set … warrior …} bestätigt mit „warrior" und nicht mit „WARRIOR". Die
     * Umwandlung an zwei Stellen zu schreiben hieße, sie an einer davon irgendwann anders zu
     * schreiben.
     */
    public static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Am schon Getippten filtern und begrenzen (FR-033).
     *
     * <p>Beides zusammen und nicht nur eines: ungefiltert schickt man bei jedem Tastendruck alles,
     * unbegrenzt bei einem leeren Eingabefeld tausend Namen.
     */
    static List<String> filtered(List<String> all, String partial) {
        String wanted = partial == null ? "" : partial.trim().toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String candidate : all) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(wanted)) {
                hits.add(candidate);
                if (hits.size() == ArgumentType.SUGGESTION_LIMIT) {
                    break;
                }
            }
        }
        return List.copyOf(hits);
    }

    private static String format(Duration duration) {
        if (duration.toDays() > 0 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0) {
            return duration.toDays() + "d";
        }
        if (duration.toHours() > 0 && duration.toMinutesPart() == 0) {
            return duration.toHours() + "h";
        }
        return duration.toMinutes() + "m";
    }
}

package rpg.core.statistics;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Der geprüfte Inhalt von {@code statistics.yml} (contracts/stats-config.md).
 *
 * <p><b>Alle Zahlen dieses Blocks stehen hier, keine im Code</b> (Prinzip V). Was ein Betreiber
 * ändern können muss — die Schwelle, ab der ein Kill zählt, die Untätigkeitsdauer, das
 * Auffrischungsintervall, die Saisons, die Gewichtung und die Belohnungen — ist Konfiguration.
 *
 * <p><b>Nachladen tauscht das Ganze aus</b>, wie {@code MobConfig} und {@code ItemConfig} es tun.
 * Zwei Dinge ändern sich dabei ausdrücklich <em>nicht</em>: ein eingefrorener Saisonendstand und
 * ein bereits angelegter Anspruch. Beide sind vergeben, und eine Konfigurationsänderung darf
 * Vergebenes nicht rückwirkend umsortieren (FR-050, ADR-045).
 *
 * @param capture Schwelle und Untätigkeitsdauer der Erfassung
 * @param leaderboards Auffrischung und Länge der Ranglisten
 * @param seasons der geprüfte Saisonkalender
 * @param score die Gewichtung der Gesamtwertung (ADR-046)
 * @param rewards je Platz der Gesamtwertung eine Belohnung
 * @param hologram die Anzeige im Hub, sofern konfiguriert
 */
public record StatisticsConfig(
        Capture capture,
        Leaderboards leaderboards,
        SeasonCalendar seasons,
        Score score,
        Map<Integer, Reward> rewards,
        Optional<Hologram> hologram) {

    public StatisticsConfig {
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(leaderboards, "leaderboards");
        Objects.requireNonNull(seasons, "seasons");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(hologram, "hologram");
        rewards =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(Objects.requireNonNull(rewards, "rewards")));
    }

    /**
     * Die Erfassung.
     *
     * @param killCreditShare Anteil am Schaden, ab dem ein Kill zählt — echt zwischen 0 und 1
     *     (FR-007d). Eine Null gäbe jedem Streiftreffer einen Bosskill.
     * @param idleAfter nach dieser Dauer ohne Aktivität steht die aktive Uhr (FR-014b)
     */
    public record Capture(double killCreditShare, Duration idleAfter) {}

    /**
     * Die Ranglisten.
     *
     * @param refreshInterval wie oft die Sichten aufgefrischt werden (FR-032)
     * @param places wie viele Plätze eine Liste zeigt (FR-033)
     */
    public record Leaderboards(Duration refreshInterval, int places) {}

    /**
     * Die Gewichtung der Saison-Gesamtwertung (ADR-046).
     *
     * <p>Punkte je Einheit, je bepunktbarer {@link Aggregation}. Was hier <em>nicht</em> steht,
     * trägt nichts bei — das Fehlen einer Metrik ist eine Null, kein Fehler.
     *
     * @param weights mindestens ein Eintrag (FR-050d)
     */
    public record Score(Map<Aggregation, Double> weights) {

        public Score {
            weights =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(Objects.requireNonNull(weights, "weights")));
        }

        /** Das Gewicht einer Aggregation; nicht genannt heißt null. */
        public double weightOf(Aggregation aggregation) {
            return weights.getOrDefault(aggregation, 0.0);
        }
    }

    /**
     * Eine Belohnung für einen Platz der Gesamtwertung (FR-051).
     *
     * @param coins Betrag, kann null sein
     * @param items Vorlagen mit Stückzahl, kann leer sein
     */
    public record Reward(long coins, List<ItemGrant> items) {

        public Reward {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * Eine Vorlage samt Stückzahl.
     *
     * @param template Vorlagen-ID aus {@code items.yml} — beim Start geprüft, nicht zur Laufzeit
     * @param amount Stückzahl, größer als null
     */
    public record ItemGrant(String template, int amount) {}

    /**
     * Die Hologramm-Anzeige im Hub (FR-064).
     *
     * <p>Der ganze Abschnitt ist freiwillig. Fehlt er, entfällt die Anzeige; ist die Stelle nicht
     * ladbar, entfällt sie mit einer Warnung — <b>der Start läuft trotzdem</b>. Eine Zierde soll
     * keinen Server aufhalten.
     */
    public record Hologram(
            String world,
            double x,
            double y,
            double z,
            Aggregation board,
            Period period,
            int places) {}
}

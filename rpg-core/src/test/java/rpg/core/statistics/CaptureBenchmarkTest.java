package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamageShare;
import rpg.core.persistence.StatisticsRepository;

/**
 * T138, SC-001 — <b>die Erfassung kostet im Tick keine messbare Zeit.</b>
 *
 * <p>Nach dem Vorbild von {@code HordeBudgetBenchmarkTest} aus B10: <b>eine Messung, kein
 * Lasttest</b> (Prinzip VII in der Fassung von ADR-031). Gemessen wird die eigene Rechenarbeit
 * dieses Blocks auf dem heißesten Pfad — dem Ereignispfad, der bei jedem Kill, jedem Tod und jedem
 * Treffer läuft:
 *
 * <ol>
 *   <li>die Empfänger eines Kills bestimmen (Schwelle, Gruppe, FR-008),
 *   <li>je Empfänger den Zähler erhöhen — mit Dimension,
 *   <li>einen Tod mit Ursache zählen,
 *   <li>einen Höchstschaden melden.
 * </ol>
 *
 * <p><b>Was hier absichtlich NICHT gemessen wird:</b> der Weg zur Datenbank. Er findet auf diesem
 * Pfad gar nicht statt — {@link Statistics} legt in den Schreibpuffer aus B02, und der schreibt im
 * Flush. Genau das <em>ist</em> die Zusage: der Tick zahlt das Ablegen, nicht das Schreiben. Ein
 * Repository-Double, das nur mitzählt, bildet diesen Pfad deshalb richtig ab und nicht zu
 * günstig.
 *
 * <p>Was nur unter Volllast sichtbar wird — hundert gleichzeitige Kämpfe, ein voller
 * Schreibpuffer, echte Netzwerklatenz — gehört seit ADR-031 zu B15 und wird hier nicht gemessen.
 */
class CaptureBenchmarkTest {

    /** Fünf Beteiligte an einem Kill: eine volle Gruppe plus den Schlagenden. */
    private static final int RECIPIENTS = 5;

    /** 50 µs für einen vollständigen Ereignisdurchlauf. */
    private static final long BUDGET_NANOS = 50_000L;

    private static final int WARMUP_ROUNDS = 2_000;
    private static final int MEASURED_ROUNDS = 200;

    private static final double THRESHOLD = 0.05;

    @Test
    @DisplayName("SC-001 - ein vollstaendiger Erfassungsdurchlauf unter 50 Mikrosekunden")
    void oneFullCaptureRoundUnderFiftyMicroseconds() {
        Counting repository = new Counting();
        Statistics statistics = new RecordedStatistics(repository, Logger.getLogger("benchmark"));

        List<UUID> players = players(RECIPIENTS);
        DamageShare shares = sharesOf(players);
        List<UUID> party = players.subList(1, players.size());

        // Warm up: die ersten Durchlaeufe messen den JIT, nicht die Erfassung.
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            oneRound(statistics, shares, party, players.get(0));
        }

        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            oneRound(statistics, shares, party, players.get(0));
            best = Math.min(best, System.nanoTime() - start);
        }

        assertThat(repository.writes.get())
                .as("der Compiler darf die Durchlaeufe nicht wegoptimieren")
                .isPositive();

        System.out.printf(
                "[statistics] SC-001: ein Erfassungsdurchlauf mit %d Empfaengern in %d ns"
                        + " (Budget %d ns, Marge %.1fx)%n",
                RECIPIENTS, best, BUDGET_NANOS, (double) BUDGET_NANOS / best);

        assertThat(best)
                .as(
                        "SC-001: ein Erfassungsdurchlauf mit %d Empfaengern muss in %d ns passen,"
                                + " aber der schnellste von %d Durchlaeufen brauchte %d ns",
                        RECIPIENTS, BUDGET_NANOS, MEASURED_ROUNDS, best)
                .isLessThan(BUDGET_NANOS);
    }

    @Test
    @DisplayName("SC-002 - tausend Kills sind tausend Ablagen und KEINE Abfrage")
    void athousandKillsAreAThousandHandoversAndNoQuery() {
        Counting repository = new Counting();
        Statistics statistics = new RecordedStatistics(repository, Logger.getLogger("benchmark"));
        UUID player = UUID.randomUUID();

        for (int i = 0; i < 1_000; i++) {
            statistics.count(player, MetricRegistry.MOB_KILLS, "rotling", 1);
        }

        // Der Punkt ist die Null: auf dem Erfassungspfad wird NICHT gelesen. Ein einziges
        // vorheriges SELECT ("wie viele sind es schon?") waere pro Kill eine Abfrage - und genau
        // das ist der Entwurf, den B12 nicht hat (FR-004, SC-002).
        assertThat(repository.reads.get()).as("keine einzige Abfrage im Tick").isZero();
        assertThat(repository.writes.get()).isEqualTo(1_000);
    }

    // ------------------------------------------------------------------ Gerüst

    /** Ein Kill, ein Tod, ein Treffer — der Pfad, der im Tick wirklich läuft. */
    private static void oneRound(
            Statistics statistics, DamageShare shares, List<UUID> party, UUID victimOwner) {
        Set<UUID> recipients = KillCredit.recipients(shares, THRESHOLD, party);
        for (UUID recipient : recipients) {
            statistics.count(recipient, MetricRegistry.MOB_KILLS, "rotling", 1);
        }
        statistics.count(victimOwner, MetricRegistry.DEATHS, MetricRegistry.DEATH_VOID, 1);
        statistics.reportMax(victimOwner, MetricRegistry.DAMAGE_MAX, 1_249);
    }

    private static List<UUID> players(int count) {
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(UUID.randomUUID());
        }
        return players;
    }

    private static DamageShare sharesOf(List<UUID> players) {
        Map<UUID, Double> shares = new LinkedHashMap<>();
        for (UUID player : players) {
            shares.put(player, 1.0 / players.size());
        }
        return new DamageShare(shares, players.get(0), 1_000.0);
    }

    /** Ein Bestand, der nur mitzählt — der Schreibpuffer aus B02 tut auf diesem Pfad nicht mehr. */
    private static final class Counting implements StatisticsRepository {

        private final AtomicLong writes = new AtomicLong();
        private final AtomicLong reads = new AtomicLong();

        @Override
        public void increment(UUID playerId, String metric, long delta) {
            writes.incrementAndGet();
        }

        @Override
        public void reportMax(UUID playerId, String metric, long value) {
            writes.incrementAndGet();
        }

        @Override
        public CompletableFuture<Long> sum(
                UUID playerId, String metric, LocalDate from, LocalDate to) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(0L);
        }

        @Override
        public CompletableFuture<Long> total(UUID playerId, String metric) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(0L);
        }
    }
}

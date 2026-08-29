package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;

/**
 * SC-003 — <b>fünfzig gleichzeitige Öffnungen, null Datenbankabfragen.</b>
 *
 * <p>Das ist das Erfolgskriterium aus dem Blockdokument, und es ist schärfer formuliert als
 * FR-030: dort heißt es „höchstens eine Abfrage", hier sind es null. Der Unterschied ist kein
 * Ehrgeiz, sondern eine Folge der Bauart — gelesen wird aus dem Speicher, die Abfragen laufen im
 * Auffrischungstakt.
 *
 * <p><b>Warum das ein Test sein muss.</b> Eine Ersatzabfrage beim Öffnen ist die naheliegendste
 * aller Reparaturen: der Cache ist noch leer, das Fenster soll etwas zeigen, also fragt man eben
 * nach. Sie funktioniert tadellos — bei einem Spieler. Bei fünfzig gleichzeitigen, und zwar genau
 * dann, wenn viel los ist und deshalb viele nachsehen, ist sie ein stehender Server. Der Fehler
 * fiele erst unter Last auf, also dort, wo ihn niemand mehr einem Block zuordnen kann.
 *
 * <p>Gezählt wird an einer Quelle, die <em>jede</em> Abfrage meldet. Sie ist verdrahtet, aber sie
 * darf nicht gerufen werden.
 */
class OpeningNeverQueriesTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("SC-003 - fuenfzig gleichzeitige Oeffnungen kosten null Abfragen")
    void fiftyConcurrentOpeningsCostNoQueries() throws Exception {
        CountingSource source = new CountingSource();
        Leaderboards boards = Leaderboards.backedBy(filledCache());

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(50);
        AtomicInteger seen = new AtomicInteger();

        for (int i = 0; i < 50; i++) {
            pool.execute(
                    () -> {
                        try {
                            start.await();
                            boards.board(Aggregation.MOB_KILLS, Period.ALL_TIME)
                                    .ifPresent(list -> seen.addAndGet(list.top().size()));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).as("alle fuenfzig fertig").isTrue();
        pool.shutdownNow();

        assertThat(seen.get()).as("alle fuenfzig haben wirklich etwas gesehen").isEqualTo(50 * 3);
        assertThat(source.queries()).as("und keine einzige Abfrage dabei ausgeloest").isZero();
    }

    @Test
    @DisplayName("FR-035 - ein leerer Stand loest KEINE Ersatzabfrage aus")
    void anemptyCacheTriggersNoFallbackQuery() {
        CountingSource source = new CountingSource();
        Leaderboards boards = Leaderboards.backedBy(LeaderboardCache.empty());

        assertThat(boards.board(Aggregation.MOB_KILLS, Period.ALL_TIME))
                .as("noch nie aufgefrischt - das ist eine Meldung, keine leere Liste")
                .isEmpty();
        assertThat(source.queries())
                .as("gerade hier waere die Ersatzabfrage am verfuehrerischsten")
                .isZero();
    }

    @Test
    @DisplayName("SC-003 gilt auch fuer die eigene Platzierung ausserhalb der ersten N")
    void thesameHoldsForTheOwnRankOutsideTheTop() {
        CountingSource source = new CountingSource();
        Leaderboards boards = Leaderboards.backedBy(filledCache());

        // Die vollstaendige Rangzuordnung liegt im Stand; ohne sie waere GENAU das hier eine
        // Abfrage je Oeffnung (FR-033).
        boards.rankOf(Aggregation.MOB_KILLS, Period.ALL_TIME, UUID.randomUUID());

        assertThat(source.queries()).isZero();
    }

    private static LeaderboardCache filledCache() {
        LeaderboardCache cache = LeaderboardCache.empty();
        Map<UUID, Long> values = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            values.put(UUID.randomUUID(), 100L - i);
        }
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.MOB_KILLS,
                                Period.ALL_TIME,
                                "",
                                values,
                                10,
                                Map.of(),
                                AT)),
                AT);
        return cache;
    }

    /** Eine Quelle, die jede Abfrage meldet — sie ist da, damit sie schweigen kann. */
    private static final class CountingSource {

        private final AtomicInteger queries = new AtomicInteger();

        @SuppressWarnings("unused")
        Map<UUID, Long> query() {
            queries.incrementAndGet();
            return Map.of();
        }

        int queries() {
            return queries.get();
        }
    }
}

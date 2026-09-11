package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.statistics.ActivityClock;
import rpg.core.statistics.Metric;
import rpg.core.statistics.Statistics;

/**
 * FR-014c2, SC-001 — <b>das Erneuern des Zeitstempels kostet nichts.</b>
 *
 * <p>Diese Methode hängt am fünften Handler auf {@code PlayerMoveEvent}, dem geschäftigsten
 * Ereignis eines Servers. Bei fünfzig Spielern läuft sie tausende Male je Sekunde. Was hier an
 * Arbeit steckt, steckt im Tick.
 *
 * <h2>Gemessen wird nicht die Zeit</h2>
 *
 * <p>Eine Laufzeitmessung wäre hier die schlechtere Prüfung: sie schwankt mit der Maschine, mit
 * dem JIT und mit der Tageszeit, und irgendwann setzt jemand die Schranke hoch, statt der Ursache
 * nachzugehen. Geprüft wird stattdessen, <b>was passiert</b> — nämlich nichts außer einer
 * Zuweisung: kein Schreibvorgang, keine Neuberechnung, kein zweiter Eintrag.
 *
 * <p>Dieselbe Überlegung wie in B02s {@code BatchingTest}, das „kein Datenbankzugriff je
 * Spielereignis" am Scheduler abliest statt an der Uhr.
 */
class ActivityTouchIsFreeTest {

    @Test
    @DisplayName("FR-014c2 - zehntausend Beruehrungen loesen keinen einzigen Schreibvorgang aus")
    void tenThousandTouchesTriggerNoWrite() {
        Recorder recorder = new Recorder();
        ActivityClock clock = new ActivityClock();
        UUID player = UUID.randomUUID();

        Instant now = Instant.parse("2026-08-29T10:00:00Z");
        for (int i = 0; i < 10_000; i++) {
            clock.touch(player, now.plusMillis(i));
        }

        assertThat(recorder.writes)
                .as("der Zeitstempel ist eine Zuweisung - erst der Sweep macht daraus Statistik")
                .isEmpty();
    }

    @Test
    @DisplayName("zehntausend Beruehrungen erzeugen genau EINEN Eintrag")
    void tenThousandTouchesProduceExactlyOneEntry() {
        ActivityClock clock = new ActivityClock();
        UUID player = UUID.randomUUID();

        Instant now = Instant.parse("2026-08-29T10:00:00Z");
        for (int i = 0; i < 10_000; i++) {
            clock.touch(player, now.plusMillis(i));
        }

        // Waere hier eine Liste von Zeitpunkten oder ein Ereignisprotokoll entstanden, wuechse der
        // Speicher mit der Bewegung jedes Spielers - und niemand haette es bemerkt, weil alles
        // funktioniert.
        assertThat(clock.tracked()).isEqualTo(1);
        assertThat(clock.lastActivityOf(player)).isEqualTo(now.plusMillis(9_999));
    }

    @Test
    @DisplayName("der Bukkit-Zuhoerer tut dasselbe und sonst nichts")
    void thebukkitListenerDoesTheSameAndNothingElse() {
        Recorder recorder = new Recorder();
        ActivityClock activity = new ActivityClock();
        UUID player = UUID.randomUUID();

        ActivityListener listener = new ActivityListener(activity, fixedClock());
        for (int i = 0; i < 1_000; i++) {
            listener.touch(player);
        }

        assertThat(recorder.writes).isEmpty();
        assertThat(activity.tracked()).isEqualTo(1);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-29T10:00:00Z"), ZoneOffset.UTC);
    }

    /** Schlägt an, sobald irgendetwas geschrieben würde. */
    private static final class Recorder implements Statistics {

        private final List<String> writes = new ArrayList<>();

        @Override
        public void count(UUID playerId, Metric metric, long delta) {
            writes.add(metric.key());
        }

        @Override
        public void count(UUID playerId, Metric family, String dimension, long delta) {
            writes.add(family.key() + "." + dimension);
        }

        @Override
        public void reportMax(UUID playerId, Metric metric, long value) {
            writes.add("max:" + metric.key());
        }
    }
}

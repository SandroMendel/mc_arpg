package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.statistics.AccountLookup;
import rpg.core.statistics.ActivityClock;
import rpg.core.statistics.Metric;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Playtime;
import rpg.core.statistics.Statistics;
import rpg.core.zone.ZoneChangedEvent;

/**
 * Edge Case — <b>der Abschnitt wechselt, aber keine der beiden Zonen bekommt aktive Zeit.</b>
 *
 * <p>Ein untätiger Spieler kann trotzdem die Zone wechseln: er wird geschoben, fällt, wird
 * teleportiert. Der laufende Abschnitt muss dann geschlossen und ein neuer geöffnet werden —
 * sonst stünde die ganze Zeit später unter der falschen Zone. Aber <b>aktive</b> Zeit darf dabei
 * nirgends anfallen, weil er nichts getan hat.
 *
 * <p>Der naheliegende Fehler ist, den Zonenwechsel als Aktivität zu lesen. Er sieht aus wie eine:
 * es bewegt sich etwas. Nur bewegt sich nicht der Spieler, sondern die Welt um ihn herum — und die
 * Spielzeit-Rangliste gewönne, wer sich in eine Strömung legt.
 */
class ZoneChangeWhileIdleTest {

    private static final Duration IDLE_AFTER = Duration.ofMinutes(5);

    @Test
    @DisplayName("ein Zonenwechsel im untaetigen Zustand bucht keine aktive Zeit")
    void azoneChangeWhileIdleBooksNoActiveTime() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        Recorder recorder = new Recorder();
        MovingClock clock = new MovingClock(Instant.parse("2026-08-29T10:00:00Z"));

        ActivityClock activity = new ActivityClock();
        Playtime playtime = new Playtime();
        PlaytimeAccrual accrual =
                new PlaytimeAccrual(recorder, playtime, activity, () -> IDLE_AFTER, clock);

        accrual.begin(account, "greenfields");

        // Zehn Minuten nichts getan - der Sweep stellt die Untaetigkeit fest.
        clock.advance(Duration.ofMinutes(10));
        accrual.accrue(account);

        // Jetzt wechselt die Zone, ohne dass der Spieler etwas getan haette.
        clock.advance(Duration.ofMinutes(5));
        new ZoneTimeListener(accrual, new AccountLookup(id -> Optional.of(account)))
                .onZoneChanged(new ZoneChangedEvent(character, Optional.of("greenfields"), Optional.of("dustlands")));

        assertThat(recorder.activeByZone("dustlands"))
                .as("die neue Zone bekommt nichts - der Wechsel selbst ist keine Taetigkeit")
                .isZero();
        assertThat(recorder.activeByZone("greenfields"))
                .as(
                        "genau fuenf aktive Minuten: von 10:00 bis zur Schwelle um 10:05. Der"
                                + " Sweep um 10:10 bemerkt die Untaetigkeit nur - er datiert sie"
                                + " nicht auf sich selbst")
                .isEqualTo(5 * 60);
        assertThat(recorder.onlineSeconds())
                .as("die Onlineuhr laeuft die ganze Zeit weiter, sie ist die zweite Uhr")
                .isEqualTo(15 * 60);
    }

    @Test
    @DisplayName("wer aktiv war, bekommt seine Zeit in der VERLASSENEN Zone gutgeschrieben")
    void anactivePlayerGetsTheTimeInTheZoneHeLeft() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        Recorder recorder = new Recorder();
        MovingClock clock = new MovingClock(Instant.parse("2026-08-29T10:00:00Z"));

        ActivityClock activity = new ActivityClock();
        PlaytimeAccrual accrual =
                new PlaytimeAccrual(recorder, new Playtime(), activity, () -> IDLE_AFTER, clock);

        accrual.begin(account, "greenfields");
        clock.advance(Duration.ofMinutes(3));

        new ZoneTimeListener(accrual, new AccountLookup(id -> Optional.of(account)))
                .onZoneChanged(new ZoneChangedEvent(character, Optional.of("greenfields"), Optional.of("dustlands")));

        // Die drei Minuten gehoeren der Zone, in der sie verbracht wurden - nicht der neuen.
        assertThat(recorder.activeByZone("greenfields")).isEqualTo(3 * 60);
        assertThat(recorder.activeByZone("dustlands")).isZero();
    }

    @Test
    @DisplayName("ohne auffindbares Konto wird gar nichts gebucht")
    void withoutAResolvableAccountNothingIsBooked() {
        Recorder recorder = new Recorder();
        MovingClock clock = new MovingClock(Instant.parse("2026-08-29T10:00:00Z"));
        PlaytimeAccrual accrual =
                new PlaytimeAccrual(
                        recorder, new Playtime(), new ActivityClock(), () -> IDLE_AFTER, clock);

        new ZoneTimeListener(accrual, new AccountLookup(id -> Optional.empty()))
                .onZoneChanged(new ZoneChangedEvent(UUID.randomUUID(), Optional.of("greenfields"), Optional.of("dustlands")));

        // Ein Rueckfall auf die Charakterkennung schriebe Zeit auf ein Konto, das es nicht gibt.
        assertThat(recorder.entries).isEmpty();
    }

    // ------------------------------------------------------------------ Gerüst

    private static final class MovingClock extends Clock {

        private Instant now;

        MovingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class Recorder implements Statistics {

        private record Entry(Metric metric, String dimension, long value) {}

        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void count(UUID playerId, Metric metric, long delta) {
            entries.add(new Entry(metric, null, delta));
        }

        @Override
        public void count(UUID playerId, Metric family, String dimension, long delta) {
            entries.add(new Entry(family, dimension, delta));
        }

        @Override
        public void reportMax(UUID playerId, Metric metric, long value) {
            entries.add(new Entry(metric, null, value));
        }

        long activeByZone(String zoneKey) {
            return entries.stream()
                    .filter(e -> e.metric().equals(MetricRegistry.PLAYTIME_ACTIVE))
                    .filter(e -> zoneKey.equals(e.dimension()))
                    .mapToLong(Entry::value)
                    .sum();
        }

        long onlineSeconds() {
            return entries.stream()
                    .filter(e -> e.metric().equals(MetricRegistry.PLAYTIME_ONLINE))
                    .mapToLong(Entry::value)
                    .sum();
        }
    }
}

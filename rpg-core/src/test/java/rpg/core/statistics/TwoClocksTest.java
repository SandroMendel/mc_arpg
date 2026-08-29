package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-014a, FR-014b, SC-015 — <b>die aktive Uhr steht, die Onlineuhr läuft weiter.</b>
 *
 * <p>Das ist der ganze Sinn von ADR-043. Gäbe es nur eine Zahl, gewönne die Spielzeit-Rangliste,
 * wer sein Konto nachts angemeldet lässt — und die Zahl hieße dann auch nicht mehr „gespielt",
 * sondern „verbunden gewesen", ohne dass jemand die Umbenennung bemerkt.
 *
 * <p><b>Die aktive Zeit ist nie größer als die Onlinezeit.</b> Hier gilt das nicht durch Nachprüfen,
 * sondern durch Bauart: beide Uhren lesen dieselben Abschnitte, und die aktive zählt nur die
 * Teilmenge mit gesetztem Kennzeichen. Eine getrennte Buchführung hätte beide auseinanderlaufen
 * lassen, sobald ein Übergang einmal nicht sauber geschlossen wird.
 */
class TwoClocksTest {

    private static final String ZONE = "greenfields";
    private static final Duration IDLE_AFTER = Duration.ofMinutes(5);

    @Test
    @DisplayName("FR-014b - nach der Untaetigkeitsschwelle steht die aktive Uhr")
    void afterTheIdleThresholdTheActiveClockStops() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();
        List<Playtime.Slice> booked = new ArrayList<>();

        Instant start = utc(10, 0);
        playtime.begin(player, start, ZONE);

        // Zehn Minuten gespielt, dann untaetig geworden.
        booked.addAll(playtime.activityChanged(player, utc(10, 10), false));
        // Eine Stunde untaetig.
        booked.addAll(playtime.end(player, utc(11, 10)));

        assertThat(activeSeconds(booked)).isEqualTo(10 * 60);
        assertThat(onlineSeconds(booked)).isEqualTo(70 * 60);
    }

    @Test
    @DisplayName("SC-015 - die aktive Zeit ist nie groesser als die Onlinezeit")
    void activeTimeIsNeverGreaterThanOnlineTime() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();
        List<Playtime.Slice> booked = new ArrayList<>();

        playtime.begin(player, utc(10, 0), ZONE);
        booked.addAll(playtime.activityChanged(player, utc(10, 20), false));
        booked.addAll(playtime.activityChanged(player, utc(10, 50), true));
        booked.addAll(playtime.activityChanged(player, utc(11, 5), false));
        booked.addAll(playtime.end(player, utc(11, 30)));

        assertThat(activeSeconds(booked)).isLessThanOrEqualTo(onlineSeconds(booked));
        assertThat(activeSeconds(booked)).isEqualTo((20 + 15) * 60);
        assertThat(onlineSeconds(booked)).isEqualTo(90 * 60);
    }

    @Test
    @DisplayName("wer durchgehend spielt, hat beide Uhren gleich")
    void someoneWhoNeverIdlesHasBothClocksEqual() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();

        playtime.begin(player, utc(10, 0), ZONE);
        List<Playtime.Slice> booked = playtime.end(player, utc(12, 0));

        assertThat(activeSeconds(booked)).isEqualTo(onlineSeconds(booked)).isEqualTo(2 * 3600);
    }

    @Test
    @DisplayName("ein Zustandswechsel, der keiner ist, schliesst nichts")
    void anUnchangedStateClosesNothing() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();

        playtime.begin(player, utc(10, 0), ZONE);

        // Sonst zerfiele der Tag in tausend Ein-Sekunden-Abschnitte, ohne dass sich an einer
        // einzigen Summe etwas aenderte.
        assertThat(playtime.activityChanged(player, utc(10, 5), true)).isEmpty();
        assertThat(playtime.end(player, utc(10, 10))).singleElement().isNotNull();
    }

    @Test
    @DisplayName("die Untaetigkeit selbst folgt aus einem Zeitstempel, nicht aus einer Aufgabe")
    void idlenessFollowsFromATimestamp() {
        ActivityClock clock = new ActivityClock();
        UUID player = StatisticsFixtures.holderId();

        clock.touch(player, utc(10, 0));

        assertThat(clock.isIdle(player, utc(10, 4), IDLE_AFTER)).isFalse();
        assertThat(clock.isIdle(player, utc(10, 5), IDLE_AFTER))
                .as("genau auf der Schwelle gilt als untaetig")
                .isTrue();
        assertThat(clock.isIdle(player, utc(10, 30), IDLE_AFTER)).isTrue();

        clock.touch(player, utc(10, 30));
        assertThat(clock.isIdle(player, utc(10, 31), IDLE_AFTER)).isFalse();
    }

    @Test
    @DisplayName("ein gerade angemeldeter Spieler gilt nicht als untaetig")
    void ajustLoggedInPlayerIsNotIdle() {
        ActivityClock clock = new ActivityClock();

        assertThat(clock.isIdle(StatisticsFixtures.holderId(), utc(10, 0), IDLE_AFTER))
                .as("die erste Antwort auf eine nie gestellte Frage soll nicht 'schlaeft' lauten")
                .isFalse();
    }

    @Test
    @DisplayName("beim Sitzungsende wird der Zeitstempel vergessen")
    void thetimestampIsForgottenWhenTheSessionEnds() {
        ActivityClock clock = new ActivityClock();
        UUID player = StatisticsFixtures.holderId();

        clock.touch(player, utc(10, 0));
        assertThat(clock.tracked()).isEqualTo(1);

        clock.forget(player);

        // Sonst wuechse die Map ueber die Laufzeit des Servers, und wer vor Stunden gegangen ist,
        // haette immer noch einen Eintrag.
        assertThat(clock.tracked()).isZero();
    }

    private static long activeSeconds(List<Playtime.Slice> slices) {
        return slices.stream().filter(Playtime.Slice::active).mapToLong(Playtime.Slice::seconds).sum();
    }

    private static long onlineSeconds(List<Playtime.Slice> slices) {
        return slices.stream().mapToLong(Playtime.Slice::seconds).sum();
    }

    private static Instant utc(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 29, hour, minute).toInstant(ZoneOffset.UTC);
    }
}

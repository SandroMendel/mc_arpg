package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-014d, FR-014e, SC-016 — <b>die Summe der Zonenzeiten ist auf die Sekunde die aktive
 * Gesamtzeit.</b>
 *
 * <p>Diese Zusage ist der Grund für die ganze Bauart. Ein Spieler, der sein Profil öffnet, sieht
 * beide Zahlen untereinander: „aktiv gespielt: 4 h 12 min" und darunter die Aufteilung nach
 * Regionen. Stimmen sie nicht überein, ist nicht eine von beiden falsch — es sind beide
 * unglaubwürdig, und zwar dauerhaft.
 *
 * <p>Die Summe stimmt hier <b>durch Konstruktion</b>: jeder aktive Abschnitt trägt genau eine
 * Zone, und die Gesamtzeit ist nichts anderes als dieselben Abschnitte, nur nicht nach Zone
 * getrennt. Es gibt keine zweite Rechnung, die abweichen könnte.
 *
 * <p><b>Der Fall, der ohne festen Ersatzschlüssel kaputtginge</b>, steht unten eigens: Zeit
 * außerhalb jeder Zone. Ohne {@link MetricRegistry#ZONE_WILDERNESS} fiele sie aus der
 * Aufschlüsselung heraus, bliebe aber in der Gesamtzeit — und die Differenz träfe jeden, der die
 * Zonen je verlässt, also jeden.
 */
class ZoneTimeSumsToActiveTimeTest {

    @Test
    @DisplayName("SC-016 - drei Zonen an einem Tag, und die Summe stimmt auf die Sekunde")
    void threeZonesInOneDayAndTheSumMatches() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();
        List<Playtime.Slice> booked = new ArrayList<>();

        playtime.begin(player, utc(10, 0), "greenfields");
        booked.addAll(playtime.zoneChanged(player, utc(10, 25), "dustlands"));
        booked.addAll(playtime.zoneChanged(player, utc(11, 5), "darkforest"));
        booked.addAll(playtime.end(player, utc(11, 40)));

        assertThat(perZone(booked))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "greenfields", 25L * 60,
                                "dustlands", 40L * 60,
                                "darkforest", 35L * 60));
        assertThat(sumOfZones(booked)).isEqualTo(activeSeconds(booked)).isEqualTo(100L * 60);
    }

    @Test
    @DisplayName("Edge Case - die Untaetigkeit tritt mitten in einer Zone ein")
    void idlenessInTheMiddleOfAZone() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();
        List<Playtime.Slice> booked = new ArrayList<>();

        playtime.begin(player, utc(10, 0), "greenfields");
        booked.addAll(playtime.activityChanged(player, utc(10, 20), false));
        booked.addAll(playtime.zoneChanged(player, utc(10, 40), "dustlands"));
        booked.addAll(playtime.activityChanged(player, utc(11, 0), true));
        booked.addAll(playtime.end(player, utc(11, 15)));

        // Aktiv: 20 min in greenfields, 15 min in dustlands. Die 40 untaetigen Minuten zaehlen auf
        // keine Zone - aber sehr wohl auf die Onlinezeit.
        assertThat(perZone(booked))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("greenfields", 20L * 60, "dustlands", 15L * 60));
        assertThat(sumOfZones(booked)).isEqualTo(activeSeconds(booked)).isEqualTo(35L * 60);
        assertThat(onlineSeconds(booked)).isEqualTo(75L * 60);
    }

    @Test
    @DisplayName("FR-014e - Zeit ausserhalb jeder Zone faellt nicht aus der Summe")
    void timeOutsideEveryZoneDoesNotFallOutOfTheSum() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();
        List<Playtime.Slice> booked = new ArrayList<>();

        playtime.begin(player, utc(10, 0), "greenfields");
        booked.addAll(playtime.zoneChanged(player, utc(10, 30), null));
        booked.addAll(playtime.zoneChanged(player, utc(11, 0), "dustlands"));
        booked.addAll(playtime.end(player, utc(11, 20)));

        assertThat(perZone(booked))
                .containsKey(MetricRegistry.ZONE_WILDERNESS)
                .containsEntry(MetricRegistry.ZONE_WILDERNESS, 30L * 60);
        assertThat(sumOfZones(booked))
                .as("ohne den festen Ersatzschluessel fehlten hier 30 Minuten")
                .isEqualTo(activeSeconds(booked))
                .isEqualTo(80L * 60);
    }

    @Test
    @DisplayName("ueber Mitternacht hinweg stimmt die Summe je TAG")
    void acrossMidnightTheSumMatchesPerDay() {
        UUID player = StatisticsFixtures.holderId();
        Playtime playtime = new Playtime();

        playtime.begin(player, utcOn(29, 23, 40), "greenfields");
        List<Playtime.Slice> booked = playtime.end(player, utcOn(30, 0, 30));

        // Zwei Zeilen in der Tagestabelle, zwei Tage, und je Tag stimmt die Summe fuer sich.
        assertThat(booked).hasSize(2);
        for (Playtime.Slice slice : booked) {
            List<Playtime.Slice> ofThatDay =
                    booked.stream().filter(other -> other.day().equals(slice.day())).toList();
            assertThat(sumOfZones(ofThatDay)).isEqualTo(activeSeconds(ofThatDay));
        }
    }

    private static Map<String, Long> perZone(List<Playtime.Slice> slices) {
        Map<String, Long> byZone = new LinkedHashMap<>();
        for (Playtime.Slice slice : slices) {
            if (slice.active()) {
                byZone.merge(slice.zoneKey(), slice.seconds(), Long::sum);
            }
        }
        return byZone;
    }

    private static long sumOfZones(List<Playtime.Slice> slices) {
        return perZone(slices).values().stream().mapToLong(Long::longValue).sum();
    }

    private static long activeSeconds(List<Playtime.Slice> slices) {
        return slices.stream().filter(Playtime.Slice::active).mapToLong(Playtime.Slice::seconds).sum();
    }

    private static long onlineSeconds(List<Playtime.Slice> slices) {
        return slices.stream().mapToLong(Playtime.Slice::seconds).sum();
    }

    private static Instant utc(int hour, int minute) {
        return utcOn(29, hour, minute);
    }

    private static Instant utcOn(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 8, day, hour, minute).toInstant(ZoneOffset.UTC);
    }
}

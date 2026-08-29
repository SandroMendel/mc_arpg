package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-015, SC-013 — <b>23:40 bis 00:30 sind zwei Tage, nicht einer.</b>
 *
 * <p>Ohne die Teilung landete der ganze Abschnitt auf dem Tag, an dem er zufällig geschlossen
 * wird. Welcher das ist, hinge davon ab, wann der Durchlauf gerade vorbeikommt — die Tagesstatistik
 * wäre also von der Taktung des Servers abhängig und nicht von dem, was der Spieler getan hat.
 * Und sie wäre trotzdem plausibel: fünfzig Minuten an einem Tag sehen aus wie fünfzig Minuten.
 *
 * <p>Alles in <b>UTC</b>, weil die gespeicherte Tagesangabe es ist (FR-026). Eine andere Zeitzone
 * hier hieße, dass ein Teil der Zeilen eines Tages zum Vortag gehört.
 */
class PlaytimeSplitsAtMidnightTest {

    private static final String ZONE = "greenfields";

    @Test
    @DisplayName("SC-013 - 23:40 bis 00:30 verteilt sich auf zwei Tage")
    void anEveningIntoTheNightSplitsAcrossTwoDays() {
        Instant from = utc(2026, 8, 29, 23, 40);
        Instant to = utc(2026, 8, 30, 0, 30);

        List<Playtime.Slice> slices = Playtime.slice(from, to, ZONE, true);

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).day()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(slices.get(0).seconds()).isEqualTo(20 * 60);
        assertThat(slices.get(1).day()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(slices.get(1).seconds()).isEqualTo(30 * 60);
    }

    @Test
    @DisplayName("die Summe der Teile ist die Dauer des Ganzen - keine Sekunde geht verloren")
    void thepartsSumToTheWhole() {
        Instant from = utc(2026, 8, 29, 23, 40);
        Instant to = utc(2026, 8, 30, 0, 30);

        assertThat(Playtime.slice(from, to, ZONE, true).stream()
                        .mapToLong(Playtime.Slice::seconds)
                        .sum())
                .isEqualTo(50 * 60);
    }

    @Test
    @DisplayName("ein Abschnitt innerhalb eines Tages bleibt ein Stueck")
    void asegmentInsideOneDayStaysWhole() {
        List<Playtime.Slice> slices =
                Playtime.slice(utc(2026, 8, 29, 10, 0), utc(2026, 8, 29, 11, 30), ZONE, true);

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).seconds()).isEqualTo(90 * 60);
    }

    @Test
    @DisplayName("ueber drei Tage hinweg entstehen drei Stuecke, das mittlere ein voller Tag")
    void acrossThreeDaysTheMiddleOneIsAFullDay() {
        List<Playtime.Slice> slices =
                Playtime.slice(utc(2026, 8, 29, 22, 0), utc(2026, 8, 31, 2, 0), ZONE, true);

        assertThat(slices).hasSize(3);
        assertThat(slices.get(0).seconds()).isEqualTo(2 * 3600);
        assertThat(slices.get(1).seconds()).isEqualTo(24 * 3600);
        assertThat(slices.get(2).seconds()).isEqualTo(2 * 3600);
    }

    @Test
    @DisplayName("ein Abschnitt, der genau um Mitternacht endet, erzeugt kein leeres zweites Stueck")
    void asegmentEndingExactlyAtMidnightMakesNoEmptySecondSlice() {
        List<Playtime.Slice> slices =
                Playtime.slice(utc(2026, 8, 29, 23, 0), utc(2026, 8, 30, 0, 0), ZONE, true);

        // Der naheliegende Fehler waere ein zweites Stueck mit null Sekunden am Folgetag - eine
        // Zeile in der Tagestabelle, die nichts bedeutet.
        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).day()).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    @DisplayName("ein Abschnitt ohne Dauer erzeugt gar nichts")
    void azeroLengthSegmentProducesNothing() {
        Instant moment = utc(2026, 8, 29, 12, 0);

        assertThat(Playtime.slice(moment, moment, ZONE, true)).isEmpty();
        assertThat(Playtime.slice(moment, moment.minusSeconds(60), ZONE, true)).isEmpty();
    }

    @Test
    @DisplayName("FR-014e - ohne Zone laeuft die Zeit unter dem festen Ersatzschluessel")
    void withoutAZoneTheTimeGoesToTheWilderness() {
        assertThat(Playtime.slice(utc(2026, 8, 29, 10, 0), utc(2026, 8, 29, 11, 0), null, true))
                .singleElement()
                .extracting(Playtime.Slice::zoneKey)
                .isEqualTo(MetricRegistry.ZONE_WILDERNESS);
    }

    private static Instant utc(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC);
    }
}

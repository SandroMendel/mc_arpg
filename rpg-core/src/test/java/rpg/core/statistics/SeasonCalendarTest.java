package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-048, FR-049 — <b>drei Arten, den Kalender falsch zu konfigurieren, und jede wird einzeln
 * zurückgewiesen.</b>
 *
 * <p>Die Meldung nennt in allen drei Fällen die <b>beteiligten Saisonschlüssel</b>. Das ist der
 * eigentliche Punkt dieser Tests: dass es abbricht, ist leicht; dass der Abbruch dem Leser sagt,
 * <em>wo</em> er nachsehen muss, ist die Arbeit. Eine Meldung wie „ungültige Saisons" zwingt ihn,
 * die Datei selbst zu durchsuchen — und zwar an dem Tag, an dem der Server nicht startet.
 */
class SeasonCalendarTest {

    private static SeasonCalendar.Season season(String key, String from, String to) {
        return new SeasonCalendar.Season(key, LocalDate.parse(from), LocalDate.parse(to));
    }

    @Test
    @DisplayName("FR-049 - eine Luecke wird zurueckgewiesen, und die Meldung nennt beide Saisons")
    void aGapIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                SeasonCalendar.of(
                                        List.of(
                                                season("2026-q3", "2026-07-01", "2026-09-30"),
                                                // ein Tag fehlt: der 1. Oktober
                                                season("2026-q4", "2026-10-02", "2026-12-31"))))
                .withMessageContaining("2026-q3")
                .withMessageContaining("2026-q4")
                .withMessageContaining("Luecke")
                .withMessageContaining("2026-10-01");
    }

    @Test
    @DisplayName("FR-049 - eine Ueberschneidung wird zurueckgewiesen, und zwar als solche")
    void anOverlapIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                SeasonCalendar.of(
                                        List.of(
                                                season("2026-q3", "2026-07-01", "2026-10-05"),
                                                season("2026-q4", "2026-10-01", "2026-12-31"))))
                .withMessageContaining("2026-q3")
                .withMessageContaining("2026-q4")
                .withMessageContaining("ueberschneiden");
    }

    @Test
    @DisplayName("FR-049 - eine Saison, die vor ihrem Beginn endet, wird zurueckgewiesen")
    void aSeasonThatEndsBeforeItStartsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> season("2026-q3", "2026-09-30", "2026-07-01"))
                .withMessageContaining("2026-q3");
    }

    @Test
    @DisplayName("ein doppelter Schluessel wird zurueckgewiesen")
    void aDuplicateKeyIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                SeasonCalendar.of(
                                        List.of(
                                                season("2026-q3", "2026-07-01", "2026-09-30"),
                                                season("2026-q3", "2026-10-01", "2026-12-31"))))
                .withMessageContaining("2026-q3")
                .withMessageContaining("zweimal");
    }

    @Test
    @DisplayName("ein leerer Kalender wird zurueckgewiesen")
    void anEmptyCalendarIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> SeasonCalendar.of(List.of()));
    }

    @Test
    @DisplayName("lueckenlos und ueberschneidungsfrei geht durch, auch unsortiert notiert")
    void aContiguousCalendarPassesEvenWhenWrittenOutOfOrder() {
        assertThatCode(
                        () ->
                                SeasonCalendar.of(
                                        List.of(
                                                season("2026-q4", "2026-10-01", "2026-12-31"),
                                                season("2026-q3", "2026-07-01", "2026-09-30"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("der Kalender ordnet nach Startdatum, nicht nach Notationsreihenfolge")
    void theCalendarIsOrderedByStartDate() {
        SeasonCalendar calendar =
                SeasonCalendar.of(
                        List.of(
                                season("2026-q4", "2026-10-01", "2026-12-31"),
                                season("2026-q3", "2026-07-01", "2026-09-30")));

        assertThat(calendar.all())
                .extracting(SeasonCalendar.Season::key)
                .containsExactly("2026-q3", "2026-q4");
    }

    @Test
    @DisplayName("eine einzelne Saison braucht keinen Nachbarn")
    void aSingleSeasonNeedsNoNeighbour() {
        assertThatCode(() -> SeasonCalendar.of(List.of(season("2026-q3", "2026-07-01", "2026-09-30"))))
                .doesNotThrowAnyException();
    }
}

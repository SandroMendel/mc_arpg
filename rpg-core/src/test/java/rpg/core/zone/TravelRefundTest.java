package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.currency.BookingReason;

/**
 * T098 - die wichtigste Zusicherung dieser Geschichte (FR-050b, FR-050f, SC-020a).
 *
 * <p>Es ist die einzige Stelle des ganzen Blocks, an der ein Fehler einem Spieler unmittelbar Besitz
 * nimmt. research.md R1 hat gezeigt, dass sie sich nicht wegkonstruieren laesst: B08b weigert sich
 * bewusst, Coins zu reservieren, und das Versetzen ist ein Paper-Aufruf, der scheitern darf. Also
 * wird nicht verhindert, sondern repariert - und dieser Test ist der Beleg, dass die Reparatur
 * wirklich passiert und nicht nur zugesagt ist.
 */
class TravelRefundTest {

    private static final String TARGET = "darkforest-crystal";

    private TravelFixture.RecordingCurrency purse;
    private TravelFixture.RecordingTeleporter teleporter;
    private Travel travel;

    private void given(long balance, boolean teleportSucceeds) throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        purse = new TravelFixture.RecordingCurrency(balance);
        teleporter = new TravelFixture.RecordingTeleporter();
        teleporter.succeeds = teleportSucceeds;
        travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith(TARGET),
                        holder -> false,
                        purse,
                        teleporter);
    }

    @Test
    @DisplayName("scheitert die Versetzung nach der Abbuchung, steht der Stand wieder am Anfang")
    void aFailedTeleportLeavesNoLoss() throws Exception {
        given(1000L, false);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .as("ein eigener Ausgang, damit der Fall zaehlbar ist")
                .isEqualTo(TravelResult.TELEPORT_FAILED);

        assertThat(purse.balance())
                .as("der Ausgangswert, nicht ungefaehr der Ausgangswert")
                .isEqualTo(1000L);
        assertThat(teleporter.destinations).as("es wurde ernsthaft versucht").hasSize(1);
    }

    @Test
    @DisplayName("der Verlauf zeigt beide Buchungen, und mit verschiedenem Grund (FR-050d)")
    void theHistoryShowsBothHalves() throws Exception {
        given(1000L, false);

        travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET);

        // Nicht "der Stand stimmt wieder" - das waere auch wahr, wenn gar nichts gebucht worden
        // waere. Der Beleg ist das Paar: erst genommen, dann zurueckgegeben, beides sichtbar.
        assertThat(purse.reasons())
                .containsExactly(BookingReason.WAYPOINT_TRAVEL, BookingReason.WAYPOINT_REFUND);
        assertThat(purse.bookings.get(0).credit()).isFalse();
        assertThat(purse.bookings.get(1).credit()).isTrue();
        assertThat(purse.bookings.get(1).amount())
                .as("zurueck kommt genau der Fahrpreis - nicht mehr und nicht weniger")
                .isEqualTo(purse.bookings.get(0).amount());
    }

    @Test
    @DisplayName("bei einer kostenlosen Reise gibt es nichts zurueckzugeben")
    void afreeJourneyRefundsNothing() throws Exception {
        java.util.Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> crystal =
                (java.util.Map<String, Object>)
                        ZoneFixture.zoneIn(document, "darkforest").get("crystal");
        crystal.put("price", 0);

        Zones zones = TravelFixture.load(document);
        purse = new TravelFixture.RecordingCurrency(1000L);
        teleporter = new TravelFixture.RecordingTeleporter();
        teleporter.succeeds = false;
        travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith(TARGET),
                        holder -> false,
                        purse,
                        teleporter);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .isEqualTo(TravelResult.TELEPORT_FAILED);
        assertThat(purse.bookings)
                .as("eine Buchung ueber null waere von B08b ohnehin abgelehnt worden")
                .isEmpty();
        assertThat(purse.balance()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("die geglueckte Reise bucht genau einmal - keine Rueckbuchung aus Versehen")
    void asuccessLeavesOnlyOneEntry() throws Exception {
        given(1000L, true);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .isEqualTo(TravelResult.OK);
        assertThat(purse.reasons()).containsExactly(BookingReason.WAYPOINT_TRAVEL);
        assertThat(purse.balance()).isEqualTo(975L);
    }

    @Test
    @DisplayName("hundert gescheiterte Reisen hintereinander kosten zusammen nichts")
    void repeatedFailuresNeverAccumulateALoss() throws Exception {
        given(1000L, false);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                    .isEqualTo(TravelResult.TELEPORT_FAILED);
        }

        // Ein Rundungs- oder Vorzeichenfehler in der Rueckbuchung faellt bei einem Versuch nicht
        // auf; bei hundert schon. Genau so verlieren Spieler in der Praxis Geld.
        assertThat(purse.balance()).isEqualTo(1000L);
        assertThat(purse.bookings).hasSize(200);
    }
}

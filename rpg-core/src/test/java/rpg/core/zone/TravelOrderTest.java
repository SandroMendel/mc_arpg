package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T097 - die Reihenfolge der Pruefungen ist Vertrag (FR-049, FR-050a, FR-051f, FR-050e, SC-020,
 * SC-023).
 *
 * <p>Jede Ablehnung wird an zwei Dingen gemessen: der Stand ist unveraendert <em>und</em> der Verlauf
 * ist leer. Das zweite ist die eigentliche Zusage - eine Buchung, die sich selbst zurueckdreht, waere
 * am Kontostand nicht zu erkennen, im Verlauf aber sehr wohl.
 */
class TravelOrderTest {

    private static final String TARGET = "pale-wilds-crystal";

    @Test
    @DisplayName("gesperrtes Ziel: keine Buchung, keine Versetzung (FR-049)")
    void aLockedCrystalBooksNothing() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(1000L);
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("greenfields-crystal"),
                        holder -> false,
                        purse,
                        teleporter);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .isEqualTo(TravelResult.NOT_UNLOCKED);
        assertThat(purse.balance()).isEqualTo(1000L);
        assertThat(purse.bookings).isEmpty();
        assertThat(teleporter.destinations).isEmpty();
    }

    @Test
    @DisplayName("im Kampf: keine Buchung, keine Versetzung (FR-051f, SC-023)")
    void combatBlocksTheJourney() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(1000L);
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith(TARGET),
                        holder -> true,
                        purse,
                        teleporter);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .isEqualTo(TravelResult.IN_COMBAT);
        assertThat(purse.bookings).isEmpty();
        assertThat(teleporter.destinations).isEmpty();
    }

    @Test
    @DisplayName("zu wenig Coins: Stand und Ort unveraendert (FR-050a, SC-020)")
    void anEmptyPurseChangesNothing() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(24L);
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith(TARGET),
                        holder -> false,
                        purse,
                        teleporter);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .isEqualTo(TravelResult.NOT_ENOUGH_COINS);
        assertThat(purse.balance()).as("kein Teilabzug, kein Deckel auf null").isEqualTo(24L);
        assertThat(purse.bookings).isEmpty();
        assertThat(teleporter.destinations).isEmpty();
    }

    @Test
    @DisplayName("das Kampfverbot wird vor der Buchung geprueft, nicht danach (FR-050e)")
    void theCheapChecksComeFirst() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(1000L);
        // Gesperrt UND im Kampf: haette die Buchung vorne gestanden, waere hier Geld geflossen,
        // bevor ueberhaupt klar war, dass die Reise nicht stattfindet.
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith(),
                        holder -> true,
                        purse,
                        new TravelFixture.RecordingTeleporter());

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .as("gesperrt schlaegt Kampf - die Reihenfolge aus FR-050e")
                .isEqualTo(TravelResult.NOT_UNLOCKED);
        assertThat(purse.bookings).isEmpty();
    }

    @Test
    @DisplayName("ein Kristall, der zwischen Oeffnen und Klick verschwand, bucht nichts")
    void aVanishedCrystalBooksNothing() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(1000L);
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("atlantis-crystal"),
                        holder -> false,
                        purse,
                        new TravelFixture.RecordingTeleporter());

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "atlantis-crystal"))
                .isEqualTo(TravelResult.NO_SUCH_CRYSTAL);
        assertThat(purse.bookings).isEmpty();
    }

    @Test
    @DisplayName("die Reise gelingt: genau eine Abbuchung mit dem Reisegrund")
    void aSuccessfulJourneyBooksOnce() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(1000L);
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith(TARGET),
                        holder -> false,
                        purse,
                        teleporter);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, TARGET))
                .isEqualTo(TravelResult.OK);
        assertThat(purse.balance()).isEqualTo(975L);
        assertThat(purse.reasons())
                .containsExactly(rpg.core.currency.BookingReason.WAYPOINT_TRAVEL);
        assertThat(teleporter.destinations).hasSize(1);
    }
}

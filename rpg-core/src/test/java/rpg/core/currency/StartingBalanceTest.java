package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T021 - das Startguthaben ist eine Buchung, kein Lesewert (US1 Szenarien 7 und 7a, FR-011a).
 */
class StartingBalanceTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("Startguthaben null: der Stand ist null, und es entsteht KEINE Buchung")
    void zeroBooksNothing() {
        CurrencyFixture.Harness harness = CurrencyFixture.startingWith(0L);

        assertThat(harness.currency.onCharacterCreated(character)).isEqualTo(BookingResult.OK);

        assertThat(harness.currency.balanceOf(character)).hasValue(0L);
        assertThat(harness.ledger.entries)
                .as("nichts zu buchen heisst nichts aufzuzeichnen (FR-011c)")
                .isEmpty();
    }

    @Test
    @DisplayName("Startguthaben 500: eine Gutschrift mit dem Grund STARTING_BALANCE im Verlauf")
    void positiveIsBookedWithItsOwnReason() {
        CurrencyFixture.Harness harness = CurrencyFixture.startingWith(500L);

        assertThat(harness.currency.onCharacterCreated(character)).isEqualTo(BookingResult.OK);

        assertThat(harness.currency.balanceOf(character)).hasValue(500L);
        assertThat(harness.ledger.forCharacter(character))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.reason()).isEqualTo(BookingReason.STARTING_BALANCE);
                            assertThat(entry.direction()).isEqualTo(LedgerEntry.Direction.CREDIT);
                            assertThat(entry.amount()).isEqualTo(500L);
                            assertThat(entry.balanceBefore()).isZero();
                            assertThat(entry.balanceAfter()).isEqualTo(500L);
                            assertThat(entry.actor())
                                    .as("kein Eingriff eines Betreibers")
                                    .isEmpty();
                        });
    }

    @Test
    @DisplayName("das Startguthaben merkt den Charakter zum Schreiben vor")
    void startingBalanceIsQueuedForWriting() {
        CurrencyFixture.Harness harness = CurrencyFixture.startingWith(500L);

        harness.currency.onCharacterCreated(character);

        assertThat(harness.repository.marked(character)).isTrue();
    }

    @Test
    @DisplayName("die erste Ausruestungsstufe bleibt ohne Coins erreichbar (SC-007)")
    void firstTierStaysReachableWithoutCoins() {
        CurrencyFixture.Harness harness = CurrencyFixture.startingWith(0L);
        harness.currency.onCharacterCreated(character);

        assertThat(harness.currency.canAfford(character, 0L))
                .as("ein leerer cost-Block ist kostenlos - dafuer braucht es kein Startguthaben")
                .isTrue();
    }
}

package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T019 - Pruefen und Abziehen sind unteilbar (US1 Szenario 3, FR-006, SC-001).
 *
 * <p>Der Fehler, den dieser Test ausschliesst, ist der teuerste des Blocks: zwei Faehigkeiten im
 * selben Tick fragen beide „kann ich zahlen", bekommen beide ja, und ziehen beide ab. Sichtbar wird
 * er erst als negativer Stand oder als Geld aus dem Nichts.
 */
class BookingAtomicityTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("zwei Buchungen ueber je 400 auf 500: genau eine gelingt")
    void twoBookingsOneWins() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 500L);

        BookingResult first = harness.currency.debit(character, 400L, BookingReason.EQUIPMENT_TIER);
        BookingResult second = harness.currency.debit(character, 400L, BookingReason.ABILITY_RANK);

        assertThat(first).isEqualTo(BookingResult.OK);
        assertThat(second).isEqualTo(BookingResult.NOT_ENOUGH);
        assertThat(harness.currency.balanceOf(character)).hasValue(100L);
    }

    @Test
    @DisplayName("1000 gleichzeitige Abbuchungen erzeugen keinen negativen Stand (SC-001)")
    void aThousandConcurrentDebits() throws Exception {
        long startingBalance = 1000L;
        long each = 3L;
        int attempts = 1000;

        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, startingBalance);

        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            for (int i = 0; i < attempts; i++) {
                pool.execute(
                        () -> {
                            try {
                                ready.await();
                                if (harness.currency
                                        .debit(character, each, BookingReason.VENDOR_PURCHASE)
                                        .isSuccess()) {
                                    succeeded.incrementAndGet();
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        });
            }
            ready.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("alle Versuche beendet").isTrue();
        } finally {
            pool.shutdownNow();
        }

        long remaining = harness.currency.balanceOf(character).orElseThrow();
        long spent = succeeded.get() * each;

        assertThat(remaining).as("niemals negativ").isNotNegative();
        assertThat(spent)
                .as("die Summe der gelungenen Abbuchungen uebersteigt den Ausgangsstand nicht")
                .isLessThanOrEqualTo(startingBalance);
        assertThat(remaining + spent)
                .as("nichts ist verschwunden und nichts entstanden")
                .isEqualTo(startingBalance);
        assertThat(harness.ledger.forCharacter(character))
                .as("je gelungener Buchung genau ein Verlaufseintrag")
                .hasSize(succeeded.get());
    }

    @Test
    @DisplayName("gleichzeitige Gutschriften summieren sich vollstaendig - keine geht verloren")
    void concurrentCreditsAllArrive() throws Exception {
        int attempts = 500;
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 0L);

        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            for (int i = 0; i < attempts; i++) {
                pool.execute(
                        () -> {
                            try {
                                ready.await();
                                harness.currency.credit(
                                        character, 2L, BookingReason.PILE_PICKED_UP);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        });
            }
            ready.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(harness.currency.balanceOf(character))
                .as("eine verlorene Gutschrift waere ein Spieler, der seine Coins nicht bekommt")
                .hasValue(attempts * 2L);
    }
}

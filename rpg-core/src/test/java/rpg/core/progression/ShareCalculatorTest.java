package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T044 - der herausgeloeste Anteilsrechner (ADR-029).
 *
 * <p>Dieselbe Regel, die {@code XpDistributorTest} fuer Erfahrung prueft, hier ohne Vergabe: nur die
 * Aufteilung. Dass beide gruen sind, <b>ohne</b> dass an B06s Tests etwas geaendert wurde, ist die
 * Abnahme der Herausloesung.
 */
class ShareCalculatorTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    private ProgressionFixture fixture;
    private PartyRegistry parties;
    private ProgressionConfig config;

    @BeforeEach
    void setUp() {
        Map<Integer, Long> curve = new LinkedHashMap<>();
        curve.put(2, 1_000L);
        fixture = new ProgressionFixture(ProgressionFixture.config(curve));
        config = fixture.config;
        parties =
                new PartyRegistry(
                        fixture.sessions,
                        fixture.eventBus,
                        fixture.clock,
                        5,
                        java.time.Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("ein einzelner Beitragender mit ganzem Anteil bekommt den ganzen Betrag")
    void singleContributorGetsEverything() {
        Map<UUID, Long> paid = allocate(shares(alice, 1.0), 100L);

        assertThat(paid).containsExactly(java.util.Map.entry(alice, 100L));
    }

    @Test
    @DisplayName("60 % und 40 % ohne Party werden 60 und 40 - rein anteilig, keine Schwelle")
    void proportionalWithoutAnyThreshold() {
        Map<UUID, Long> paid = allocate(shares(alice, 0.6, bob, 0.4), 100L);

        assertThat(paid).containsEntry(alice, 60L).containsEntry(bob, 40L);
    }

    @Test
    @DisplayName("20 % Beteiligung ergeben 20 %, nicht nichts (FR-024a)")
    void twentyPercentGetsTwentyPercent() {
        Map<UUID, Long> paid = allocate(shares(alice, 0.8, bob, 0.2), 100L);

        assertThat(paid)
                .as("eine Mindestbeteiligung gibt es nicht - bei Erfahrung nicht und bei Coins auch nicht")
                .containsEntry(bob, 20L);
    }

    @Test
    @DisplayName("abgerundet wird nach unten, der Rest bleibt liegen")
    void roundsDownAndLeavesTheRemainder() {
        Map<UUID, Long> paid = allocate(shares(alice, 0.333, bob, 0.667), 10L);

        long total = paid.values().stream().mapToLong(Long::longValue).sum();
        assertThat(total)
                .as("aufrunden erzeugte Coins aus dem Nichts, bei 800 Mobs sichtbar")
                .isLessThanOrEqualTo(10L);
        assertThat(paid).containsEntry(alice, 3L).containsEntry(bob, 6L);
    }

    @Test
    @DisplayName("ein Anteil, der nicht auf eins aufgeht, wird gar nicht gemeldet")
    void aShareTooSmallIsNotReported() {
        Map<UUID, Long> paid = allocate(shares(alice, 0.99, bob, 0.01), 10L);

        assertThat(paid).containsEntry(alice, 9L);
        assertThat(paid).as("0,1 abgerundet ist null - und null wird nicht gemeldet").doesNotContainKey(bob);
    }

    @Test
    @DisplayName("eine leere Aufteilung meldet niemanden und ist kein Fehler")
    void emptySharesReportNobody() {
        assertThat(allocate(Map.of(), 100L)).isEmpty();
    }

    @Test
    @DisplayName("ein Betrag von null meldet niemanden")
    void zeroAmountReportsNobody() {
        assertThat(allocate(shares(alice, 1.0), 0L)).isEmpty();
    }

    @Test
    @DisplayName("die Anteile werden uebernommen, nie neu berechnet")
    void sharesAreTakenAsGiven() {
        // Absichtlich mehr als 100 %: der Rechner korrigiert das nicht, er rechnet damit. Das ist
        // die Zusage aus B05 - die Aufteilung entsteht dort und nirgends sonst.
        Map<UUID, Long> paid = allocate(shares(alice, 0.9, bob, 0.9), 100L);

        assertThat(paid).containsEntry(alice, 90L).containsEntry(bob, 90L);
    }

    @Test
    @DisplayName("ohne registrierte Naeheprüfung zaehlt nur der Beitragende selbst")
    void withoutAProximityCheckOnlyTheContributorCounts() {
        Map<UUID, Long> paid = allocate(shares(carol, 1.0), 50L);

        assertThat(paid)
                .as("nicht alle bezahlen und nicht alles verschlucken (FR-044)")
                .containsExactly(java.util.Map.entry(carol, 50L));
    }

    // --- Hilfsmittel -----------------------------------------------------

    private Map<UUID, Long> allocate(Map<UUID, Double> shares, long amount) {
        ShareCalculator calculator = new ShareCalculator(parties, config, () -> null);
        Map<UUID, Long> paid = new HashMap<>();
        calculator.allocate(shares, amount, null, (holder, share) -> paid.merge(holder, share, Long::sum));
        return paid;
    }

    private static Map<UUID, Double> shares(Object... pairs) {
        Map<UUID, Double> shares = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            shares.put((UUID) pairs[i], (Double) pairs[i + 1]);
        }
        return shares;
    }
}

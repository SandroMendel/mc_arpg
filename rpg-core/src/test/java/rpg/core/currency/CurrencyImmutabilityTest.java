package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.progression.WorldPoint;

/**
 * T118 - die Werte dieses Blocks sind unveraenderlich.
 *
 * <p>Nicht Formsache: ein Verlaufseintrag, den jemand nachtraeglich aendern kann, ist kein Verlauf,
 * und ein Kontostand, der sich im Vorbeigehen aendern laesst, waere ein zweiter Schreibweg neben der
 * Sperre, die es genau dafuer gibt.
 */
class CurrencyImmutabilityTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("ein Kontostand aendert sich nicht - eine Buchung erzeugt einen neuen")
    void aBalanceIsReplacedNeverChanged() {
        CharacterBalance before = new CharacterBalance(character, 100L, 1, 1L);
        CharacterBalance after = before.withBalance(500L);

        assertThat(before.balance()).as("unberuehrt").isEqualTo(100L);
        assertThat(after.balance()).isEqualTo(500L);
        assertThat(after).isNotSameAs(before);
    }

    @Test
    @DisplayName("ein Verlaufseintrag ist nicht nachtraeglich zu aendern")
    void aLedgerEntryCannotBeEdited() {
        LedgerEntry entry =
                LedgerEntry.pending(
                        character,
                        Instant.EPOCH,
                        100L,
                        LedgerEntry.Direction.CREDIT,
                        BookingReason.PILE_PICKED_UP,
                        0L,
                        100L,
                        Optional.empty());

        // Ein Record ohne with-Methoden: es gibt keinen Weg hinein. Der Test haelt fest, dass das so
        // bleiben soll - ein aenderbarer Verlauf ist keiner.
        assertThat(entry.getClass().isRecord()).isTrue();
        assertThat(entry.getClass().getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    @DisplayName("die Ertragskarte der Konfiguration laesst sich von aussen nicht aendern")
    void theDropTableIsCopied() {
        Map<String, Long> mutable = new HashMap<>();
        mutable.put("ZOMBIE", 5L);
        CurrencyConfig config =
                new CurrencyConfig(
                        0L,
                        4L,
                        mutable,
                        java.time.Duration.ofSeconds(120),
                        3.0d,
                        400,
                        java.time.Duration.ofDays(30),
                        45);

        mutable.put("ZOMBIE", 9999L);

        assertThat(config.dropFor("ZOMBIE"))
                .as("eine geliehene Karte waere Balancing, das sich zur Laufzeit aendert")
                .isEqualTo(5L);
        assertThatThrownBy(() -> config.dropsByType().put("CREEPER", 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("ein Wurfplan ist unveraenderlich und verlangt einen positiven Betrag")
    void adropPlanIsImmutableAndPositive() {
        WorldPoint origin = new WorldPoint(UUID.randomUUID(), 0, 64, 0);
        CoinDropPlan plan = new CoinDropPlan(character, UUID.randomUUID(), 5L, origin);

        assertThat(plan.getClass().isRecord()).isTrue();
        assertThatThrownBy(
                        () -> new CoinDropPlan(character, UUID.randomUUID(), 0L, origin))
                .as("ein Plan ueber nichts wird gar nicht erst gebaut")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("eine Kostenangabe ist unveraenderlich")
    void acostSpecIsImmutable() {
        assertThat(CostSpec.parse(Map.of("coins", 500), "irgendwo").getClass().isRecord()).isTrue();
        assertThat(CostSpec.FREE.isFree()).isTrue();
    }
}

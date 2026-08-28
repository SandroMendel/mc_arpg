package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T099 - der undurchsichtige {@code cost}-Block, endlich gelesen (FR-049, FR-050).
 */
class CostSpecTest {

    private static final String WHERE = "classes.warrior.armor.tier 3";

    @Test
    @DisplayName("cost: { coins: 500 } ergibt 500")
    void coinsAreRead() {
        assertThat(CostSpec.parse(Map.of("coins", 500), WHERE).coins()).isEqualTo(500L);
    }

    @Test
    @DisplayName("ein leerer Block heisst kostenlos (FR-049)")
    void anEmptyBlockIsFree() {
        CostSpec cost = CostSpec.parse(Map.of(), WHERE);

        assertThat(cost.isFree()).isTrue();
        assertThat(cost.coins()).isZero();
    }

    @Test
    @DisplayName("eine ausdrueckliche Null heisst dasselbe")
    void anExplicitZeroIsAlsoFree() {
        assertThat(CostSpec.parse(Map.of("coins", 0), WHERE).isFree()).isTrue();
    }

    @Test
    @DisplayName("ein unbekannter Schluessel ist ein Fehler und nennt Klasse und Stufe (FR-050)")
    void anUnknownKeyIsRefusedAndSaysWhere() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("coins", 500);
        block.put("shards", 7);

        assertThatThrownBy(() -> CostSpec.parse(block, WHERE))
                .as("ein Preis, den niemand verlangen kann, wuerde nie bezahlt")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shards")
                .hasMessageContaining(WHERE);
    }

    @Test
    @DisplayName("Grossschreibung im Schluessel wird akzeptiert - ein Tippfehler ist es nicht")
    void theKeyIsCaseInsensitive() {
        assertThat(CostSpec.parse(Map.of("Coins", 250), WHERE).coins()).isEqualTo(250L);
    }

    @Test
    @DisplayName("ein nicht-numerischer Betrag wird abgelehnt")
    void anonNumericAmountIsRefused() {
        assertThatThrownBy(() -> CostSpec.parse(Map.of("coins", "viele"), WHERE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(WHERE);
    }

    @Test
    @DisplayName("ein negativer Preis wird abgelehnt")
    void anegativePriceIsRefused() {
        assertThatThrownBy(() -> CostSpec.parse(Map.of("coins", -5), WHERE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}

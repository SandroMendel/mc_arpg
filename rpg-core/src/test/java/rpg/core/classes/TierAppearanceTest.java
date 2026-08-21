package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T016 - Unterscheidbarkeit ist das Tripel aus Material, Farbe und Trim (FR-016).
 *
 * <p>Diese Regel traegt zwei der drei Klassen: der Mage bleibt durchgehend auf Leder, der Rogue ab
 * Stufe 4 auf Kettenhemd. Waere Unterscheidbarkeit am Material allein festgemacht, waere beides
 * unmoeglich.
 */
class TierAppearanceTest {

    @Test
    @DisplayName("gleiches Material, verschiedene Farbe: unterscheidbar (Mage)")
    void sameMaterialDifferentColour() {
        TierAppearance first = TierAppearance.dyed("LEATHER", 0x4a4a52);
        TierAppearance second = TierAppearance.dyed("LEATHER", 0x1f3a93);

        assertThat(first.looksLike(second)).isFalse();
        assertThat(first.material()).isEqualTo(second.material());
    }

    @Test
    @DisplayName("gleiches Material, verschiedener Trim: unterscheidbar (Rogue)")
    void sameMaterialDifferentTrim() {
        TierAppearance first = TierAppearance.trimmed("CHAINMAIL", "COPPER", "RIB");
        TierAppearance second = TierAppearance.trimmed("CHAINMAIL", "AMETHYST", "SILENCE");

        assertThat(first.looksLike(second)).isFalse();
    }

    @Test
    @DisplayName("gleiches Material ohne Farbe und ohne Trim: nicht unterscheidbar")
    void sameMaterialNothingElse() {
        assertThat(
                        TierAppearance.ofMaterial("LEATHER")
                                .looksLike(TierAppearance.ofMaterial("LEATHER")))
                .isTrue();
    }

    @Test
    @DisplayName("Material, Farbe und Trim alle gleich: nicht unterscheidbar (FR-016)")
    void everythingEqual() {
        TierAppearance first = new TierAppearance("CHAINMAIL", 0x112233, "COPPER", "RIB", null);
        TierAppearance second = new TierAppearance("CHAINMAIL", 0x112233, "COPPER", "RIB", null);

        assertThat(first.looksLike(second)).isTrue();
    }

    @Test
    @DisplayName("verschiedenes Material: unterscheidbar (Warrior)")
    void differentMaterial() {
        assertThat(
                        TierAppearance.ofMaterial("COPPER")
                                .looksLike(TierAppearance.ofMaterial("IRON")))
                .isFalse();
    }

    @Test
    @DisplayName("modelData macht keine Stufe sichtbar - ohne Resource Pack rendert es nichts (ADR-005)")
    void modelDataDoesNotDistinguish() {
        TierAppearance withoutModel = new TierAppearance("LEATHER", null, null, null, null);
        TierAppearance withModel = new TierAppearance("LEATHER", null, null, null, 42);

        assertThat(withoutModel.looksLike(withModel)).isTrue();
    }

    @Test
    @DisplayName("halber Trim wird abgewiesen - beide Felder oder keines")
    void halfTrimIsRejected() {
        assertThatThrownBy(() -> new TierAppearance("CHAINMAIL", null, "COPPER", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");

        assertThatThrownBy(() -> new TierAppearance("CHAINMAIL", null, null, "RIB", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
    }

    @Test
    @DisplayName("ein leeres Material wird abgewiesen")
    void blankMaterialIsRejected() {
        assertThatThrownBy(() -> TierAppearance.ofMaterial("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("material");
    }

    @Test
    @DisplayName("hasColor und hasTrim spiegeln die gesetzten Felder")
    void flagsReflectFields() {
        assertThat(TierAppearance.dyed("LEATHER", 0x000000).hasColor()).isTrue();
        assertThat(TierAppearance.dyed("LEATHER", 0x000000).hasTrim()).isFalse();
        assertThat(TierAppearance.trimmed("CHAINMAIL", "COPPER", "RIB").hasTrim()).isTrue();
        assertThat(TierAppearance.trimmed("CHAINMAIL", "COPPER", "RIB").hasColor()).isFalse();
        assertThat(TierAppearance.ofMaterial("IRON").hasColor()).isFalse();
        assertThat(TierAppearance.ofMaterial("IRON").hasTrim()).isFalse();
    }
}

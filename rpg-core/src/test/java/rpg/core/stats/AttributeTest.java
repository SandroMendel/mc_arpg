package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T010: the closed set of attributes (FR-001, FR-004, FR-004a). */
class AttributeTest {

    @Test
    @DisplayName("there are exactly ten attributes")
    void tenAttributes() {
        assertThat(Attribute.count()).isEqualTo(10);
        assertThat(Attribute.all()).hasSize(10);
    }

    @Test
    @DisplayName("every attribute has a distinct configuration key")
    void distinctKeys() {
        assertThat(Arrays.stream(Attribute.all()).map(Attribute::key).collect(Collectors.toSet()))
                .hasSize(Attribute.count());
    }

    @Test
    @DisplayName("the keys are exactly the ones the block brief and ADR-023 name")
    void keysMatchTheBrief() {
        assertThat(Arrays.stream(Attribute.all()).map(Attribute::key))
                .containsExactly(
                        "health",
                        "healthRegen",
                        "defense",
                        "mana",
                        "manaRegen",
                        "physicalDamage",
                        "magicDamage",
                        "attackSpeed",
                        "movementSpeed",
                        "abilityCooldown");
    }

    @Test
    @DisplayName("byKey resolves every valid key")
    void byKeyResolvesAll() {
        for (Attribute attribute : Attribute.all()) {
            assertThat(Attribute.byKey(attribute.key())).isSameAs(attribute);
        }
    }

    @Test
    @DisplayName("an unknown key is refused and the message lists the valid ones")
    void unknownKeyIsRefused() {
        assertThatThrownBy(() -> Attribute.byKey("phyiscalDamage"))
                .isInstanceOf(UnknownAttributeException.class)
                .hasMessageContaining("phyiscalDamage")
                .hasMessageContaining("physicalDamage")
                .hasMessageContaining("abilityCooldown");
    }

    @Test
    @DisplayName("only abilityCooldown is a percent attribute")
    void onlyCooldownIsPercent() {
        assertThat(
                        Arrays.stream(Attribute.all())
                                .filter(a -> a.kind() == AttributeKind.PERCENT)
                                .toList())
                .containsExactly(Attribute.ABILITY_COOLDOWN);
    }

    @Test
    @DisplayName("ordinals are dense and start at zero - the value arrays depend on it")
    void ordinalsAreUsableAsArrayIndices() {
        Attribute[] all = Attribute.all();
        for (int i = 0; i < all.length; i++) {
            assertThat(all[i].ordinal()).isEqualTo(i);
        }
    }
}

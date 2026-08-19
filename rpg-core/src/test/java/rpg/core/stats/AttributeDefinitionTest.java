package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T009: every invariant of an attribute definition, checked against the message rather than only
 * the exception type.
 *
 * <p>The message is the product here. An operator who edits {@code stats.yml} and gets
 * "IllegalArgumentException" learns that something is wrong; one who gets "attribute 'health': min
 * (0.0) must be at least 1.0" learns what to change.
 */
class AttributeDefinitionTest {

    @Test
    @DisplayName("min must be strictly below max, and the message names both")
    void minBelowMax() {
        assertThatThrownBy(() -> new AttributeDefinition(Attribute.DEFENSE, 0.0, 300.0, 300.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defense")
                .hasMessageContaining("min")
                .hasMessageContaining("max");
    }

    @Test
    @DisplayName("base must lie inside the bounds")
    void baseWithinBounds() {
        assertThatThrownBy(() -> new AttributeDefinition(Attribute.MANA, 900.0, 0.0, 500.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mana")
                .hasMessageContaining("base");
    }

    @Test
    @DisplayName("non-finite values are refused, and the message names the field")
    void nonFiniteRefused() {
        assertThatThrownBy(
                        () ->
                                new AttributeDefinition(
                                        Attribute.MANA, Double.NaN, 0.0, 500.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mana")
                .hasMessageContaining("base");

        assertThatThrownBy(
                        () ->
                                new AttributeDefinition(
                                        Attribute.MANA, 50.0, 0.0, Double.POSITIVE_INFINITY, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mana")
                .hasMessageContaining("max");
    }

    @Test
    @DisplayName("health cannot have a maximum of zero")
    void healthFloor() {
        assertThatThrownBy(() -> new AttributeDefinition(Attribute.HEALTH, 100.0, 0.0, 2000.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("health")
                .hasMessageContaining("min");
    }

    @Test
    @DisplayName("a percent attribute may not exceed the [-1, 1] range")
    void percentRange() {
        assertThatThrownBy(
                        () ->
                                new AttributeDefinition(
                                        Attribute.ABILITY_COOLDOWN, 0.0, 0.0, 40.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abilityCooldown")
                .hasMessageContaining("percent");
    }

    @Test
    @DisplayName("attack and movement speed require a band greater than zero")
    void bandRequiredForSpeeds() {
        assertThatThrownBy(
                        () -> new AttributeDefinition(Attribute.ATTACK_SPEED, 4.0, 0.0, 1024.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attackSpeed")
                .hasMessageContaining("modifier-band");

        assertThatThrownBy(
                        () ->
                                new AttributeDefinition(
                                        Attribute.MOVEMENT_SPEED, 0.1, 0.0, 1.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("movementSpeed");
    }

    @Test
    @DisplayName("a band on any other attribute is refused rather than silently ignored")
    void strayBandRefused() {
        assertThatThrownBy(() -> new AttributeDefinition(Attribute.HEALTH, 100.0, 1.0, 2000.0, 0.25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("health")
                .hasMessageContaining("no effect");
    }

    @Test
    @DisplayName("a negative band is refused")
    void negativeBandRefused() {
        assertThatThrownBy(
                        () ->
                                new AttributeDefinition(
                                        Attribute.ATTACK_SPEED, 4.0, 0.0, 1024.0, -0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attackSpeed")
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("band floor and ceiling follow from base and band")
    void bandBounds() {
        AttributeDefinition definition =
                new AttributeDefinition(Attribute.ATTACK_SPEED, 4.0, 0.0, 1024.0, 0.50);
        assertThat(definition.hasBand()).isTrue();
        assertThat(definition.bandFloor(4.0)).isEqualTo(2.0);
        assertThat(definition.bandCeiling(4.0)).isEqualTo(6.0);
        // The band follows the effective base, so a levelled-up holder keeps the same relative room.
        assertThat(definition.bandFloor(8.0)).isEqualTo(4.0);
        assertThat(definition.bandCeiling(8.0)).isEqualTo(12.0);
    }

    @Test
    @DisplayName("a configuration missing an attribute names it")
    void missingAttributeIsNamed() {
        Map<Attribute, AttributeDefinition> incomplete =
                new EnumMap<>(StatConfig.defaults().definitions());
        incomplete.remove(Attribute.MAGIC_DAMAGE);

        assertThatThrownBy(() -> new StatConfig(incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("magicDamage")
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("the shipped defaults are themselves valid and complete")
    void defaultsAreValid() {
        StatConfig config = StatConfig.defaults();
        assertThat(config.definitions()).hasSize(Attribute.count());
        assertThat(config.definition(Attribute.HEALTH).base()).isEqualTo(100.0);
        assertThat(config.definition(Attribute.ABILITY_COOLDOWN).max()).isEqualTo(0.40);
        assertThat(config.definition(Attribute.ATTACK_SPEED).modifierBand()).isEqualTo(0.50);
        assertThat(config.definition(Attribute.MOVEMENT_SPEED).modifierBand()).isEqualTo(0.30);
    }
}

package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import rpg.core.stats.Attribute;

/** T015 - die Leiterlaenge ist Konfiguration, und der Endwert haengt nicht von ihr ab (SC-014). */
class EquipmentLadderTest {

    @ParameterizedTest(name = "{0} Stufen")
    @ValueSource(ints = {5, 6, 7})
    @DisplayName("Leitern verschiedener Laenge erreichen denselben Endwert (SC-014)")
    void endValueIsIndependentOfLength(int length) {
        EquipmentLadder ladder = LadderFixture.rising(LadderSlot.ARMOR, length, 60.0, 1400.0);

        assertThat(ladder.length()).isEqualTo(length);
        assertThat(ladder.tier(1).valueOf(Attribute.HEALTH)).isEqualTo(60.0);
        assertThat(ladder.top().valueOf(Attribute.HEALTH)).isEqualTo(1400.0);
        assertThat(ladder.isTop(length)).isTrue();
    }

    @ParameterizedTest(name = "{0} Stufen")
    @ValueSource(ints = {5, 6, 7})
    @DisplayName("jede Stufe liegt ueber ihrer Vorstufe, unabhaengig von der Laenge (FR-017)")
    void everyTierIsStrictlyAboveItsPredecessor(int length) {
        EquipmentLadder ladder = LadderFixture.rising(LadderSlot.WEAPON, length, 3.0, 110.0);

        for (int i = 2; i <= length; i++) {
            assertThat(ladder.tier(i).valueOf(Attribute.PHYSICAL_DAMAGE))
                    .isGreaterThan(ladder.tier(i - 1).valueOf(Attribute.PHYSICAL_DAMAGE));
            assertThat(ladder.tier(i).requiredLevel())
                    .isGreaterThan(ladder.tier(i - 1).requiredLevel());
        }
    }

    @Test
    @DisplayName("eine Leiter mit einer Stufe wird abgewiesen - es gaebe nichts aufzusteigen (FR-013)")
    void singleTierIsRejected() {
        List<EquipmentTier> one =
                List.of(
                        LadderFixture.tier(
                                1, LadderSlot.ARMOR, 60.0, TierAppearance.ofMaterial("LEATHER"), 1));

        assertThatThrownBy(() -> EquipmentLadder.of(LadderSlot.ARMOR, one))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 tiers");
    }

    @Test
    @DisplayName("eine nicht steigende Leiter wird abgewiesen und nennt die Stufe (FR-017)")
    void nonIncreasingLadderIsRejected() {
        List<EquipmentTier> flat =
                List.of(
                        LadderFixture.tier(
                                1, LadderSlot.ARMOR, 60.0, TierAppearance.ofMaterial("LEATHER"), 1),
                        LadderFixture.tier(
                                2, LadderSlot.ARMOR, 60.0, TierAppearance.ofMaterial("COPPER"), 15));

        assertThatThrownBy(() -> EquipmentLadder.of(LadderSlot.ARMOR, flat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tier 2")
                .hasMessageContaining("must exceed tier 1");
    }

    @Test
    @DisplayName("nicht steigende Levelanforderungen werden abgewiesen (FR-018)")
    void nonIncreasingLevelRequirementIsRejected() {
        List<EquipmentTier> tiers =
                List.of(
                        LadderFixture.tier(
                                1, LadderSlot.ARMOR, 60.0, TierAppearance.ofMaterial("LEATHER"), 1),
                        LadderFixture.tier(
                                2, LadderSlot.ARMOR, 300.0, TierAppearance.ofMaterial("COPPER"), 1));

        assertThatThrownBy(() -> EquipmentLadder.of(LadderSlot.ARMOR, tiers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required-level");
    }

    @Test
    @DisplayName("Stufe 1 muss auf Level 1 tragbar sein - ein frischer Charakter traegt sie")
    void firstTierMustBeWearableAtLevelOne() {
        List<EquipmentTier> tiers =
                List.of(
                        LadderFixture.tier(
                                1, LadderSlot.ARMOR, 60.0, TierAppearance.ofMaterial("LEATHER"), 5),
                        LadderFixture.tier(
                                2, LadderSlot.ARMOR, 300.0, TierAppearance.ofMaterial("COPPER"), 15));

        assertThatThrownBy(() -> EquipmentLadder.of(LadderSlot.ARMOR, tiers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tier 1 must require level 1");
    }

    @Test
    @DisplayName("zwei ununterscheidbare Stufen werden abgewiesen (FR-016, SC-013)")
    void indistinguishableTiersAreRejected() {
        List<EquipmentTier> tiers =
                List.of(
                        LadderFixture.tier(
                                1, LadderSlot.ARMOR, 60.0, TierAppearance.ofMaterial("LEATHER"), 1),
                        LadderFixture.tier(
                                2, LadderSlot.ARMOR, 300.0, TierAppearance.ofMaterial("LEATHER"), 15));

        assertThatThrownBy(() -> EquipmentLadder.of(LadderSlot.ARMOR, tiers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indistinguishable")
                .hasMessageContaining("Set a colour or a trim");
    }

    @Test
    @DisplayName("gleiches Material mit anderer Farbe ist unterscheidbar - der Mage-Fall (FR-016a)")
    void sameMaterialWithDifferentColourIsFine() {
        EquipmentLadder ladder =
                EquipmentLadder.of(
                        LadderSlot.ARMOR,
                        List.of(
                                LadderFixture.tier(
                                        1,
                                        LadderSlot.ARMOR,
                                        40.0,
                                        TierAppearance.dyed("LEATHER", 0x4a4a52),
                                        1),
                                LadderFixture.tier(
                                        2,
                                        LadderSlot.ARMOR,
                                        120.0,
                                        TierAppearance.dyed("LEATHER", 0x1f3a93),
                                        11)));

        assertThat(ladder.length()).isEqualTo(2);
    }

    @Test
    @DisplayName("gleiches Material mit anderem Trim ist unterscheidbar - der Rogue-Fall (FR-016a)")
    void sameMaterialWithDifferentTrimIsFine() {
        EquipmentLadder ladder =
                EquipmentLadder.of(
                        LadderSlot.ARMOR,
                        List.of(
                                LadderFixture.tier(
                                        1,
                                        LadderSlot.ARMOR,
                                        350.0,
                                        TierAppearance.ofMaterial("CHAINMAIL"),
                                        1),
                                LadderFixture.tier(
                                        2,
                                        LadderSlot.ARMOR,
                                        575.0,
                                        TierAppearance.trimmed("CHAINMAIL", "COPPER", "RIB"),
                                        34)));

        assertThat(ladder.top().appearance().hasTrim()).isTrue();
    }

    @Test
    @DisplayName("eine Stufe darf kein Attribut des anderen Slots tragen (FR-015)")
    void foreignAttributeIsRejected() {
        assertThatThrownBy(
                        () ->
                                EquipmentTier.of(
                                        1,
                                        LadderSlot.ARMOR,
                                        Map.of(Attribute.PHYSICAL_DAMAGE, 10.0),
                                        TierAppearance.ofMaterial("LEATHER"),
                                        1,
                                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("physicalDamage");
    }

    @Test
    @DisplayName("ein fehlendes Attribut des eigenen Slots wird abgewiesen (FR-015)")
    void missingOwnAttributeIsRejected() {
        assertThatThrownBy(
                        () ->
                                EquipmentTier.of(
                                        1,
                                        LadderSlot.ARMOR,
                                        Map.of(Attribute.HEALTH, 60.0),
                                        TierAppearance.ofMaterial("LEATHER"),
                                        1,
                                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing value for");
    }

    @Test
    @DisplayName("der cost-Block wird unausgelegt durchgereicht (FR-021)")
    void costIsPassedThroughUninterpreted() {
        EquipmentTier tier =
                EquipmentTier.of(
                        1,
                        LadderSlot.ARMOR,
                        Map.of(
                                Attribute.HEALTH, 60.0,
                                Attribute.DEFENSE, 6.0,
                                Attribute.MANA, 18.0,
                                Attribute.MOVEMENT_SPEED, 0.0),
                        TierAppearance.ofMaterial("LEATHER"),
                        1,
                        Map.of("coins", 500, "irgendwas", "B11 entscheidet"));

        assertThat(tier.cost()).containsEntry("coins", 500);
        assertThat(tier.cost()).containsEntry("irgendwas", "B11 entscheidet");
    }
}

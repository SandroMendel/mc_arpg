package rpg.content.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import rpg.core.ability.AbilityConfig;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.AbilityKind;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassBaseStats;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassGrowth;
import rpg.core.classes.EquipmentLadder;
import rpg.core.classes.EquipmentTier;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.item.ItemConfig;
import rpg.core.item.LootEntry;
import rpg.core.item.LootTable;
import rpg.core.item.LootTables;
import rpg.core.item.RepairPricing;
import rpg.core.item.WearCurve;
import rpg.core.message.MessageKey;
import rpg.core.mob.Budget;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.scheduler.WorldPosition;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.zone.ZoneConfig;

class B16ContentReferenceValidatorTest {

    @Test
    void unknownClassAbilityContainsTheClassAndAbilityPath() {
        ClassConfig classes = classConfigWithMissingAbilities();
        AbilityConfig abilities = new AbilityConfig(Map.of(), Duration.ZERO, 1.0, 1.0);

        assertThatThrownBy(
                        () ->
                                B16ContentReferenceValidator.validateClassAbilities(
                                        Path.of("classes.yml"), classes, abilities))
                .isInstanceOf(ContentInvariantViolation.class)
                .satisfies(
                        thrown -> {
                            ContentInvariantViolation violation =
                                    (ContentInvariantViolation) thrown;
                            assertThat(violation.sourceId()).isEqualTo("WARRIOR");
                            assertThat(violation.targetId()).isEqualTo("missing.ability.1");
                            assertThat(violation.documentPath())
                                    .isEqualTo("classes.WARRIOR.abilities[1].id");
                        });
    }

    @Test
    void unknownHordeZoneContainsTheBoundSourceAndYamlPath() {
        MobConfig mobs =
                new MobConfig(
                        budget(),
                        Duration.ofSeconds(1),
                        0.0,
                        Duration.ofSeconds(1),
                        8.0,
                        Duration.ofSeconds(1),
                        Map.of("wolf", mobKind("wolf", false)),
                        Map.of(
                                "lost-zone",
                                new HordeSpec(
                                        "missing-zone",
                                        List.of(new HordeSpec.Entry("forest", "wolf", 1)),
                                        null)));

        assertThatThrownBy(
                        () ->
                                B16ContentReferenceValidator.validateMobHordes(
                                        Path.of("mobs.yml"), mobs, emptyZones()))
                .isInstanceOf(ContentInvariantViolation.class)
                .satisfies(
                        thrown -> {
                            ContentInvariantViolation violation =
                                    (ContentInvariantViolation) thrown;
                            assertThat(violation.sourceId()).isEqualTo("hordes.lost-zone");
                            assertThat(violation.targetId()).isEqualTo("missing-zone");
                            assertThat(violation.documentPath()).isEqualTo("hordes.lost-zone");
                        });
    }

    @Test
    void unknownLootTemplateContainsTheLootRegistryAndEntryPath() {
        ItemConfig items =
                new ItemConfig(
                        Map.of(),
                        new WearCurve(50.0, 0.2, 1.0, 1.0, 2.0, 2.0, List.of(), Duration.ZERO),
                        new RepairPricing(List.of()),
                        new LootTables(
                                Map.of(
                                        "wolf",
                                        new LootTable(
                                                List.of(LootEntry.single("missing.potion", 1.0)))),
                                Map.of(),
                                Map.of()),
                        Map.of(),
                        Duration.ZERO);

        assertThatThrownBy(
                        () ->
                                B16ContentReferenceValidator.validateItems(
                                        Path.of("items.yml"), items, mobConfig(), emptyZones()))
                .isInstanceOf(ContentInvariantViolation.class)
                .satisfies(
                        thrown -> {
                            ContentInvariantViolation violation =
                                    (ContentInvariantViolation) thrown;
                            assertThat(violation.sourceId()).isEqualTo("loot.by-kind.wolf");
                            assertThat(violation.targetId()).isEqualTo("missing.potion");
                            assertThat(violation.documentPath())
                                    .isEqualTo("loot.by-kind.wolf[1].template");
                        });
    }

    private static MobConfig mobConfig() {
        return new MobConfig(
                budget(),
                Duration.ofSeconds(1),
                0.0,
                Duration.ofSeconds(1),
                8.0,
                Duration.ofSeconds(1),
                Map.of("wolf", mobKind("wolf", false)),
                Map.of());
    }

    private static MobKind mobKind(String key, boolean boss) {
        return new MobKind(
                key,
                "WOLF",
                1,
                Map.of(Attribute.HEALTH, 10.0),
                8.0,
                MessageKey.of("mobs." + key),
                1L,
                1L,
                boss);
    }

    private static Budget budget() {
        return new Budget(10, 10, 10, 10);
    }

    private static ClassConfig classConfigWithMissingAbilities() {
        EnumMap<CharacterClass, CharacterClassDefinition> definitions =
                new EnumMap<>(CharacterClass.class);
        for (CharacterClass id : CharacterClass.values()) {
            List<AbilityBinding> abilities =
                    id == CharacterClass.WARRIOR
                            ? List.of(
                                    new AbilityBinding(
                                            "missing.ability.1", AbilityKind.ACTIVE, false, 1),
                                    new AbilityBinding(
                                            "missing.ability.2", AbilityKind.ACTIVE, false, 1),
                                    new AbilityBinding(
                                            "missing.ability.3", AbilityKind.ACTIVE, false, 1),
                                    new AbilityBinding(
                                            "missing.ability.4", AbilityKind.ACTIVE, false, 1),
                                    new AbilityBinding(
                                            "missing.ability.5", AbilityKind.ACTIVE, false, 1),
                                    new AbilityBinding(
                                            "missing.ability.6", AbilityKind.ACTIVE, false, 1))
                            : List.of();
            definitions.put(
                    id,
                    new CharacterClassDefinition(
                            id,
                            MessageKey.of("classes." + id.name().toLowerCase() + ".name"),
                            "STONE",
                            ClassBaseStats.of(new double[Attribute.count()]),
                            ClassGrowth.of(new double[Attribute.count()]),
                            ladder(id, LadderSlot.ARMOR),
                            ladder(id, LadderSlot.WEAPON),
                            abilities));
        }
        return ClassConfig.of(definitions);
    }

    private static EquipmentLadder ladder(CharacterClass id, LadderSlot slot) {
        Map<Attribute, Double> first =
                slot == LadderSlot.ARMOR
                        ? Map.of(
                                Attribute.HEALTH, 1.0,
                                Attribute.DEFENSE, 1.0,
                                Attribute.MANA, 1.0,
                                Attribute.MOVEMENT_SPEED, 1.0)
                        : Map.of(
                                Attribute.PHYSICAL_DAMAGE, 1.0,
                                Attribute.MAGIC_DAMAGE, 1.0,
                                Attribute.ATTACK_SPEED, 1.0,
                                Attribute.ABILITY_COOLDOWN, 1.0);
        Map<Attribute, Double> second =
                slot == LadderSlot.ARMOR
                        ? Map.of(
                                Attribute.HEALTH, 2.0,
                                Attribute.DEFENSE, 2.0,
                                Attribute.MANA, 2.0,
                                Attribute.MOVEMENT_SPEED, 2.0)
                        : Map.of(
                                Attribute.PHYSICAL_DAMAGE, 2.0,
                                Attribute.MAGIC_DAMAGE, 2.0,
                                Attribute.ATTACK_SPEED, 2.0,
                                Attribute.ABILITY_COOLDOWN, 2.0);
        String prefix = id.name() + "_" + slot.name();
        return EquipmentLadder.of(
                slot,
                List.of(
                        EquipmentTier.of(
                                1, slot, first, TierAppearance.ofMaterial("LEATHER"), 1, Map.of()),
                        EquipmentTier.of(
                                2,
                                slot,
                                second,
                                TierAppearance.ofMaterial(prefix + "_TIER_2"),
                                2,
                                Map.of())));
    }

    private static ZoneConfig emptyZones() {
        return new ZoneConfig(
                false,
                new WorldPosition(UUID.randomUUID(), 0.0, 64.0, 0.0),
                List.of(),
                Duration.ZERO,
                true);
    }
}

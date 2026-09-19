package rpg.content.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import rpg.content.ContentRegistryContract;

class ContentInvariantValidationTest {

    @Test
    void allInScopeRelationsHaveStableNamesAndHappyPathResolves() throws Exception {
        assertThat(B16ContentInvariant.values())
                .extracting(Enum::name)
                .containsExactly(
                        "CLASSES_TO_ABILITIES",
                        "MOB_HORDES_TO_ZONES",
                        "MOB_HORDES_TO_SPAWN_AREAS",
                        "MOB_HORDES_TO_KINDS",
                        "ITEM_LOOT_TO_TEMPLATES",
                        "ITEM_LOOT_TO_ZONES",
                        "ITEM_LOOT_TO_KINDS",
                        "ITEM_VENDORS_TO_ZONES",
                        "ITEM_VENDORS_TO_TEMPLATES",
                        "MOB_KINDS_TO_MESSAGES");

        B16ContentInvariant.CLASSES_TO_ABILITIES.validate(
                Path.of("classes.yml"),
                List.of(
                        new InvariantReference(
                                "classes",
                                "WARRIOR",
                                "abilities",
                                "warrior.rage",
                                "classes.WARRIOR.abilities[0]",
                                "class ability ID must exist")),
                Set.of("warrior.rage"));
    }

    @Test
    void unknownTargetContainsCompleteReferenceContext() {
        InvariantReference reference =
                new InvariantReference(
                        "items",
                        "loot.by-zone.greenfields",
                        "items",
                        "potion.missing",
                        "loot.by-zone.greenfields[0].template",
                        "loot template must exist");

        assertThatThrownBy(
                        () ->
                                B16ContentInvariant.ITEM_LOOT_TO_TEMPLATES.validate(
                                        Path.of("items.yml"), List.of(reference), Set.of()))
                .isInstanceOf(ContentInvariantViolation.class)
                .satisfies(
                        thrown -> {
                            ContentInvariantViolation violation =
                                    (ContentInvariantViolation) thrown;
                            assertThat(violation.invariantName())
                                    .isEqualTo("ITEM_LOOT_TO_TEMPLATES");
                            assertThat(violation.sourceDomain()).isEqualTo("items");
                            assertThat(violation.sourceId()).isEqualTo("loot.by-zone.greenfields");
                            assertThat(violation.targetDomain()).isEqualTo("items");
                            assertThat(violation.targetId()).isEqualTo("potion.missing");
                            assertThat(violation.documentPath())
                                    .isEqualTo("loot.by-zone.greenfields[0].template");
                            assertThat(violation.getMessage())
                                    .contains("ITEM_LOOT_TO_TEMPLATES")
                                    .contains("items/loot.by-zone.greenfields")
                                    .contains("items/potion.missing")
                                    .contains("loot.by-zone.greenfields[0].template");
                        });
    }

    @Test
    void stableIdPolicyAcceptsTypedCurrentShapesAndRejectsDuplicates() throws Exception {
        StableContentIdPolicy.validateAll(
                Path.of("content.yml"),
                List.of(
                        new StableIdCandidate(
                                "classes",
                                "WARRIOR",
                                ContentRegistryContract.KeyType.CLASS_ID,
                                "classes.WARRIOR",
                                "classes.<classId>"),
                        new StableIdCandidate(
                                "abilities",
                                "warrior.rage",
                                ContentRegistryContract.KeyType.ABILITY_ID,
                                "abilities.warrior.rage",
                                "abilities.<abilityId>"),
                        new StableIdCandidate(
                                "progression",
                                "1",
                                ContentRegistryContract.KeyType.LEVEL_ID,
                                "xp-curve.1",
                                "xp-curve.<levelId>"),
                        new StableIdCandidate(
                                "combat",
                                "ZOMBIE",
                                ContentRegistryContract.KeyType.VANILLA_MOB_TYPE,
                                "mobs.by-type.ZOMBIE",
                                "mobs.by-type.<vanillaMobType>")));

        StableIdCandidate duplicate =
                new StableIdCandidate(
                        "classes",
                        "WARRIOR",
                        ContentRegistryContract.KeyType.CLASS_ID,
                        "classes.WARRIOR.copy",
                        "classes.<classId>");
        assertThatThrownBy(
                        () ->
                                StableContentIdPolicy.validateAll(
                                        Path.of("classes.yml"),
                                        List.of(
                                                new StableIdCandidate(
                                                        "classes",
                                                        "WARRIOR",
                                                        ContentRegistryContract.KeyType.CLASS_ID,
                                                        "classes.WARRIOR",
                                                        "classes.<classId>"),
                                                duplicate)))
                .isInstanceOf(ContentInvariantViolation.class)
                .hasMessageContaining("STABLE_ID_UNIQUENESS")
                .hasMessageContaining("classes.WARRIOR.copy");
    }

    @Test
    void invalidAndDisplayLikeIdsContainStableIdContext() {
        StableIdCandidate invalid =
                new StableIdCandidate(
                        "zones",
                        "Green Fields",
                        ContentRegistryContract.KeyType.ZONE_ID,
                        "zones.Green Fields",
                        "zones.<zoneId>");

        assertThatThrownBy(() -> StableContentIdPolicy.validate(Path.of("zones.yml"), invalid))
                .isInstanceOf(ContentInvariantViolation.class)
                .satisfies(
                        thrown -> {
                            ContentInvariantViolation violation =
                                    (ContentInvariantViolation) thrown;
                            assertThat(violation.invariantName()).isEqualTo("STABLE_ID_SYNTAX");
                            assertThat(violation.sourceDomain()).isEqualTo("zones");
                            assertThat(violation.sourceId()).isEqualTo("Green Fields");
                            assertThat(violation.targetDomain()).isEqualTo("zones");
                            assertThat(violation.targetId()).isEqualTo("Green Fields");
                            assertThat(violation.documentPath()).isEqualTo("zones.Green Fields");
                        });
    }

    @Test
    void registryIdentityAllowsSameIdInSeparateRegistries() throws Exception {
        StableContentIdPolicy.validateAll(
                Path.of("items.yml"),
                List.of(
                        new StableIdCandidate("items", "greenfields", ContentRegistryContract.KeyType.ZONE_ID, "loot.by-zone.greenfields", "loot.by-zone.<zoneId>"),
                        new StableIdCandidate("items", "greenfields", ContentRegistryContract.KeyType.ZONE_ID, "vendors.greenfields", "vendors.<zoneId>")));

        assertThatThrownBy(
                        () ->
                                StableContentIdPolicy.validateAll(
                                        Path.of("items.yml"),
                                        List.of(
                                                new StableIdCandidate("items", "greenfields", ContentRegistryContract.KeyType.ZONE_ID, "a", "loot.by-zone.<zoneId>"),
                                                new StableIdCandidate("items", "greenfields", ContentRegistryContract.KeyType.ZONE_ID, "b", "loot.by-zone.<zoneId>"))))
                .isInstanceOf(ContentInvariantViolation.class)
                .hasMessageContaining("STABLE_ID_UNIQUENESS");
    }

    @Test
    void richTargetsCheckDomainKeyTypeScopeAndCapability() throws Exception {
        InvariantReference reference =
                new InvariantReference("mobs", "hordes.greenfields", "zones", "greenfields-east", "hordes.greenfields.areas.greenfields-east", "spawn area must belong to zone", "greenfields", null);
        B16ContentInvariant.MOB_HORDES_TO_SPAWN_AREAS.validateTargets(
                Path.of("mobs.yml"),
                List.of(reference),
                List.of(new InvariantTarget("zones", "greenfields-east", "zones.<zoneId>.spawn-areas[].key", ContentRegistryContract.KeyType.SPAWN_AREA_ID, "greenfields", Set.of())));

        InvariantReference bossReference =
                new InvariantReference("mobs", "hordes.greenfields.boss", "mobs", "greenfields.boss", "hordes.greenfields.boss.kind", "boss kind", null, "boss");
        assertThatThrownBy(
                        () ->
                                B16ContentInvariant.MOB_HORDES_TO_KINDS.validateTargets(
                                        Path.of("mobs.yml"),
                                        List.of(bossReference),
                                        List.of(new InvariantTarget("mobs", "greenfields.boss", "kinds.<mobKindId>", ContentRegistryContract.KeyType.MOB_KIND_ID, null, Set.of()))))
                .isInstanceOf(ContentInvariantViolation.class)
                .hasMessageContaining("boss");
    }

    @Test
    void wrongDomainAndDiagnosticControlCharactersAreStructuredAndEscaped() {
        InvariantReference reference =
                new InvariantReference("currency\n", "source\u0001", "abilities", "missing\n", "classes.path\r\n", "must exist\t");
        assertThatThrownBy(
                        () ->
                                B16ContentInvariant.CLASSES_TO_ABILITIES.validate(
                                        Path.of("classes.yml"), List.of(reference), Set.of()))
                .isInstanceOf(ContentInvariantViolation.class)
                .satisfies(
                        thrown -> {
                            ContentInvariantViolation violation = (ContentInvariantViolation) thrown;
                            assertThat(violation.getMessage()).doesNotContain("currency\n").doesNotContain("classes.path\r\n");
                            assertThat(violation.getMessage()).contains("\\n").contains("\\u0001");
                        });
    }

    @Test
    void vanillaAllowlistIsCallerSuppliedAndIdsOnlyRejectWhitespace() throws Exception {
        StableContentIdPolicy.validateAll(
                Path.of("combat.yml"),
                List.of(new StableIdCandidate("combat", "NOT_A_REAL_MOB", ContentRegistryContract.KeyType.VANILLA_MOB_TYPE, "mobs.by-type.NOT_A_REAL_MOB", "mobs.by-type.<vanillaMobType>")),
                Set.of("NOT_A_REAL_MOB"));
        assertThatThrownBy(
                        () ->
                                StableContentIdPolicy.validate(
                                        Path.of("zones.yml"),
                                        new StableIdCandidate("zones", "zone\nname", ContentRegistryContract.KeyType.ZONE_ID, "zones.zone", "zones.<zoneId>")))
                .isInstanceOf(ContentInvariantViolation.class)
                .hasMessageContaining("\\n");
    }

    @Test
    void messagesAreExplicitlyOutOfScopeAndOpenBalanceValuesHaveNoInvariant() {
        assertThat(B16ContentInvariant.MOB_KINDS_TO_MESSAGES.inScope()).isFalse();
        assertThat(B16ContentInvariant.MOB_KINDS_TO_MESSAGES.description())
                .contains("outside B16");
        assertThatThrownBy(
                        () ->
                                B16ContentInvariant.MOB_KINDS_TO_MESSAGES.validate(
                                        Path.of("mobs.yml"), List.of(), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out-of-scope");
        assertThat(B16ContentInvariant.values())
                .noneMatch(invariant -> invariant.name().matches(".*(COOLDOWN|CASTING|RARITY|AFFIX|BALANCE).*"));
    }
}

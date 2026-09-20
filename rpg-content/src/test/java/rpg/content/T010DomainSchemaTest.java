package rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import rpg.content.ability.AbilityContentSchema;
import rpg.content.classes.ClassContentSchema;
import rpg.content.combat.CombatContentSchema;
import rpg.content.currency.CurrencyContentSchema;
import rpg.content.item.ItemContentSchema;
import rpg.content.mob.MobContentSchema;
import rpg.content.progression.ProgressionContentSchema;
import rpg.content.stats.StatsContentSchema;
import rpg.content.zone.ZoneContentSchema;
import rpg.core.config.ConfigFieldContract;
import rpg.core.config.FieldType;
import rpg.core.zone.WorldResolver;

class T010DomainSchemaTest {

    @Test
    void allNineSchemasUseTheirCanonicalDocumentAndVersion() {
        List<ContentDocumentSchema<?>> schemas =
                List.of(
                        ClassContentSchema.schema(),
                        AbilityContentSchema.schema(),
                        ProgressionContentSchema.schema(),
                        CombatContentSchema.schema(),
                        ZoneContentSchema.schema(WorldResolver.of(Map.of())),
                        MobContentSchema.schema(),
                        ItemContentSchema.schema(),
                        CurrencyContentSchema.schema(),
                        StatsContentSchema.schema());
        List<String> filenames =
                schemas.stream().map(schema -> schema.document().fileName()).toList();

        assertThat(filenames)
                .containsExactly(
                        "classes.yml",
                        "abilities.yml",
                        "progression.yml",
                        "combat.yml",
                        "zones.yml",
                        "mobs.yml",
                        "items.yml",
                        "currency.yml",
                        "stats.yml");
        assertThat(schemas).allSatisfy(schema -> assertThat(schema.schemaVersion()).isEqualTo(1));
        assertThat(schemas)
                .allSatisfy(
                        schema ->
                                assertThat(schema.document())
                                        .isSameAs(ContentKeyPolicy.defaultPolicy().requireDocument(
                                                schema.document().fileName())));
    }

    @Test
    void fixedSectionsAndDynamicRegistriesComeFromTheDocumentPolicy() {
        assertThat(ClassContentSchema.fixedRootSections()).containsExactly("classes");
        assertThat(AbilityContentSchema.fixedRootSections())
                .containsExactly("runtime", "abilities");
        assertThat(ProgressionContentSchema.fixedRootSections())
                .containsExactly("xp-curve", "level-growth", "mob-xp", "party", "progress-event");
        assertThat(CombatContentSchema.fixedRootSections())
                .containsExactly("combat", "environment", "mobs");
        assertThat(ZoneContentSchema.fixedRootSections())
                .containsExactly(
                        "provisional",
                        "fallback-point",
                        "warning-cooldown-seconds",
                        "combat-logout",
                        "zones");
        assertThat(MobContentSchema.fixedRootSections())
                .containsExactly("admin-spawn-limit", "budget", "horde", "kinds", "hordes");
        assertThat(ItemContentSchema.fixedRootSections())
                .containsExactly("inventory", "wear", "repair", "templates", "loot", "vendors");
        assertThat(CurrencyContentSchema.fixedRootSections())
                .containsExactly("account", "drops", "ledger", "history");
        assertThat(StatsContentSchema.fixedRootSections()).containsExactly("attributes");

        assertThat(AbilityContentSchema.dynamicRegistries())
                .extracting(ContentRegistryContract::path)
                .containsExactly("abilities.<abilityId>");
        assertThat(ProgressionContentSchema.dynamicRegistries())
                .extracting(ContentRegistryContract::keyType)
                .containsExactly(
                        ContentRegistryContract.KeyType.LEVEL_ID,
                        ContentRegistryContract.KeyType.VANILLA_MOB_TYPE);
        assertThat(ZoneContentSchema.dynamicRegistries())
                .extracting(ContentRegistryContract::path)
                .containsExactly("zones.<zoneId>", "zones.<zoneId>.spawn-areas[].key");
        assertThat(ItemContentSchema.dynamicRegistries())
                .extracting(ContentRegistryContract::path)
                .containsExactly(
                        "templates.<itemTemplateId>",
                        "loot.by-zone.<zoneId>",
                        "loot.by-kind.<mobKindId>",
                        "loot.by-boss.<mobKindId>",
                        "vendors.<zoneId>");
    }

    @Test
    void fieldMetadataReusesCoreTypesAndKeepsKnownUnits() {
        ContentDocumentSchema<?> ability = AbilityContentSchema.schema();
        ConfigFieldContract cooldown = ability.requireField("runtime.global-cooldown-ms");
        assertThat(cooldown.type()).isEqualTo(FieldType.LONG);
        assertThat(cooldown.required()).isTrue();
        assertThat(cooldown.unit()).contains("milliseconds");

        ContentDocumentSchema<?> progression = ProgressionContentSchema.schema();
        assertThat(progression.requireField("party.range-blocks").unit()).contains("blocks");
        assertThat(progression.requireField("progress-event.window-millis").unit())
                .contains("milliseconds");

        ConfigFieldContract inventory = ItemContentSchema.schema().requireField("inventory");
        assertThat(inventory.required()).isFalse();
        assertThat(inventory.defaultValue()).contains(Map.of());
        assertThat(MobContentSchema.schema().requireField("admin-spawn-limit").defaultValue())
                .contains(20);
    }

    @Test
    void typedRegistriesAreImmutableAndPreserveEntryOrder() {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("first", 1);
        source.put("second", 2);
        TypedContentRegistry<String, Integer> registry =
                new TypedContentRegistry<>(
                        new ContentRegistryContract(
                                "test.<id>", ContentRegistryContract.KeyType.CLASS_ID),
                        source);
        source.put("third", 3);

        assertThat(registry.keys()).containsExactly("first", "second");
        assertThat(registry.require("second")).isEqualTo(2);
        assertThatThrownBy(() -> registry.keys().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.asMap().put("third", 3))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                        () ->
                                new TypedContentRegistry<>(
                                        new ContentRegistryContract(
                                                "test.<id>", ContentRegistryContract.KeyType.CLASS_ID),
                                        Map.of("bad", null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(registry.contract().path()).isEqualTo("test.<id>");
    }

    @Test
    void everyDeclaredRegistryHasAContractBoundTypedFacade() {
        Map<Class<?>, Map<String, String>> expected =
                Map.of(
                        ClassContentSchema.class,
                                Map.of("classes.<classId>", "registry"),
                        AbilityContentSchema.class,
                                Map.of("abilities.<abilityId>", "registry"),
                        ProgressionContentSchema.class,
                                Map.of(
                                        "xp-curve.<levelId>", "xpRegistry",
                                        "mob-xp.by-type.<vanillaMobType>", "mobXpRegistry"),
                        CombatContentSchema.class,
                                Map.of("mobs.by-type.<vanillaMobType>", "registry"),
                        ZoneContentSchema.class,
                                Map.of(
                                        "zones.<zoneId>", "registry",
                                        "zones.<zoneId>.spawn-areas[].key", "spawnAreaRegistry"),
                        MobContentSchema.class,
                                Map.of(
                                        "kinds.<mobKindId>", "kindRegistry",
                                        "hordes.<zoneId>", "hordeRegistry",
                                        "hordes.<zoneId>.areas.<spawnAreaId>", "spawnAreaRegistry"),
                        ItemContentSchema.class,
                                Map.of(
                                        "templates.<itemTemplateId>", "templateRegistry",
                                        "loot.by-zone.<zoneId>", "lootByZoneRegistry",
                                        "loot.by-kind.<mobKindId>", "lootByKindRegistry",
                                        "loot.by-boss.<mobKindId>", "lootByBossRegistry",
                                        "vendors.<zoneId>", "vendorRegistry"),
                        CurrencyContentSchema.class,
                                Map.of("drops.by-type.<vanillaMobType>", "registry"),
                        StatsContentSchema.class,
                                Map.of("attributes.<attributeId>", "registry"));

        for (Map.Entry<Class<?>, Map<String, String>> entry : expected.entrySet()) {
            Class<?> facade = entry.getKey();
            Set<String> declared =
                    switch (facade.getSimpleName()) {
                        case "ClassContentSchema" -> Set.copyOf(ClassContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "AbilityContentSchema" -> Set.copyOf(AbilityContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "ProgressionContentSchema" -> Set.copyOf(ProgressionContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "CombatContentSchema" -> Set.copyOf(CombatContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "ZoneContentSchema" -> Set.copyOf(ZoneContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "MobContentSchema" -> Set.copyOf(MobContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "ItemContentSchema" -> Set.copyOf(ItemContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "CurrencyContentSchema" -> Set.copyOf(CurrencyContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        case "StatsContentSchema" -> Set.copyOf(StatsContentSchema.dynamicRegistries().stream().map(ContentRegistryContract::path).toList());
                        default -> throw new AssertionError(facade);
                    };
            assertThat(declared).containsExactlyInAnyOrderElementsOf(entry.getValue().keySet());
            for (String methodName : entry.getValue().values()) {
                assertThat(
                                java.util.Arrays.stream(facade.getDeclaredMethods())
                                        .filter(method -> method.getName().equals(methodName))
                                        .anyMatch(
                                                method ->
                                                        method.getReturnType()
                                                                .equals(TypedContentRegistry.class)))
                        .as(facade.getSimpleName() + "." + methodName)
                        .isTrue();
            }
        }
    }

    @Test
    void rootEnvelopeRejectsNonMapWrongVersionAndUnknownKeys() {
        ContentDocumentSchema<?> schema = AbilityContentSchema.schema();
        schema.assertRootStructure(Map.of("schemaVersion", 1, "runtime", Map.of()));

        assertThatThrownBy(() -> schema.assertRootStructure(List.of("not-a-map")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schema.assertRootStructure(Map.of("schemaVersion", 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                schema.assertRootStructure(
                                        Map.of("schemaVersion", 1, "unknown", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        Map<Object, Object> nonStringKey = new LinkedHashMap<>();
        nonStringKey.put("schemaVersion", 1);
        nonStringKey.put(42, Map.of());
        assertThatThrownBy(() -> schema.assertRootStructure(nonStringKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void facadeCanWrapExactCoreSchemaInstance() {
        rpg.core.config.ConfigSchema<String> core =
                rpg.core.config.ConfigSchema.<String>builder(1)
                        .required("value", FieldType.STRING)
                        .boundTo(view -> view.getString("value"))
                        .build();
        ContentDocumentSchema<String> facade =
                ContentDocumentSchema.fromCore(
                        new ContentDocumentContract(
                                "test.yml", "test", List.of("schemaVersion"), List.of("value"),
                                List.of("value"), List.of()),
                        core);

        assertThat(facade.configSchema()).isSameAs(core);
    }
}

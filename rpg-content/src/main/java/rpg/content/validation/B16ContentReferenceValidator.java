package rpg.content.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import rpg.content.ContentReference;
import rpg.content.ContentRegistryContract;
import rpg.content.TypedContentRegistry;
import rpg.content.ability.AbilityContentSchema;
import rpg.content.item.ItemContentSchema;
import rpg.content.mob.MobContentSchema;
import rpg.content.zone.ZoneContentSchema;
import rpg.core.ability.AbilityConfig;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassConfig;
import rpg.core.item.ItemConfig;
import rpg.core.item.LootEntry;
import rpg.core.item.LootTable;
import rpg.core.item.LootTables;
import rpg.core.item.VendorStock;
import rpg.core.mob.BossSpec;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.session.CharacterClass;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneConfig;

/**
 * Builds and evaluates the server-free B16 cross-domain references.
 *
 * <p>The actual invariant rules live in {@link B16ContentInvariant}; this class is the adapter
 * from the already-bound core configuration records to those rules. Keeping the adapter here means
 * the platform layer does not gain a second set of reference checks and the content module remains
 * free of Bukkit/Paper.
 *
 * <p>This implementation deliberately covers the nine relations specified by T012. Formula values
 * and vanilla-keyed currency drops are not cross-domain registries in the current contract, so this
 * class does not invent a fake relation for them. They remain subject to their own schema/value
 * validation until a typed cross-domain contract is specified.
 */
public final class B16ContentReferenceValidator {

    private static final String BOSS_CAPABILITY = "boss";

    private B16ContentReferenceValidator() {}

    /** Validates all nine currently executable T012 relations in deterministic rule order. */
    public static List<ContentReference> validateAll(
            Path classesSource,
            ClassConfig classes,
            Path abilitiesSource,
            AbilityConfig abilities,
            Path mobsSource,
            MobConfig mobs,
            Path zonesSource,
            ZoneConfig zones,
            Path itemsSource,
            ItemConfig items)
            throws ContentInvariantViolation {
        List<ContentReference> references = new ArrayList<>();
        references.addAll(validateClassAbilities(classesSource, classes, abilities));
        references.addAll(validateMobHordes(mobsSource, mobs, zones));
        references.addAll(validateItems(itemsSource, items, mobs, zones));
        return List.copyOf(references);
    }

    /** Validates the class-to-ability relation. */
    public static List<ContentReference> validateClassAbilities(
            Path source, ClassConfig classes, AbilityConfig abilities)
            throws ContentInvariantViolation {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(abilities, "abilities");

        List<InvariantReference> references = new ArrayList<>();
        for (Map.Entry<CharacterClass, CharacterClassDefinition> entry :
                classes.definitions().entrySet()) {
            String classId = entry.getKey().name();
            List<AbilityBinding> bindings = entry.getValue().abilities();
            for (int index = 0; index < bindings.size(); index++) {
                AbilityBinding binding = bindings.get(index);
                references.add(
                        new InvariantReference(
                                "classes",
                                classId,
                                "abilities",
                                binding.abilityId(),
                                "classes." + classId + ".abilities[" + (index + 1) + "].id",
                                B16ContentInvariant.CLASSES_TO_ABILITIES.description()));
            }
        }

        TypedContentRegistry<String, rpg.core.ability.Ability> abilityRegistry =
                AbilityContentSchema.registry(abilities);
        List<InvariantTarget> targets =
                targets(
                        "abilities",
                abilityRegistry,
                ContentRegistryContract.KeyType.ABILITY_ID);
        B16ContentInvariant.CLASSES_TO_ABILITIES.validateTargets(source, references, targets);
        return toContentReferences(references);
    }

    /** Validates horde zone, spawn-area, mob-kind and boss-kind references. */
    public static List<ContentReference> validateMobHordes(
            Path source, MobConfig mobs, ZoneConfig zones) throws ContentInvariantViolation {
        Objects.requireNonNull(mobs, "mobs");
        Objects.requireNonNull(zones, "zones");

        List<InvariantReference> zoneReferences = new ArrayList<>();
        List<InvariantReference> areaReferences = new ArrayList<>();
        List<InvariantReference> kindReferences = new ArrayList<>();

        for (Map.Entry<String, HordeSpec> entry : mobs.hordes().entrySet()) {
            String hordeId = entry.getKey();
            HordeSpec horde = entry.getValue();
            String hordePath = "hordes." + hordeId;
            String zoneId = horde.zoneKey();
            zoneReferences.add(
                    reference(
                            "mobs",
                            hordePath,
                            "zones",
                            zoneId,
                            hordePath,
                            B16ContentInvariant.MOB_HORDES_TO_ZONES));

            Map<String, Integer> areaEntryNumbers = new HashMap<>();
            for (int index = 0; index < horde.entries().size(); index++) {
                HordeSpec.Entry hordeEntry = horde.entries().get(index);
                String areaPath = hordePath + ".areas." + hordeEntry.areaKey();
                int areaEntryNumber =
                        areaEntryNumbers.merge(hordeEntry.areaKey(), 1, Integer::sum);
                areaReferences.add(
                        new InvariantReference(
                                "mobs",
                                hordePath,
                                "zones",
                                hordeEntry.areaKey(),
                                areaPath,
                                B16ContentInvariant.MOB_HORDES_TO_SPAWN_AREAS.description(),
                                zoneId,
                                null));
                kindReferences.add(
                        new InvariantReference(
                                "mobs",
                                hordePath,
                                "mobs",
                                hordeEntry.kindKey(),
                                areaPath + "[" + areaEntryNumber + "].kind",
                                B16ContentInvariant.MOB_HORDES_TO_KINDS.description()));
            }

            BossSpec boss = horde.boss();
            if (boss != null) {
                String bossPath = hordePath + ".boss";
                areaReferences.add(
                        new InvariantReference(
                                "mobs",
                                bossPath,
                                "zones",
                                boss.areaKey(),
                                bossPath + ".area",
                                B16ContentInvariant.MOB_HORDES_TO_SPAWN_AREAS.description(),
                                zoneId,
                                null));
                kindReferences.add(
                        new InvariantReference(
                                "mobs",
                                bossPath,
                                "mobs",
                                boss.kindKey(),
                                bossPath + ".kind",
                                B16ContentInvariant.MOB_HORDES_TO_KINDS.description(),
                                null,
                                BOSS_CAPABILITY));
            }
        }

        TypedContentRegistry<String, Zone> zoneRegistry = ZoneContentSchema.registry(zones);
        TypedContentRegistry<String, TypedContentRegistry<String, SpawnArea>> areaRegistry =
                ZoneContentSchema.spawnAreaRegistry(zones);
        List<InvariantTarget> zoneTargets =
                targets("zones", zoneRegistry, ContentRegistryContract.KeyType.ZONE_ID);
        List<InvariantTarget> areaTargets = new ArrayList<>();
        for (Map.Entry<String, TypedContentRegistry<String, SpawnArea>> zoneEntry :
                areaRegistry.asMap().entrySet()) {
            areaTargets.addAll(
                    targets(
                            "zones",
                            zoneEntry.getValue(),
                            ContentRegistryContract.KeyType.SPAWN_AREA_ID,
                            zoneEntry.getKey()));
        }

        TypedContentRegistry<String, MobKind> kindRegistry = MobContentSchema.kindRegistry(mobs);
        List<InvariantTarget> kindTargets = new ArrayList<>();
        for (Map.Entry<String, MobKind> entry : kindRegistry.asMap().entrySet()) {
            Set<String> capabilities = entry.getValue().boss() ? Set.of(BOSS_CAPABILITY) : Set.of();
            kindTargets.add(
                    new InvariantTarget(
                            "mobs",
                            entry.getKey(),
                            kindRegistry.contract().path(),
                            ContentRegistryContract.KeyType.MOB_KIND_ID,
                            null,
                            capabilities));
        }

        B16ContentInvariant.MOB_HORDES_TO_ZONES.validateTargets(
                source, zoneReferences, zoneTargets);
        B16ContentInvariant.MOB_HORDES_TO_SPAWN_AREAS.validateTargets(
                source, areaReferences, areaTargets);
        B16ContentInvariant.MOB_HORDES_TO_KINDS.validateTargets(
                source, kindReferences, kindTargets);
        List<ContentReference> references = new ArrayList<>();
        references.addAll(toContentReferences(zoneReferences));
        references.addAll(toContentReferences(areaReferences));
        references.addAll(toContentReferences(kindReferences));
        return List.copyOf(references);
    }

    /** Validates item loot registries, loot entries, vendor zones and vendor templates. */
    public static List<ContentReference> validateItems(
            Path source, ItemConfig items, MobConfig mobs, ZoneConfig zones)
            throws ContentInvariantViolation {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(mobs, "mobs");
        Objects.requireNonNull(zones, "zones");

        List<InvariantReference> templateReferences = new ArrayList<>();
        List<InvariantReference> zoneReferences = new ArrayList<>();
        List<InvariantReference> kindReferences = new ArrayList<>();
        List<InvariantReference> vendorZoneReferences = new ArrayList<>();
        List<InvariantReference> vendorTemplateReferences = new ArrayList<>();

        LootTables loot = items.loot();
        addLootReferences(
                templateReferences,
                zoneReferences,
                kindReferences,
                loot.byZone(),
                "loot.by-zone",
                B16ContentInvariant.ITEM_LOOT_TO_ZONES,
                B16ContentInvariant.ITEM_LOOT_TO_KINDS);
        addLootReferences(
                templateReferences,
                zoneReferences,
                kindReferences,
                loot.byKind(),
                "loot.by-kind",
                null,
                B16ContentInvariant.ITEM_LOOT_TO_KINDS);
        addLootReferences(
                templateReferences,
                zoneReferences,
                kindReferences,
                loot.byBoss(),
                "loot.by-boss",
                null,
                B16ContentInvariant.ITEM_LOOT_TO_KINDS);

        for (Map.Entry<String, VendorStock> entry : items.vendors().entrySet()) {
            String vendorPath = "vendors." + entry.getKey();
            vendorZoneReferences.add(
                    reference(
                            "items",
                            vendorPath,
                            "zones",
                            entry.getKey(),
                            vendorPath,
                            B16ContentInvariant.ITEM_VENDORS_TO_ZONES));
            int index = 0;
            for (String templateId : entry.getValue().templateKeys()) {
                vendorTemplateReferences.add(
                        new InvariantReference(
                                "items",
                                vendorPath,
                                "items",
                                templateId,
                                vendorPath + ".stock[" + (++index) + "].template",
                                B16ContentInvariant.ITEM_VENDORS_TO_TEMPLATES.description()));
            }
        }

        TypedContentRegistry<String, rpg.core.item.ItemTemplate> templateRegistry =
                ItemContentSchema.templateRegistry(items);
        List<InvariantTarget> templateTargets =
                targets(
                        "items",
                        templateRegistry,
                        ContentRegistryContract.KeyType.ITEM_TEMPLATE_ID);
        TypedContentRegistry<String, Zone> zoneRegistry = ZoneContentSchema.registry(zones);
        List<InvariantTarget> zoneTargets =
                targets("zones", zoneRegistry, ContentRegistryContract.KeyType.ZONE_ID);
        TypedContentRegistry<String, MobKind> kindRegistry = MobContentSchema.kindRegistry(mobs);
        List<InvariantTarget> kindTargets = new ArrayList<>();
        for (Map.Entry<String, MobKind> entry : kindRegistry.asMap().entrySet()) {
            Set<String> capabilities = entry.getValue().boss() ? Set.of(BOSS_CAPABILITY) : Set.of();
            kindTargets.add(
                    new InvariantTarget(
                            "mobs",
                            entry.getKey(),
                            kindRegistry.contract().path(),
                            ContentRegistryContract.KeyType.MOB_KIND_ID,
                            null,
                            capabilities));
        }

        B16ContentInvariant.ITEM_LOOT_TO_TEMPLATES.validateTargets(
                source, templateReferences, templateTargets);
        B16ContentInvariant.ITEM_LOOT_TO_ZONES.validateTargets(
                source, zoneReferences, zoneTargets);
        B16ContentInvariant.ITEM_LOOT_TO_KINDS.validateTargets(
                source, kindReferences, kindTargets);
        B16ContentInvariant.ITEM_VENDORS_TO_ZONES.validateTargets(
                source, vendorZoneReferences, zoneTargets);
        B16ContentInvariant.ITEM_VENDORS_TO_TEMPLATES.validateTargets(
                source, vendorTemplateReferences, templateTargets);
        List<ContentReference> references = new ArrayList<>();
        references.addAll(toContentReferences(templateReferences));
        references.addAll(toContentReferences(zoneReferences));
        references.addAll(toContentReferences(kindReferences));
        references.addAll(toContentReferences(vendorZoneReferences));
        references.addAll(toContentReferences(vendorTemplateReferences));
        return List.copyOf(references);
    }

    private static void addLootReferences(
            List<InvariantReference> templateReferences,
            List<InvariantReference> zoneReferences,
            List<InvariantReference> kindReferences,
            Map<String, LootTable> tables,
            String registryName,
            B16ContentInvariant registryInvariant,
            B16ContentInvariant kindInvariant) {
        for (Map.Entry<String, LootTable> tableEntry : tables.entrySet()) {
            String tablePath = registryName + "." + tableEntry.getKey();
            if (registryInvariant != null) {
                zoneReferences.add(
                        reference(
                                "items",
                                tablePath,
                                "zones",
                                tableEntry.getKey(),
                                tablePath,
                                registryInvariant));
            }
            if (kindInvariant != null && !registryName.equals("loot.by-zone")) {
                kindReferences.add(
                        new InvariantReference(
                                "items",
                                tablePath,
                                "mobs",
                                tableEntry.getKey(),
                                tablePath,
                                kindInvariant.description(),
                                null,
                                registryName.equals("loot.by-boss") ? BOSS_CAPABILITY : null));
            }
            List<LootEntry> entries = tableEntry.getValue().entries();
            for (int index = 0; index < entries.size(); index++) {
                LootEntry entry = entries.get(index);
                templateReferences.add(
                        new InvariantReference(
                                "items",
                                tablePath,
                                "items",
                                entry.templateKey(),
                                tablePath + "[" + (index + 1) + "].template",
                                B16ContentInvariant.ITEM_LOOT_TO_TEMPLATES.description()));
            }
        }
    }

    private static InvariantReference reference(
            String sourceDomain,
            String sourceId,
            String targetDomain,
            String targetId,
            String yamlPath,
            B16ContentInvariant invariant) {
        return new InvariantReference(
                sourceDomain, sourceId, targetDomain, targetId, yamlPath, invariant.description());
    }

    private static <K, V> List<InvariantTarget> targets(
            String domain,
            TypedContentRegistry<K, V> registry,
            ContentRegistryContract.KeyType keyType) {
        return targets(domain, registry, keyType, null);
    }

    private static <K, V> List<InvariantTarget> targets(
            String domain,
            TypedContentRegistry<K, V> registry,
            ContentRegistryContract.KeyType keyType,
            String scope) {
        List<InvariantTarget> targets = new ArrayList<>();
        for (K key : registry.keys()) {
            targets.add(
                    new InvariantTarget(
                            domain,
                            String.valueOf(key),
                            registry.contract().path(),
                            keyType,
                            scope,
                            Set.of()));
        }
        return targets;
    }

    private static List<ContentReference> toContentReferences(
            List<InvariantReference> references) {
        return references.stream()
                .map(
                        reference ->
                                new ContentReference(
                                        reference.sourceDomain(),
                                        reference.sourceId(),
                                        reference.targetDomain(),
                                        reference.targetId(),
                                        reference.yamlPath(),
                                        reference.requirement()))
                .toList();
    }
}

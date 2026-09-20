package rpg.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable index of the nine current B16 document key contracts.
 *
 * <p>{@link #defaultPolicy()} describes the current versioned content resources. It is a
 * server-free, parser-free declaration: no resource is loaded and no runtime validation is wired
 * by this type.
 */
public final class ContentKeyPolicy {

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final ContentKeyPolicy DEFAULT = new ContentKeyPolicy(defaultDocuments());

    private final List<ContentDocumentContract> documents;
    private final Map<String, ContentDocumentContract> byFileName;

    /** Creates an immutable policy and rejects duplicate document filenames. */
    public ContentKeyPolicy(List<ContentDocumentContract> documents) {
        Objects.requireNonNull(documents, "documents");
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }

        List<ContentDocumentContract> documentCopy = new ArrayList<>(documents.size());
        Map<String, ContentDocumentContract> index = new LinkedHashMap<>();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ContentDocumentContract document : documents) {
            Objects.requireNonNull(document, "documents entry");
            if (!names.add(document.fileName())) {
                throw new IllegalArgumentException(
                        "documents contains duplicate fileName: " + document.fileName());
            }
            if (!document.reservedRootKeys().contains(SCHEMA_VERSION)) {
                throw new IllegalArgumentException(
                        document.fileName() + " does not reserve " + SCHEMA_VERSION);
            }
            documentCopy.add(document);
            index.put(document.fileName(), document);
        }
        this.documents = List.copyOf(documentCopy);
        this.byFileName = Map.copyOf(index);
    }

    /** Returns the canonical policy for the nine B16 content filenames. */
    public static ContentKeyPolicy defaultPolicy() {
        return DEFAULT;
    }

    /** Returns all document contracts in the documented resource order. */
    public List<ContentDocumentContract> documents() {
        return documents;
    }

    /** Looks up a document by its exact bundled filename. */
    public Optional<ContentDocumentContract> document(String fileName) {
        return Optional.ofNullable(byFileName.get(fileName));
    }

    /** Looks up a document or fails with a descriptive filename error. */
    public ContentDocumentContract requireDocument(String fileName) {
        return document(fileName)
                .orElseThrow(() -> new IllegalArgumentException("unknown content filename: " + fileName));
    }

    private static List<ContentDocumentContract> defaultDocuments() {
        return List.of(
                document(
                        "classes.yml",
                        "classes",
                        List.of("classes"),
                        List.of(
                                "classes.<classId>.base-stats",
                                "classes.<classId>.growth",
                                "classes.<classId>.armor-ladder",
                                "classes.<classId>.weapon-ladder",
                                "classes.<classId>.abilities"),
                        new ContentRegistryContract("classes.<classId>", ContentRegistryContract.KeyType.CLASS_ID)),
                document(
                        "abilities.yml",
                        "abilities",
                        List.of("runtime", "abilities"),
                        List.of(
                                "runtime.regeneration",
                                "abilities.<abilityId>.rank-cost",
                                "abilities.<abilityId>.target",
                                "abilities.<abilityId>.effects"),
                        new ContentRegistryContract(
                                "abilities.<abilityId>", ContentRegistryContract.KeyType.ABILITY_ID)),
                document(
                        "progression.yml",
                        "progression",
                        List.of("xp-curve", "level-growth", "mob-xp", "party", "progress-event"),
                        List.of("mob-xp.by-type"),
                        new ContentRegistryContract(
                                "xp-curve.<levelId>", ContentRegistryContract.KeyType.LEVEL_ID),
                        new ContentRegistryContract(
                                "mob-xp.by-type.<vanillaMobType>",
                                ContentRegistryContract.KeyType.VANILLA_MOB_TYPE)),
                document(
                        "combat.yml",
                        "combat",
                        List.of("combat", "environment", "mobs"),
                        List.of("combat.attribution", "combat.feedback", "environment.fall", "mobs.default", "mobs.by-type"),
                        new ContentRegistryContract(
                                "mobs.by-type.<vanillaMobType>",
                                ContentRegistryContract.KeyType.VANILLA_MOB_TYPE)),
                document(
                        "zones.yml",
                        "zones",
                        List.of("provisional", "fallback-point", "warning-cooldown-seconds", "combat-logout", "zones"),
                        List.of(
                                "fallback-point",
                                "zones.<zoneId>.level-band",
                                "zones.<zoneId>.area",
                                "zones.<zoneId>.safe-core",
                                "zones.<zoneId>.crystal",
                                "zones.<zoneId>.spawn-areas"),
                        new ContentRegistryContract("zones.<zoneId>", ContentRegistryContract.KeyType.ZONE_ID),
                        new ContentRegistryContract(
                                "zones.<zoneId>.spawn-areas[].key",
                                ContentRegistryContract.KeyType.SPAWN_AREA_ID)),
                document(
                        "mobs.yml",
                        "mobs",
                        List.of("admin-spawn-limit", "budget", "horde", "kinds", "hordes"),
                        List.of("budget", "horde", "kinds.<mobKindId>.attributes", "hordes.<zoneId>.areas"),
                        new ContentRegistryContract("kinds.<mobKindId>", ContentRegistryContract.KeyType.MOB_KIND_ID),
                        new ContentRegistryContract("hordes.<zoneId>", ContentRegistryContract.KeyType.ZONE_ID),
                        new ContentRegistryContract(
                                "hordes.<zoneId>.areas.<spawnAreaId>",
                                ContentRegistryContract.KeyType.SPAWN_AREA_ID)),
                document(
                        "items.yml",
                        "items",
                        List.of("inventory", "wear", "repair", "templates", "loot", "vendors"),
                        List.of(
                                "inventory",
                                "wear",
                                "repair",
                                "loot.by-zone",
                                "loot.by-kind",
                                "loot.by-boss"),
                        new ContentRegistryContract(
                                "templates.<itemTemplateId>", ContentRegistryContract.KeyType.ITEM_TEMPLATE_ID),
                        new ContentRegistryContract(
                                "loot.by-zone.<zoneId>", ContentRegistryContract.KeyType.ZONE_ID),
                        new ContentRegistryContract(
                                "loot.by-kind.<mobKindId>", ContentRegistryContract.KeyType.MOB_KIND_ID),
                        new ContentRegistryContract(
                                "loot.by-boss.<mobKindId>", ContentRegistryContract.KeyType.MOB_KIND_ID),
                        new ContentRegistryContract("vendors.<zoneId>", ContentRegistryContract.KeyType.ZONE_ID)),
                document(
                        "currency.yml",
                        "currency",
                        List.of("account", "drops", "ledger", "history"),
                        List.of("account", "drops", "ledger", "history", "drops.by-type"),
                        new ContentRegistryContract(
                                "drops.by-type.<vanillaMobType>",
                                ContentRegistryContract.KeyType.VANILLA_MOB_TYPE)),
                document(
                        "stats.yml",
                        "stats.attributes",
                        List.of("attributes"),
                        List.of("attributes.<attributeId>"),
                        new ContentRegistryContract(
                                "attributes.<attributeId>", ContentRegistryContract.KeyType.ATTRIBUTE_ID)));
    }

    private static ContentDocumentContract document(
            String fileName,
            String type,
            List<String> fixedRootKeys,
            List<String> fixedPaths,
            ContentRegistryContract... registries) {
        List<ContentRegistryContract> registryList = List.of(registries);
        return new ContentDocumentContract(
                fileName, type, List.of(SCHEMA_VERSION), fixedRootKeys, fixedPaths, registryList);
    }
}

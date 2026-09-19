package rpg.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import rpg.content.ability.AbilityContentSchema;
import rpg.content.classes.ClassContentSchema;
import rpg.content.combat.CombatContentSchema;
import rpg.content.currency.CurrencyContentSchema;
import rpg.content.item.ItemContentSchema;
import rpg.content.mob.MobContentSchema;
import rpg.content.progression.ProgressionContentSchema;
import rpg.content.stats.StatsContentSchema;
import rpg.content.validation.B16ContentReferenceValidator;
import rpg.content.zone.ZoneContentSchema;
import rpg.core.ability.AbilityConfig;
import rpg.core.classes.ClassConfig;
import rpg.core.combat.CombatConfig;
import rpg.core.config.ConfigValidationException;
import rpg.core.currency.CurrencyConfig;
import rpg.core.item.ItemConfig;
import rpg.core.mob.MobConfig;
import rpg.core.progression.ProgressionConfig;
import rpg.core.stats.StatConfig;
import rpg.core.zone.WorldResolver;
import rpg.core.zone.ZoneConfig;

/**
 * Server-free coordinator for one complete B16 content load.
 *
 * <p>The reader is deliberately injected. {@code rpg-content} owns the document schemas and the
 * cross-domain rules, while {@code rpg-platform} owns the YAML parser. A
 * {@code YamlConfigLoader::readDocument} method reference is therefore a valid reader without
 * introducing a dependency from content back to platform.
 *
 * <p>The phases are intentionally visible in the implementation: all sources are read first, all
 * parsed roots are then schema-validated and bound, and only then are cross-domain invariants run.
 * The bundle-returning method does not construct a snapshot; {@link #loadSnapshot(Sources)} captures
 * the same fully checked generation, while publication remains outside this coordinator.
 */
public final class B16ContentLoader {

    /** Parser boundary supplied by the platform layer. */
    @FunctionalInterface
    public interface DocumentReader {

        /** Reads and parses one source into its document root. */
        Map<String, Object> read(Path source) throws ConfigValidationException;
    }

    /** The nine B16 source paths, in the canonical read and validation order. */
    public record Sources(
            Path classes,
            Path abilities,
            Path progression,
            Path combat,
            Path zones,
            Path mobs,
            Path items,
            Path currency,
            Path stats) {

        public Sources {
            Objects.requireNonNull(classes, "classes");
            Objects.requireNonNull(abilities, "abilities");
            Objects.requireNonNull(progression, "progression");
            Objects.requireNonNull(combat, "combat");
            Objects.requireNonNull(zones, "zones");
            Objects.requireNonNull(mobs, "mobs");
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(stats, "stats");

            List<Path> paths =
                    List.of(classes, abilities, progression, combat, zones, mobs, items, currency, stats);
            Set<Path> distinct = new HashSet<>(paths);
            if (distinct.size() != paths.size()) {
                throw new IllegalArgumentException("B16 sources must use distinct file paths");
            }
        }

        /** Standard relative runtime names used in the plugin data folder. */
        public static Sources standard() {
            return new Sources(
                    Path.of("classes.yml"),
                    Path.of("abilities.yml"),
                    Path.of("progression.yml"),
                    Path.of("combat.yml"),
                    Path.of("zones.yml"),
                    Path.of("mobs.yml"),
                    Path.of("items.yml"),
                    Path.of("currency.yml"),
                    Path.of("stats.yml"));
        }

        /** Paths in the order in which they are read and subsequently bound. */
        public List<Path> orderedPaths() {
            return List.of(
                    classes,
                    abilities,
                    progression,
                    combat,
                    zones,
                    mobs,
                    items,
                    currency,
                    stats);
        }
    }

    private record Source(String fileName, Path path) {}

    private record LoadedGeneration(B16ContentBundle bundle, List<ContentReference> references) {}

    private final DocumentReader reader;
    private final WorldResolver worlds;

    public B16ContentLoader(DocumentReader reader, WorldResolver worlds) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /**
     * Reads, binds and cross-validates one complete B16 generation.
     *
     * @throws ConfigValidationException if parsing, schema validation or a cross-domain invariant
     *     fails; no partially bound bundle is returned
     */
    public B16ContentBundle load(Sources sources) throws ConfigValidationException {
        return loadGeneration(sources).bundle();
    }

    /**
     * Reads, binds and cross-validates one complete B16 generation, then captures it as one
     * immutable snapshot.
     *
     * <p>This is the batch-loader seam used by the runtime. The snapshot is returned only after all
     * nine parser, document-schema and cross-domain checks have passed; publication into a live
     * {@code ConfigHandle} remains the responsibility of the config-loader transaction.
     */
    public ContentSnapshot loadSnapshot(Sources sources) throws ConfigValidationException {
        LoadedGeneration generation = loadGeneration(sources);
        return snapshot(generation.bundle(), generation.references());
    }

    private LoadedGeneration loadGeneration(Sources sources) throws ConfigValidationException {
        Objects.requireNonNull(sources, "sources");

        // Phase 1: read and parse every source before asking any domain binder to validate it.
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (Source source : orderedSources(sources)) {
            Map<String, Object> root = reader.read(source.path());
            if (root == null) {
                throw new ConfigValidationException(
                        source.path(),
                        "<document>",
                        "a parsed mapping at the document root",
                        "null");
            }
            parsed.put(source.fileName(), root);
        }

        // Phase 2: enforce each document's version/structure and bind its existing core model.
        ClassConfig classes =
                ClassContentSchema.validateAndBind(sources.classes(), parsed.get("classes.yml"));
        AbilityConfig abilities =
                AbilityContentSchema.validateAndBind(
                        sources.abilities(), parsed.get("abilities.yml"));
        ProgressionConfig progression =
                ProgressionContentSchema.validateAndBind(
                        sources.progression(), parsed.get("progression.yml"));
        CombatConfig combat =
                CombatContentSchema.validateAndBind(sources.combat(), parsed.get("combat.yml"));
        ZoneConfig zones =
                ZoneContentSchema.validateAndBind(
                        sources.zones(), parsed.get("zones.yml"), worlds);
        MobConfig mobs = MobContentSchema.validateAndBind(sources.mobs(), parsed.get("mobs.yml"));
        ItemConfig items = ItemContentSchema.validateAndBind(sources.items(), parsed.get("items.yml"));
        CurrencyConfig currency =
                CurrencyContentSchema.validateAndBind(
                        sources.currency(), parsed.get("currency.yml"));
        StatConfig stats =
                StatsContentSchema.validateAndBind(sources.stats(), parsed.get("stats.yml"));

        // Phase 3: only the complete, bound generation is allowed into cross-domain validation.
        List<ContentReference> references =
                B16ContentReferenceValidator.validateAll(
                sources.classes(),
                classes,
                sources.abilities(),
                abilities,
                sources.mobs(),
                mobs,
                sources.zones(),
                zones,
                sources.items(),
                items);

        return new LoadedGeneration(
                new B16ContentBundle(
                        classes, abilities, progression, combat, zones, mobs, items, currency, stats),
                references);
    }

    private ContentSnapshot snapshot(
            B16ContentBundle bundle, List<ContentReference> references) {
        ContentKeyPolicy policy = ContentKeyPolicy.defaultPolicy();
        List<ContentDocument<?>> documents = new ArrayList<>();

        documents.add(
                document(
                        policy.requireDocument("classes.yml"),
                        ClassContentSchema.schema().schemaVersion(),
                        bundle.classes(),
                        new ContentRegistry(
                                "classes", List.of(ClassContentSchema.registry(bundle.classes()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("abilities.yml"),
                        AbilityContentSchema.schema().schemaVersion(),
                        bundle.abilities(),
                        new ContentRegistry(
                                "abilities", List.of(AbilityContentSchema.registry(bundle.abilities()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("progression.yml"),
                        ProgressionContentSchema.schema().schemaVersion(),
                        bundle.progression(),
                        new ContentRegistry(
                                "progression",
                                List.of(
                                        ProgressionContentSchema.xpRegistry(bundle.progression()),
                                        ProgressionContentSchema.mobXpRegistry(bundle.progression()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("combat.yml"),
                        CombatContentSchema.schema().schemaVersion(),
                        bundle.combat(),
                        new ContentRegistry(
                                "combat", List.of(CombatContentSchema.registry(bundle.combat()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("zones.yml"),
                        ZoneContentSchema.schema(worlds).schemaVersion(),
                        bundle.zones(),
                        new ContentRegistry(
                                // The nested spawn-area facade is a validation view whose contract
                                // pattern shares the zone registry's outer path. The bound Zone value
                                // still owns every spawn area, so the snapshot keeps one identity per
                                // declared registry path.
                                "zones",
                                List.of(ZoneContentSchema.registry(bundle.zones()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("mobs.yml"),
                        MobContentSchema.schema().schemaVersion(),
                        bundle.mobs(),
                        new ContentRegistry(
                                // Horde entries retain their area lists in the bound MobConfig; the
                                // nested validation facade has the same outer contract path as the
                                // horde registry and cannot be a second snapshot registry identity.
                                "mobs",
                                List.of(
                                        MobContentSchema.kindRegistry(bundle.mobs()),
                                        MobContentSchema.hordeRegistry(bundle.mobs()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("items.yml"),
                        ItemContentSchema.schema().schemaVersion(),
                        bundle.items(),
                        new ContentRegistry(
                                "items",
                                List.of(
                                        ItemContentSchema.templateRegistry(bundle.items()),
                                        ItemContentSchema.lootByZoneRegistry(bundle.items()),
                                        ItemContentSchema.lootByKindRegistry(bundle.items()),
                                        ItemContentSchema.lootByBossRegistry(bundle.items()),
                                        ItemContentSchema.vendorRegistry(bundle.items()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("currency.yml"),
                        CurrencyContentSchema.schema().schemaVersion(),
                        bundle.currency(),
                        new ContentRegistry(
                                "currency", List.of(CurrencyContentSchema.registry(bundle.currency()))),
                        references));
        documents.add(
                document(
                        policy.requireDocument("stats.yml"),
                        StatsContentSchema.schema().schemaVersion(),
                        bundle.stats(),
                        new ContentRegistry(
                                "stats.attributes", List.of(StatsContentSchema.registry(bundle.stats()))),
                        references));
        return new ContentSnapshot(documents);
    }

    private static <T> ContentDocument<T> document(
            ContentDocumentContract contract,
            int schemaVersion,
            T content,
            ContentRegistry registry,
            List<ContentReference> references) {
        List<ContentReference> ownedReferences =
                references.stream()
                        .filter(reference -> reference.sourceDomain().equals(contract.documentType()))
                        .toList();
        return new ContentDocument<>(
                contract.fileName(),
                contract.documentType(),
                schemaVersion,
                content,
                registry,
                ownedReferences);
    }

    private static List<Source> orderedSources(Sources sources) {
        return List.of(
                new Source("classes.yml", sources.classes()),
                new Source("abilities.yml", sources.abilities()),
                new Source("progression.yml", sources.progression()),
                new Source("combat.yml", sources.combat()),
                new Source("zones.yml", sources.zones()),
                new Source("mobs.yml", sources.mobs()),
                new Source("items.yml", sources.items()),
                new Source("currency.yml", sources.currency()),
                new Source("stats.yml", sources.stats()));
    }
}

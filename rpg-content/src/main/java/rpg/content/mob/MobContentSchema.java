package rpg.content.mob;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobConfigSchema;
import rpg.core.mob.MobKind;

/** Server-free B16 schema facade for the canonical {@code mobs.yml} document. */
public final class MobContentSchema {

    private static final String FILE = "mobs.yml";

    private MobContentSchema() {}

    public static rpg.content.ContentDocumentSchema<MobConfig> schema() {
        var core = MobConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static MobConfig validateAndBind(Path source, Object root)
            throws ConfigValidationException {
        return schema().validateAndBind(source, root);
    }

    public static ContentDocumentContract document() {
        return ContentSchemaSupport.document(FILE);
    }

    public static List<String> fixedRootSections() {
        return document().fixedRootKeys();
    }

    public static List<String> fixedPaths() {
        return document().fixedPaths();
    }

    public static List<ContentRegistryContract> dynamicRegistries() {
        return document().dynamicRegistries();
    }

    public static TypedContentRegistry<String, MobKind> kindRegistry(MobConfig config) {
        return ContentSchemaSupport.registry(FILE, "kinds.<mobKindId>", config.kinds());
    }

    public static TypedContentRegistry<String, HordeSpec> hordeRegistry(MobConfig config) {
        return ContentSchemaSupport.registry(FILE, "hordes.<zoneId>", config.hordes());
    }

    /** Returns the nested zone-to-area entry registries declared by {@code mobs.yml}. */
    public static TypedContentRegistry<String, TypedContentRegistry<String, List<HordeSpec.Entry>>>
            spawnAreaRegistry(MobConfig config) {
        LinkedHashMap<String, TypedContentRegistry<String, List<HordeSpec.Entry>>> nested =
                new LinkedHashMap<>();
        config.hordes()
                .forEach(
                        (zoneKey, horde) -> {
                            LinkedHashMap<String, List<HordeSpec.Entry>> byArea =
                                    new LinkedHashMap<>();
                            horde.entries()
                                    .forEach(
                                            entry ->
                                                    byArea.computeIfAbsent(
                                                                    entry.areaKey(),
                                                                    ignored -> new java.util.ArrayList<>())
                                                            .add(entry));
                            byArea.replaceAll((key, entries) -> List.copyOf(entries));
                            nested.put(
                                    zoneKey,
                                    ContentSchemaSupport.registry(
                                            FILE,
                                            "hordes.<zoneId>.areas.<spawnAreaId>",
                                            byArea));
                        });
        return ContentSchemaSupport.registry(FILE, "hordes.<zoneId>", nested);
    }
}

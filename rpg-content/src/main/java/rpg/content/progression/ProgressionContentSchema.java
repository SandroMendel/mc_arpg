package rpg.content.progression;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.progression.ProgressionConfig;
import rpg.core.progression.ProgressionConfigSchema;

/** Server-free B16 schema facade for the canonical {@code progression.yml} document. */
public final class ProgressionContentSchema {

    private static final String FILE = "progression.yml";

    private ProgressionContentSchema() {}

    public static rpg.content.ContentDocumentSchema<ProgressionConfig> schema() {
        var core = ProgressionConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static ProgressionConfig validateAndBind(Path source, Object root)
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

    /** Returns the numeric level registry declared at {@code xp-curve.<levelId>}. */
    public static TypedContentRegistry<Integer, Long> xpRegistry(ProgressionConfig config) {
        LinkedHashMap<Integer, Long> levels = new LinkedHashMap<>();
        for (int level = 2; level <= config.curve().maxLevel(); level++) {
            levels.put(level, config.curve().thresholdFor(level));
        }
        return ContentSchemaSupport.registry(FILE, "xp-curve.<levelId>", levels);
    }

    /** Returns the Vanilla-mob-type registry declared at {@code mob-xp.by-type.<vanillaMobType>}. */
    public static TypedContentRegistry<String, Long> mobXpRegistry(ProgressionConfig config) {
        return ContentSchemaSupport.registry(
                FILE, "mob-xp.by-type.<vanillaMobType>", config.mobXpByType());
    }
}

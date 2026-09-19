package rpg.content.combat;

import java.nio.file.Path;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.combat.CombatConfig;
import rpg.core.combat.CombatConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;

/** Server-free B16 schema facade for the canonical {@code combat.yml} document. */
public final class CombatContentSchema {

    private static final String FILE = "combat.yml";

    private CombatContentSchema() {}

    public static rpg.content.ContentDocumentSchema<CombatConfig> schema() {
        var core = CombatConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static CombatConfig validateAndBind(Path source, Object root)
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

    public static TypedContentRegistry<String, CombatConfig.MobStats> registry(
            CombatConfig config) {
        return ContentSchemaSupport.registry(FILE, "mobs.by-type.<vanillaMobType>", config.mobStats());
    }
}

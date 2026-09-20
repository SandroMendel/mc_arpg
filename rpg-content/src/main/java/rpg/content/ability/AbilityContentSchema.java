package rpg.content.ability;

import java.nio.file.Path;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.ability.Ability;
import rpg.core.ability.AbilityConfig;
import rpg.core.ability.AbilityConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;

/** Server-free B16 schema facade for the canonical {@code abilities.yml} document. */
public final class AbilityContentSchema {

    private static final String FILE = "abilities.yml";

    private AbilityContentSchema() {}

    public static rpg.content.ContentDocumentSchema<AbilityConfig> schema() {
        var core = AbilityConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static AbilityConfig validateAndBind(Path source, Object root)
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

    public static TypedContentRegistry<String, Ability> registry(AbilityConfig config) {
        return ContentSchemaSupport.registry(FILE, "abilities.<abilityId>", config.abilities());
    }
}

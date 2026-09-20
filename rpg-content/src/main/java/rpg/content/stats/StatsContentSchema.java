package rpg.content.stats;

import java.nio.file.Path;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.stats.Attribute;
import rpg.core.stats.AttributeDefinition;
import rpg.core.stats.StatConfig;

/** Server-free B16 schema facade for the permitted {@code stats.yml} attributes subdocument. */
public final class StatsContentSchema {

    private static final String FILE = "stats.yml";

    private StatsContentSchema() {}

    public static rpg.content.ContentDocumentSchema<StatConfig> schema() {
        var core = StatConfig.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static StatConfig validateAndBind(Path source, Object root)
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

    public static TypedContentRegistry<Attribute, AttributeDefinition> registry(StatConfig config) {
        return ContentSchemaSupport.registry(FILE, "attributes.<attributeId>", config.definitions());
    }
}

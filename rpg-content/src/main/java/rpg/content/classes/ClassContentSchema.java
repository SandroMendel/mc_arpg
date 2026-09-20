package rpg.content.classes;

import java.nio.file.Path;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.session.CharacterClass;

/** Server-free B16 schema facade for the canonical {@code classes.yml} document. */
public final class ClassContentSchema {

    private static final String FILE = "classes.yml";

    private ClassContentSchema() {}

    public static rpg.content.ContentDocumentSchema<ClassConfig> schema() {
        var core = ClassConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static ClassConfig validateAndBind(Path source, Object root)
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

    public static TypedContentRegistry<CharacterClass, CharacterClassDefinition> registry(
            ClassConfig config) {
        return ContentSchemaSupport.registry(FILE, "classes.<classId>", config.definitions());
    }
}

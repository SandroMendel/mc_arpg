package rpg.content.currency;

import java.nio.file.Path;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.currency.CurrencyConfig;
import rpg.core.currency.CurrencyConfigSchema;

/** Server-free B16 schema facade for the canonical {@code currency.yml} document. */
public final class CurrencyContentSchema {

    private static final String FILE = "currency.yml";

    private CurrencyContentSchema() {}

    public static rpg.content.ContentDocumentSchema<CurrencyConfig> schema() {
        var core = CurrencyConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static CurrencyConfig validateAndBind(Path source, Object root)
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

    public static TypedContentRegistry<String, Long> registry(CurrencyConfig config) {
        return ContentSchemaSupport.registry(
                FILE, "drops.by-type.<vanillaMobType>", config.dropsByType());
    }
}

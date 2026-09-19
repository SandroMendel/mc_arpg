package rpg.content.item;

import java.nio.file.Path;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemConfigSchema;
import rpg.core.item.ItemTemplate;
import rpg.core.item.LootTable;
import rpg.core.item.VendorStock;

/** Server-free B16 schema facade for the canonical {@code items.yml} document. */
public final class ItemContentSchema {

    private static final String FILE = "items.yml";

    private ItemContentSchema() {}

    public static rpg.content.ContentDocumentSchema<ItemConfig> schema() {
        var core = ItemConfigSchema.schema();
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root)
            throws ConfigValidationException {
        return schema().validate(source, root);
    }

    public static ItemConfig validateAndBind(Path source, Object root)
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

    public static TypedContentRegistry<String, ItemTemplate> templateRegistry(ItemConfig config) {
        return ContentSchemaSupport.registry(FILE, "templates.<itemTemplateId>", config.templates());
    }

    public static TypedContentRegistry<String, VendorStock> vendorRegistry(ItemConfig config) {
        return ContentSchemaSupport.registry(FILE, "vendors.<zoneId>", config.vendors());
    }

    public static TypedContentRegistry<String, LootTable> lootByZoneRegistry(ItemConfig config) {
        return ContentSchemaSupport.registry(FILE, "loot.by-zone.<zoneId>", config.loot().byZone());
    }

    public static TypedContentRegistry<String, LootTable> lootByKindRegistry(ItemConfig config) {
        return ContentSchemaSupport.registry(
                FILE, "loot.by-kind.<mobKindId>", config.loot().byKind());
    }

    public static TypedContentRegistry<String, LootTable> lootByBossRegistry(ItemConfig config) {
        return ContentSchemaSupport.registry(
                FILE, "loot.by-boss.<mobKindId>", config.loot().byBoss());
    }
}

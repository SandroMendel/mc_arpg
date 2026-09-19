package rpg.content.zone;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentRegistryContract;
import rpg.content.ContentSchemaSupport;
import rpg.content.TypedContentRegistry;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.ValidatedConfigView;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.WorldResolver;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneConfig;
import rpg.core.zone.ZoneConfigSchema;

/** Server-free B16 schema facade for the canonical {@code zones.yml} document. */
public final class ZoneContentSchema {

    private static final String FILE = "zones.yml";

    private ZoneContentSchema() {}

    /**
     * Builds the zone schema with the caller's server-free world-name resolver.
     *
     * <p>The resolver is an existing core boundary; it can be supplied by a platform adapter or a
     * test without introducing Bukkit/Paper into this module.
     */
    public static rpg.content.ContentDocumentSchema<ZoneConfig> schema(WorldResolver worlds) {
        var core = ZoneConfigSchema.schema(worlds);
        return ContentSchemaSupport.wrap(FILE, core);
    }

    public static ValidatedConfigView validate(Path source, Object root, WorldResolver worlds)
            throws ConfigValidationException {
        return schema(worlds).validate(source, root);
    }

    public static ZoneConfig validateAndBind(Path source, Object root, WorldResolver worlds)
            throws ConfigValidationException {
        return schema(worlds).validateAndBind(source, root);
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

    public static TypedContentRegistry<String, Zone> registry(ZoneConfig config) {
        LinkedHashMap<String, Zone> zones = new LinkedHashMap<>();
        config.zones().forEach(zone -> zones.put(zone.key(), zone));
        return ContentSchemaSupport.registry(FILE, "zones.<zoneId>", zones);
    }

    /** Returns the nested zone-to-spawn-area value registries declared by the document policy. */
    public static TypedContentRegistry<String, TypedContentRegistry<String, SpawnArea>>
            spawnAreaRegistry(ZoneConfig config) {
        LinkedHashMap<String, TypedContentRegistry<String, SpawnArea>> nested = new LinkedHashMap<>();
        for (Zone zone : config.zones()) {
            LinkedHashMap<String, SpawnArea> areas = new LinkedHashMap<>();
            for (SpawnArea area : zone.spawnAreas()) {
                areas.put(area.key(), area);
            }
            nested.put(
                    zone.key(),
                    ContentSchemaSupport.registry(
                            FILE, "zones.<zoneId>.spawn-areas[].key", areas));
        }
        return ContentSchemaSupport.registry(FILE, "zones.<zoneId>", nested);
    }
}

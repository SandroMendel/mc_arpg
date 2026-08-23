package rpg.core.zone;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;
import rpg.core.scheduler.WorldPosition;

/**
 * Schema for {@code zones.yml} (FR-001, FR-013).
 *
 * <p><b>Only the top level can be declared as fields.</b> The zones themselves are a map with free
 * keys - the same shape {@code drops.by-type} and {@code mob-xp.by-type} use - so the depth is
 * validated in the binder. Everything the binder refuses throws with a message naming the offending
 * place, and the loader turns that into the fail-fast the constitution asks for (Principle V).
 *
 * <p><b>There is no runtime precedence rule.</b> Overlapping zones, a core outside its region, a
 * spawn area in a core, a crystal without a destination - all of it is refused at start rather than
 * quietly resolved later. What must not happen should not need a tie-breaker.
 *
 * <p><b>One refusal from the task list turned out to be unreachable, and that is worth saying.</b>
 * A duplicate <em>zone</em> key cannot occur: {@code zones} is a YAML mapping, so the parser already
 * guarantees the keys are distinct. Uniqueness is enforced - by the format rather than by a check
 * here. Duplicate <em>crystal</em> and <em>spawn area</em> keys are a different matter: those are
 * free strings across zones and inside lists, so both are checked below.
 */
public final class ZoneConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private ZoneConfigSchema() {}

    public static ConfigSchema<ZoneConfig> schema(WorldResolver worlds) {
        ConfigSchema.Builder<ZoneConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        builder.optional("provisional", FieldType.BOOLEAN, Boolean.FALSE);
        builder.required("fallback-point", FieldType.MAP);
        builder.required("zones", FieldType.MAP);
        // The cooldown keeps a player walking a border from being warned on every step (FR-024).
        // Seconds with the unit in the key, like B08b's despawn-seconds: the configuration layer has
        // no duration type, and a second way to write time would be one too many.
        builder.optional("warning-cooldown-seconds", FieldType.INTEGER, 30);
        // ADR-030. A switch and not a number: the eight seconds themselves belong to B05 and are
        // read from there, never copied (FR-039).
        builder.optional("combat-logout", FieldType.STRING, "death");
        return builder.boundTo(view -> bind(view, worlds)).build();
    }

    private static ZoneConfig bind(ConfigView view, WorldResolver worlds) {
        WorldPosition fallback = readPoint(view.getMap("fallback-point"), "fallback-point", worlds);
        List<Zone> zones = readZones(view.getMap("zones"), worlds);
        if (zones.isEmpty()) {
            throw new IllegalArgumentException("zones: at least one region is required");
        }
        checkNoOverlap(zones);
        checkExactlyOneStartRegion(zones);
        checkCrystalKeysAreUnique(zones);
        return new ZoneConfig(
                view.getBoolean("provisional"),
                fallback,
                zones,
                Duration.ofSeconds(view.getInt("warning-cooldown-seconds")),
                readLogoutMode(view.getString("combat-logout")));
    }

    // ---------------------------------------------------------------- zones

    private static List<Zone> readZones(Map<?, ?> raw, WorldResolver worlds) {
        List<Zone> zones = new ArrayList<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.isBlank()) {
                throw new IllegalArgumentException("zones: a zone key must not be blank");
            }
            zones.add(readZone(key, section(entry.getValue(), "zones." + key), worlds));
        }
        return zones;
    }

    private static Zone readZone(String key, Map<?, ?> body, WorldResolver worlds) {
        String where = "zones." + key;
        String worldName = requireString(body, "world", where);
        UUID worldId =
                worlds.resolve(worldName)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                where
                                                        + ".world: no such world '"
                                                        + worldName
                                                        + "' (FR-002a). A zone is (worldId,"
                                                        + " geometry); an unknown world cannot"
                                                        + " carry one."));

        Area area = readArea(body.get("area"), where + ".area");
        LevelBand band = readBand(section(body.get("level-band"), where + ".level-band"), where);
        boolean pvp = optionalBoolean(body, "pvp", false);
        boolean startRegion = optionalBoolean(body, "start-region", false);

        Optional<SafeCore> core = Optional.empty();
        if (body.get("safe-core") != null) {
            core = Optional.of(readSafeCore(section(body.get("safe-core"), where + ".safe-core"), where, worldId, area));
        }

        List<SpawnArea> spawnAreas = readSpawnAreas(body.get("spawn-areas"), where, area, core);

        Optional<WaypointCrystal> crystal = Optional.empty();
        if (body.get("crystal") != null) {
            if (core.isEmpty()) {
                throw new IllegalArgumentException(
                        where
                                + ".crystal: a crystal needs a safe-core in the same zone, because"
                                + " travelling leads to that core's respawn-point (FR-051c)");
            }
            crystal =
                    Optional.of(
                            readCrystal(section(body.get("crystal"), where + ".crystal"), where, area));
        }

        return new Zone(key, worldId, area, band, core, spawnAreas, crystal, pvp, startRegion);
    }

    private static SafeCore readSafeCore(Map<?, ?> body, String where, UUID worldId, Area zoneArea) {
        Area coreArea = readArea(body.get("area"), where + ".safe-core.area");
        if (!coreArea.isInside(zoneArea)) {
            throw new IllegalArgumentException(
                    where
                            + ".safe-core.area: the core must lie completely inside its zone"
                            + " (FR-009)");
        }
        Map<?, ?> point = section(body.get("respawn-point"), where + ".safe-core.respawn-point");
        SafeCore core =
                new SafeCore(
                        coreArea,
                        new WorldPosition(
                                worldId,
                                requireDouble(point, "x", where + ".safe-core.respawn-point"),
                                requireDouble(point, "y", where + ".safe-core.respawn-point"),
                                requireDouble(point, "z", where + ".safe-core.respawn-point")));
        if (!core.respawnPointIsInside()) {
            throw new IllegalArgumentException(
                    where
                            + ".safe-core.respawn-point: must lie inside the core, otherwise a death"
                            + " would put the character outside their own refuge");
        }
        return core;
    }

    private static List<SpawnArea> readSpawnAreas(
            Object raw, String where, Area zoneArea, Optional<SafeCore> core) {
        if (raw == null) {
            return List.of();
        }
        List<SpawnArea> areas = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int at = 0;
        for (Object element : list(raw, where + ".spawn-areas")) {
            String at_ = where + ".spawn-areas[" + at++ + "]";
            Map<?, ?> body = section(element, at_);
            String key = requireString(body, "key", at_);
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        at_ + ".key: '" + key + "' appears twice in this zone (FR-055)");
            }
            Area area = readArea(body.get("area"), at_ + ".area");
            if (!area.isInside(zoneArea)) {
                throw new IllegalArgumentException(
                        at_ + ".area: a spawn area must lie inside its own zone (FR-055)");
            }
            if (core.isPresent() && area.intersects(core.get().area())) {
                throw new IllegalArgumentException(
                        at_
                                + ".area: a spawn area must not reach into the safe core - nothing"
                                + " spawns there (FR-055)");
            }
            areas.add(new SpawnArea(key, area));
        }
        return areas;
    }

    private static WaypointCrystal readCrystal(Map<?, ?> body, String where, Area zoneArea) {
        String key = requireString(body, "key", where + ".crystal");
        Area trigger = readArea(body.get("trigger-area"), where + ".crystal.trigger-area");
        if (!trigger.isInside(zoneArea)) {
            throw new IllegalArgumentException(
                    where
                            + ".crystal.trigger-area: must lie inside its own zone, otherwise the"
                            + " crystal would belong to a region it does not stand in (FR-051d)");
        }
        Object price = body.get("price");
        if (price == null) {
            throw new IllegalArgumentException(where + ".crystal.price is missing (FR-050c)");
        }
        if (!(price instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + ".crystal.price must be a number, but was " + describe(price));
        }
        return new WaypointCrystal(key, trigger, number.longValue());
    }

    // ---------------------------------------------------------------- cross-zone checks

    private static void checkNoOverlap(List<Zone> zones) {
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                Zone a = zones.get(i);
                Zone b = zones.get(j);
                if (a.worldId().equals(b.worldId()) && a.area().intersects(b.area())) {
                    throw new IllegalArgumentException(
                            "zones '"
                                    + a.key()
                                    + "' and '"
                                    + b.key()
                                    + "' overlap in the same world (FR-012). There is no runtime"
                                    + " precedence rule: what must not happen is refused here.");
                }
            }
        }
    }

    private static void checkExactlyOneStartRegion(List<Zone> zones) {
        List<String> found = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.startRegion()) {
                found.add(zone.key());
            }
        }
        if (found.size() != 1) {
            throw new IllegalArgumentException(
                    "exactly one zone must carry start-region: true, but "
                            + (found.isEmpty() ? "none does" : "these do: " + found)
                            + " (FR-037a). Without it a new character would fall back to the"
                            + " Minecraft world spawn.");
        }
    }

    private static void checkCrystalKeysAreUnique(List<Zone> zones) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (Zone zone : zones) {
            if (zone.crystal().isEmpty()) {
                continue;
            }
            String key = zone.crystal().get().key();
            String previous = owners.putIfAbsent(key, zone.key());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "crystal key '"
                                + key
                                + "' is used by both '"
                                + previous
                                + "' and '"
                                + zone.key()
                                + "' (FR-051d). Player unlocks reference this key, so two crystals"
                                + " sharing it would share unlocks.");
            }
        }
    }

    // ---------------------------------------------------------------- small readers

    private static Area readArea(Object raw, String where) {
        List<Cuboid> parts = new ArrayList<>();
        int at = 0;
        for (Object element : list(raw, where)) {
            parts.add(readCuboid(section(element, where + "[" + at++ + "]"), where));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException(where + ": at least one box is required");
        }
        return new Area(parts);
    }

    private static Cuboid readCuboid(Map<?, ?> body, String where) {
        int minX = requireInt(body, "min-x", where);
        int minZ = requireInt(body, "min-z", where);
        int maxX = requireInt(body, "max-x", where);
        int maxZ = requireInt(body, "max-z", where);
        boolean hasMinY = body.get("min-y") != null;
        boolean hasMaxY = body.get("max-y") != null;
        if (hasMinY != hasMaxY) {
            throw new IllegalArgumentException(
                    where
                            + ": min-y and max-y come as a pair - one without the other leaves it"
                            + " unclear whether the box is bounded vertically (FR-004)");
        }
        if (!hasMinY) {
            return Cuboid.of(minX, minZ, maxX, maxZ);
        }
        return Cuboid.of(
                minX, requireInt(body, "min-y", where), minZ, maxX, requireInt(body, "max-y", where), maxZ);
    }

    private static LevelBand readBand(Map<?, ?> body, String where) {
        return new LevelBand(
                requireInt(body, "min", where + ".level-band"),
                requireInt(body, "max", where + ".level-band"));
    }

    private static WorldPosition readPoint(Map<?, ?> body, String where, WorldResolver worlds) {
        String worldName = requireString(body, "world", where);
        UUID worldId =
                worlds.resolve(worldName)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                where
                                                        + ".world: no such world '"
                                                        + worldName
                                                        + "' (FR-002a)"));
        return new WorldPosition(
                worldId,
                requireDouble(body, "x", where),
                requireDouble(body, "y", where),
                requireDouble(body, "z", where));
    }

    private static boolean readLogoutMode(String raw) {
        String mode = raw.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "death" -> true;
            case "none" -> false;
            default ->
                    throw new IllegalArgumentException(
                            "combat-logout must be 'death' or 'none', but was '" + raw + "'");
        };
    }

    private static Map<?, ?> section(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        throw new IllegalArgumentException(path + " must be a section, but was " + describe(value));
    }

    private static List<?> list(Object value, String path) {
        if (value instanceof List<?> found) {
            return found;
        }
        if (value == null) {
            throw new IllegalArgumentException(path + " is missing");
        }
        throw new IllegalArgumentException(path + " must be a list, but was " + describe(value));
    }

    private static String requireString(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + field + " is missing");
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(where + "." + field + " must not be blank");
        }
        return text;
    }

    private static int requireInt(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + field + " is missing");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + "." + field + " must be a number, but was " + describe(value));
        }
        return number.intValue();
    }

    private static double requireDouble(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + field + " is missing");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    where + "." + field + " must be a number, but was " + describe(value));
        }
        return number.doubleValue();
    }

    private static boolean optionalBoolean(Map<?, ?> body, String field, boolean fallback) {
        Object value = body.get(field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(
                    field + " must be true or false, but was " + describe(value));
        }
        return flag;
    }

    private static String describe(Object value) {
        return value == null ? "nothing" : value.getClass().getSimpleName();
    }
}

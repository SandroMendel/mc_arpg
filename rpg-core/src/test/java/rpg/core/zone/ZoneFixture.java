package rpg.core.zone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Builders for zone documents and zone objects, shared by the tests of this block.
 *
 * <p>Documents are built as mutable maps on purpose: almost every validation test takes a valid
 * document and breaks exactly one thing, which is the only way to be sure the message names that one
 * thing and not something else.
 */
final class ZoneFixture {

    static final UUID WORLD = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private ZoneFixture() {}

    static WorldResolver worlds() {
        return WorldResolver.of(Map.of("world", WORLD, "nether", OTHER_WORLD));
    }

    /** Messages that have a text for everything. The wording is irrelevant to these tests. */
    static Messages allMessages() {
        return messagesMissing(null);
    }

    /**
     * Messages that have a text for everything except one zone's name.
     *
     * @param missingZoneKey the zone whose name is absent, or {@code null} for none
     */
    static Messages messagesMissing(String missingZoneKey) {
        MessageKey absent =
                missingZoneKey == null ? null : ZoneMessageKeys.nameOf(missingZoneKey);
        return new Messages() {
            @Override
            public String get(MessageKey key) {
                return key.value();
            }

            @Override
            public String get(MessageKey key, Map<String, String> placeholders) {
                return key.value();
            }

            @Override
            public boolean contains(MessageKey key) {
                return absent == null || !absent.equals(key);
            }
        };
    }

    /** A box without vertical bounds. */
    static Map<String, Object> box(int minX, int minZ, int maxX, int maxZ) {
        Map<String, Object> box = new LinkedHashMap<>();
        box.put("min-x", minX);
        box.put("min-z", minZ);
        box.put("max-x", maxX);
        box.put("max-z", maxZ);
        return box;
    }

    /** A box with vertical bounds. */
    static Map<String, Object> box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Map<String, Object> box = box(minX, minZ, maxX, maxZ);
        box.put("min-y", minY);
        box.put("max-y", maxY);
        return box;
    }

    static List<Object> area(Map<String, Object> box) {
        List<Object> area = new ArrayList<>();
        area.add(box);
        return area;
    }

    static Map<String, Object> point(double x, double y, double z) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", x);
        point.put("y", y);
        point.put("z", z);
        return point;
    }

    static Map<String, Object> band(int min, int max) {
        Map<String, Object> band = new LinkedHashMap<>();
        band.put("min", min);
        band.put("max", max);
        return band;
    }

    /**
     * One region, shaped like the shipped ones: a 1000x1000 area, a 120x120 core at its centre, one
     * crystal in that core, two spawn areas outside it.
     */
    static Map<String, Object> region(
            String key, int centreX, int centreZ, int bandMin, int bandMax, boolean startRegion) {
        Map<String, Object> zone = new LinkedHashMap<>();
        zone.put("world", "world");
        if (startRegion) {
            zone.put("start-region", Boolean.TRUE);
        }
        zone.put("level-band", band(bandMin, bandMax));
        zone.put("pvp", Boolean.FALSE);
        zone.put("area", area(box(centreX - 500, centreZ - 500, centreX + 500, centreZ + 500)));

        Map<String, Object> core = new LinkedHashMap<>();
        core.put("area", area(box(centreX - 60, centreZ - 60, centreX + 60, centreZ + 60)));
        core.put("respawn-point", point(centreX + 0.5d, 65.0d, centreZ + 0.5d));
        zone.put("safe-core", core);

        Map<String, Object> crystal = new LinkedHashMap<>();
        crystal.put("key", key + "-crystal");
        crystal.put("price", 25);
        crystal.put(
                "trigger-area",
                area(box(centreX - 2, 64, centreZ - 2, centreX + 2, 67, centreZ + 2)));
        zone.put("crystal", crystal);

        List<Object> spawnAreas = new ArrayList<>();
        spawnAreas.add(spawnArea(key + "-east", centreX + 120, centreZ - 80, centreX + 300, centreZ + 80));
        spawnAreas.add(spawnArea(key + "-west", centreX - 300, centreZ - 80, centreX - 120, centreZ + 80));
        zone.put("spawn-areas", spawnAreas);
        return zone;
    }

    static Map<String, Object> spawnArea(String key, int minX, int minZ, int maxX, int maxZ) {
        Map<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("key", key);
        spawn.put("area", area(box(minX, minZ, maxX, maxZ)));
        return spawn;
    }

    /** The six shipped regions, level bands 1-10 up to 51-60, laid out as a 3x2 grid. */
    static Map<String, Object> sixRegions() {
        Map<String, Object> zones = new LinkedHashMap<>();
        zones.put("greenfields", region("greenfields", 0, 0, 1, 10, true));
        zones.put("dustlands", region("dustlands", 1500, 0, 11, 20, false));
        zones.put("safari-plains", region("safari-plains", 3000, 0, 21, 30, false));
        zones.put("terracotta-canyons", region("terracotta-canyons", 0, 1500, 31, 40, false));
        zones.put("darkforest", region("darkforest", 1500, 1500, 41, 50, false));
        zones.put("pale-wilds", region("pale-wilds", 3000, 1500, 51, 60, false));
        return zones;
    }

    /** A whole valid document, mutable all the way down. */
    static Map<String, Object> document() {
        Map<String, Object> fallback = point(0.5d, 64.0d, 0.5d);
        fallback.put("world", "world");

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("provisional", Boolean.TRUE);
        document.put("fallback-point", fallback);
        document.put("warning-cooldown-seconds", 30);
        document.put("combat-logout", "death");
        document.put("zones", sixRegions());
        return document;
    }

    /** The region body inside a document, for breaking one thing. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> zoneIn(Map<String, Object> document, String key) {
        Map<String, Object> zones = (Map<String, Object>) document.get("zones");
        return (Map<String, Object>) zones.get(key);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> zones(Map<String, Object> document) {
        return (Map<String, Object>) document.get("zones");
    }
}

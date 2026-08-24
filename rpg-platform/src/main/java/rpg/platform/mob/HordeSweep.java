package rpg.platform.mob;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

import rpg.core.event.EventBus;
import rpg.core.mob.CleanupRule;
import rpg.core.mob.HordeRegistry;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.mob.NearbyChunks;
import rpg.core.mob.SpawnPlanner;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.Cuboid;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneChangedEvent;
import rpg.core.zone.Zones;
import rpg.platform.zone.BukkitPositions;

/**
 * Der selbst neu eingeplante Einmal-Durchlauf je bevoelkerter Zone (R4). Nachsetzen und Aufraeumen
 * in einem Zug (FR-011 bis FR-022).
 *
 * <p><b>Warum das keine wiederkehrende Aufgabe je Spieler oder Entitaet ist</b> (Prinzip II). Ein
 * Durchlauf entsteht erst, wenn eine Zone ihren ersten Spieler bekommt, und plant sich danach
 * selbst neu, solange sie noch etwas zu tun hat. Bei sechs Regionen sind das hoechstens sechs
 * Aufgaben, nie eine je Kreatur und nie eine je Spieler.
 *
 * <p><b>Der Start ist ereignisgetrieben.</b> {@link #onZoneChanged} sieht {@link ZoneChangedEvent}
 * und stoesst eine neue Schleife nur an, wenn fuer diese Zone noch keine laeuft. Eine leere Zone
 * hat also gar keine Aufgabe eingeplant (FR-015, research.md R4) - nicht nur eine, die nichts tut.
 *
 * <p><b>Verlaesst der letzte Spieler die Zone, endet die Schleife nicht sofort</b> (FR-019). Sie
 * laeuft weiter und raeumt weiterhin nach Reichweite auf, merkt sich aber, seit wann niemand mehr
 * da ist - und erst nach Ablauf der konfigurierten Frist wird die ganze Zone auf einen Schlag
 * geraeumt und die Schleife endet. Ein kurzer Rueckweg oder ein Zonenwechsel loescht so nichts.
 *
 * <p><b>Eine Zone, deren Konfiguration oder Welt Aerger macht, darf die anderen nicht mitreissen</b>
 * (FR-044, Prinzip VI). Der Kern des Durchlaufs faengt jede Ausnahme, protokolliert sie hoechstens
 * einmal je Vorfall und plant trotzdem neu - sonst bliebe genau diese Horde fuer immer stehen.
 */
public final class HordeSweep implements Listener {

    private final Server server;
    private final Scheduler scheduler;
    private final Supplier<Zones> zones;
    private final Supplier<MobConfig> config;
    private final HordeRegistry registry;
    private final Predicate<UUID> inCombat;
    private final PaperMobPlacer placer;
    private final Clock clock;
    private final Logger logger;
    private final RandomGenerator random = RandomGenerator.getDefault();

    /** Welche Zonen gerade eine eigene Schleife laufen haben - hoechstens eine je Zone. */
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    /** Welche Zonen bereits einmal ueber einen Fehler geloggt haben - einmal je Vorfall (FR-044). */
    private final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();

    /** Seit wann eine Zone keinen Spieler mehr hat - fehlt der Eintrag, ist sie bevoelkert. */
    private final java.util.Map<String, Instant> emptySince = new ConcurrentHashMap<>();

    /** Der wiederverwendete raeumliche Index je Zone (research.md R3a) - eine Zuweisung je Zone. */
    private final java.util.Map<String, NearbyChunks> nearbyByZone = new ConcurrentHashMap<>();

    public HordeSweep(
            Server server,
            Scheduler scheduler,
            Supplier<Zones> zones,
            Supplier<MobConfig> config,
            HordeRegistry registry,
            Predicate<UUID> inCombat,
            PaperMobPlacer placer,
            Clock clock,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.inCombat = Objects.requireNonNull(inCombat, "inCombat");
        this.placer = Objects.requireNonNull(placer, "placer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Beginnt zu hoeren - eine Zone, die schon Spieler hat, kommt beim naechsten Wechsel dran. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(ZoneChangedEvent.class, this::onZoneChanged);
    }

    /**
     * Erneut anstossen fuer jede Zone, die schon jetzt Spieler haelt.
     *
     * <p>Fuer den Start: eine Zone, die von Anfang an bevoelkert ist, hat noch kein
     * {@link ZoneChangedEvent} ausgeloest, das dieser Zuhoerer sehen koennte.
     */
    public void ensureScheduledForPopulatedZones() {
        MobConfig current = config.get();
        Zones currentZones = zones.get();
        for (String zoneKey : current.hordes().keySet()) {
            if (countPlayers(zoneKey, currentZones) > 0) {
                ensureScheduled(zoneKey);
            }
        }
    }

    /**
     * Alle eigenen Kreaturen entfernen und den Bestand leeren (FR-023).
     *
     * <p>Beim Abschalten, synchron im Tick - Bukkits eigene Abschaltreihenfolge laeuft schon dort.
     * Sonst stuende beim naechsten Start eine Zaehlung ohne Kreaturen, oder schlimmer: eine
     * Kreatur, die keinem Durchlauf mehr gehoert und nie mehr geraeumt wird.
     */
    public void shutdown() {
        for (HordeRegistry.Entry entry : List.copyOf(registry.all())) {
            Entity entity = server.getEntity(entry.entityId());
            if (entity != null) {
                placer.remove(entity);
            }
        }
        registry.clear();
        active.clear();
        emptySince.clear();
        nearbyByZone.clear();
    }

    private void onZoneChanged(ZoneChangedEvent event) {
        event.to().ifPresent(this::ensureScheduled);
    }

    private void ensureScheduled(String zoneKey) {
        if (config.get().horde(zoneKey).isEmpty()) {
            return;
        }
        if (active.add(zoneKey)) {
            scheduler.runAsync(() -> sweep(zoneKey));
        }
    }

    private void sweep(String zoneKey) {
        boolean keepGoing;
        try {
            keepGoing = doSweep(zoneKey);
        } catch (RuntimeException failure) {
            logFailureOnce(zoneKey, failure);
            // FR-044: eine kaputte Zone darf nicht fuer immer stehenbleiben - der naechste
            // Durchlauf bekommt eine neue Chance.
            keepGoing = true;
        }
        if (keepGoing) {
            scheduler.runAsyncDelayed(config.get().respawnInterval(), () -> sweep(zoneKey));
        } else {
            active.remove(zoneKey);
        }
    }

    /**
     * Ein Durchlauf: aufraeumen, dann - wenn noch jemand da ist - nachsetzen.
     *
     * @return ob diese Zone weiter beobachtet werden soll, oder ob die Schleife endet
     */
    private boolean doSweep(String zoneKey) {
        Zones currentZones = zones.get();
        MobConfig currentConfig = config.get();
        int playersInZone = countPlayers(zoneKey, currentZones);

        if (playersInZone <= 0) {
            // FR-019, geprueft bevor irgendetwas gerechnet wird: solange die Kulanzfrist laeuft,
            // bleibt alles unangetastet - eine Kreatur wird hier NICHT auch noch ueber die Reichweite
            // aus FR-020 geraeumt, sonst haette die Frist keine Wirkung mehr (niemand ist gerade in
            // der Naehe, sonst waere die Zone ja nicht leer).
            Instant now = Instant.now(clock);
            Instant since = emptySince.computeIfAbsent(zoneKey, key -> now);
            boolean abandoned =
                    CleanupRule.zoneIsAbandoned(Duration.between(since, now), currentConfig.cleanupAfter());
            if (!abandoned) {
                return true;
            }
            cleanup(zoneKey, currentConfig, true);
            emptySince.remove(zoneKey);
            nearbyByZone.remove(zoneKey);
            return false;
        }

        emptySince.remove(zoneKey);
        cleanup(zoneKey, currentConfig, false);

        Optional<HordeSpec> horde = currentConfig.horde(zoneKey);
        if (horde.isEmpty()) {
            // Ein Nachladen kann eine Horde entfernt haben - dann endet auch ihre Schleife.
            return false;
        }
        int serverTotalElsewhere = registry.total() - registry.countIn(zoneKey);
        int zoneTotal = registry.countIn(zoneKey);
        Optional<SpawnPlanner.Decision> decision =
                SpawnPlanner.plan(
                        horde, currentConfig.budget(), serverTotalElsewhere, zoneTotal, playersInZone, random);
        decision.ifPresent(d -> place(zoneKey, currentConfig, horde.get(), d, currentZones));
        return true;
    }

    /**
     * Wer aus dieser Zone jetzt weg soll (FR-019 bis FR-022).
     *
     * <p>Der raeumliche Index wird bei jedem Durchlauf neu gestempelt - von JEDEM Online-Spieler,
     * nicht nur denen dieser Zone: eine Kreatur, die sich ueber die Grenze bewegt hat, bleibt dem
     * Budget ihrer Ursprungszone zugerechnet (FR-017), aber ihre Sichtbarkeit haengt vom naechsten
     * Spieler ab, gleich in welcher Zone er steht. Derselbe Puffer wird wiederverwendet statt neu
     * angelegt (research.md R3a).
     */
    private void cleanup(String zoneKey, MobConfig currentConfig, boolean zoneAbandoned) {
        NearbyChunks nearby = nearbyByZone.computeIfAbsent(zoneKey, key -> new NearbyChunks());
        nearby.clear();
        for (Player player : server.getOnlinePlayers()) {
            Location at = player.getLocation();
            nearby.stampAround(at.getBlockX(), at.getBlockZ(), currentConfig.cleanupRadius());
        }
        for (HordeRegistry.Entry entry : registry.all()) {
            if (!entry.zoneKey().equals(zoneKey)) {
                continue;
            }
            boolean remove =
                    CleanupRule.shouldRemove(
                            zoneAbandoned, entry.chunkKey(), nearby, inCombat.test(entry.entityId()));
            if (remove) {
                removeEntity(entry.entityId());
            }
        }
    }

    /** Entfernen entitaetsgebunden ueber B01s Scheduler, nie ueber den globalen (FR-042). */
    private void removeEntity(UUID entityId) {
        scheduler.runSyncOnEntity(
                new EntityRef(entityId),
                () -> {
                    Entity entity = server.getEntity(entityId);
                    if (entity != null) {
                        placer.remove(entity);
                    }
                    registry.remove(entityId);
                });
    }

    private void place(
            String zoneKey,
            MobConfig currentConfig,
            HordeSpec horde,
            SpawnPlanner.Decision decision,
            Zones currentZones) {
        Optional<MobKind> kind = currentConfig.kind(decision.kindKey());
        Optional<Zone> zone = currentZones.byKey(zoneKey);
        if (kind.isEmpty() || zone.isEmpty()) {
            return;
        }
        SpawnArea area = areaByKey(zone.get(), decision.areaKey());
        if (area == null) {
            return;
        }
        // Ein grober Punkt reicht als Ortsbindung fuer den Scheduler - die eigentliche Wuerfelung
        // von X/Z und die Aufloesung von Y ueber die Weltoberflaeche brauchen ohnehin schon den
        // Tick und laufen darum erst im Rumpf unten.
        Cuboid routing = area.area().parts().get(0);
        WorldPosition routingHint =
                new WorldPosition(
                        zone.get().worldId(),
                        midpoint(routing.minX(), routing.maxX()),
                        Math.max(routing.minY(), 64),
                        midpoint(routing.minZ(), routing.maxZ()));
        UUID worldId = zone.get().worldId();
        scheduler.runSyncAtLocation(
                routingHint, () -> placeInTick(zoneKey, currentConfig, kind.get(), area, worldId));
    }

    private void placeInTick(
            String zoneKey, MobConfig currentConfig, MobKind kind, SpawnArea area, UUID worldId) {
        World world = server.getWorld(worldId);
        if (world == null) {
            return;
        }
        Location location = randomLocationIn(area, world);
        long chunkKey = NearbyChunks.packBlock(location.getBlockX(), location.getBlockZ());
        if (registry.countInChunk(chunkKey) >= currentConfig.budget().perChunk()) {
            // Die schaerfste Grenze entscheidet zuletzt: der Zonencheck im Planer weiss noch
            // nichts vom konkreten Chunk (FR-013).
            return;
        }
        placer.place(kind, location, zoneKey)
                .ifPresent(
                        entity ->
                                registry.add(
                                        new HordeRegistry.Entry(
                                                entity.getUniqueId(),
                                                kind.key(),
                                                zoneKey,
                                                chunkKey,
                                                Instant.now(clock))));
    }

    private Location randomLocationIn(SpawnArea area, World world) {
        List<Cuboid> parts = area.area().parts();
        Cuboid part = parts.get(parts.size() == 1 ? 0 : random.nextInt(parts.size()));
        int x = part.minX() + random.nextInt(Math.max(1, part.maxX() - part.minX() + 1));
        int z = part.minZ() + random.nextInt(Math.max(1, part.maxZ() - part.minZ() + 1));
        int y;
        if (part.boundedVertically()) {
            y = part.minY() + random.nextInt(Math.max(1, part.maxY() - part.minY() + 1));
        } else {
            y = world.getHighestBlockYAt(x, z) + 1;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private int countPlayers(String zoneKey, Zones currentZones) {
        int count = 0;
        for (Player player : server.getOnlinePlayers()) {
            WorldPosition position = BukkitPositions.of(player.getLocation());
            if (zoneKey.equals(currentZones.zoneKeyAt(position))) {
                count++;
            }
        }
        return count;
    }

    private static SpawnArea areaByKey(Zone zone, String areaKey) {
        for (SpawnArea area : zone.spawnAreas()) {
            if (area.key().equals(areaKey)) {
                return area;
            }
        }
        return null;
    }

    private static double midpoint(int min, int max) {
        return min + (max - min) / 2.0;
    }

    private void logFailureOnce(String zoneKey, RuntimeException failure) {
        if (loggedFailures.add(zoneKey)) {
            logger.log(
                    Level.SEVERE,
                    "[mob] the sweep of zone " + zoneKey + " failed and will be logged only once - "
                            + "it keeps retrying every " + config.get().respawnInterval(),
                    failure);
        }
    }

    /** Haelt den Bestand ehrlich, wenn eine Kreatur auf einem fremden Weg verschwindet. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        registry.remove(entity.getUniqueId());
    }
}

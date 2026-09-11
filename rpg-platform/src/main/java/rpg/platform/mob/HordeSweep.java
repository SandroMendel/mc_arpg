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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import rpg.core.event.EventBus;
import rpg.core.mob.BossSpec;
import rpg.core.mob.BossState;
import rpg.core.mob.CleanupRule;
import rpg.core.mob.DensityScaling;
import rpg.core.mob.HordeRegistry;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.mob.NearbyChunks;
import rpg.core.mob.SpawnPlanner;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.Cuboid;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneChangedEvent;
import rpg.core.zone.ZonePresence;
import rpg.core.zone.Zones;

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
    private final ZonePresence zonePresence;
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

    /**
     * Ob ein Boss lebt und wann der letzte gefallen ist, je Zone (US5, FR-029, FR-031).
     *
     * <p><b>Geliehen, nicht angelegt.</b> Dieselbe Karte wie {@code MobModule.bosses()} - die
     * oeffentliche Abfrage {@code Hordes.bossOf(zoneKey)} (contracts/mob-api.md §3) liest genau
     * diese Instanz. Eine zweite, eigene Karte haette hier lautlos einen zweiten, nie gesehenen
     * Bosszustand gefuehrt: die Abfrage haette immer "kein Boss" geantwortet, ganz gleich, was
     * dieser Durchlauf tatsaechlich gesetzt hat.
     */
    private final java.util.Map<String, BossState> bossStates;

    public HordeSweep(
            Server server,
            Scheduler scheduler,
            Supplier<Zones> zones,
            ZonePresence zonePresence,
            Supplier<MobConfig> config,
            HordeRegistry registry,
            java.util.Map<String, BossState> bossStates,
            Predicate<UUID> inCombat,
            PaperMobPlacer placer,
            Clock clock,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.zonePresence = Objects.requireNonNull(zonePresence, "zonePresence");
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.bossStates = Objects.requireNonNull(bossStates, "bossStates");
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
        for (String zoneKey : current.hordes().keySet()) {
            if (countPlayers(zoneKey) > 0) {
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
        bossStates.clear();
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
        MobConfig currentConfig = config.get();
        Duration nextInterval = currentConfig.respawnInterval();
        try {
            int playersInZone = countPlayers(zoneKey);
            nextInterval =
                    DensityScaling.respawnInterval(
                            currentConfig.respawnInterval(), currentConfig.densityPerPlayer(), playersInZone);
            keepGoing = doSweep(zoneKey, currentConfig, playersInZone);
        } catch (RuntimeException failure) {
            logFailureOnce(zoneKey, failure);
            // FR-044: eine kaputte Zone darf nicht fuer immer stehenbleiben - der naechste
            // Durchlauf bekommt eine neue Chance.
            keepGoing = true;
        }
        if (keepGoing) {
            scheduler.runAsyncDelayed(nextInterval, () -> sweep(zoneKey));
        } else {
            active.remove(zoneKey);
        }
    }

    /**
     * Ein Durchlauf: aufraeumen, dann - wenn noch jemand da ist - nachsetzen.
     *
     * @param playersInZone von {@link #sweep} schon ermittelt - eine Zaehlung je Durchlauf reicht
     * @return ob diese Zone weiter beobachtet werden soll, oder ob die Schleife endet
     */
    private boolean doSweep(String zoneKey, MobConfig currentConfig, int playersInZone) {
        Zones currentZones = zones.get();

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
            cleanup(zoneKey, currentConfig, true, currentZones);
            emptySince.remove(zoneKey);
            nearbyByZone.remove(zoneKey);
            return false;
        }

        emptySince.remove(zoneKey);
        cleanup(zoneKey, currentConfig, false, currentZones);

        Optional<HordeSpec> horde = currentConfig.horde(zoneKey);
        if (horde.isEmpty()) {
            // Ein Nachladen kann eine Horde entfernt haben - dann endet auch ihre Schleife.
            return false;
        }
        int serverTotalElsewhere = registry.total() - registry.countIn(zoneKey);
        int zoneTotal = registry.countIn(zoneKey);
        // FR-025, FR-027: min(zieldichte, budget) - die Zieldichte entscheidet hier, OB ueberhaupt
        // geplant wird; das Budget entscheidet in SpawnPlanner unveraendert, ob es erlaubt ist.
        int target =
                DensityScaling.targetDensity(
                        currentConfig.budget(), currentConfig.densityPerPlayer(), playersInZone, serverTotalElsewhere);
        if (zoneTotal < target) {
            Optional<SpawnPlanner.Decision> decision =
                    SpawnPlanner.plan(
                            horde, currentConfig.budget(), serverTotalElsewhere, zoneTotal, playersInZone, random);
            decision.ifPresent(d -> place(zoneKey, currentConfig, horde.get(), d, currentZones));
        }
        BossSpec bossSpec = horde.get().boss();
        if (bossSpec != null) {
            maybeSpawnBoss(zoneKey, currentConfig, bossSpec, currentZones, playersInZone);
        }
        return true;
    }

    /**
     * Ob jetzt ein Boss erscheinen soll (US5, FR-029 bis FR-033): keiner lebt, der Timer ist
     * abgelaufen, und das Budget seiner Zone laesst noch einen weiteren Platz zu - der Boss zaehlt
     * darin mit, er bekommt keinen eigenen (FR-033).
     */
    private void maybeSpawnBoss(
            String zoneKey,
            MobConfig currentConfig,
            BossSpec bossSpec,
            Zones currentZones,
            int playersInZone) {
        BossState state = bossStates.computeIfAbsent(zoneKey, BossState::new);
        if (!state.mayAppear(Instant.now(clock), bossSpec.respawn())) {
            return;
        }
        int serverTotal = registry.total();
        int zoneTotal = registry.countIn(zoneKey);
        if (!currentConfig.budget().allows(serverTotal, zoneTotal, 0, playersInZone)) {
            // Der grobe Vorabcheck ohne Chunk-Zahl - die Chunk-Grenze entscheidet erst im Tick,
            // sobald der tatsaechliche Ort feststeht (derselbe Aufbau wie bei einer gewoehnlichen
            // Kreatur in placeInTick).
            return;
        }
        Optional<MobKind> kind = currentConfig.kind(bossSpec.kindKey());
        Optional<Zone> zone = currentZones.byKey(zoneKey);
        if (kind.isEmpty() || zone.isEmpty()) {
            return;
        }
        SpawnArea area = areaByKey(zone.get(), bossSpec.areaKey());
        if (area == null) {
            return;
        }
        UUID worldId = zone.get().worldId();
        Cuboid routing = area.area().parts().get(0);
        WorldPosition routingHint =
                new WorldPosition(
                        worldId,
                        routing.minX() + bossSpec.offsetX(),
                        Math.max(routing.minY(), 64),
                        routing.minZ() + bossSpec.offsetZ());
        scheduler.runSyncAtLocation(
                routingHint,
                () -> placeBossInTick(zoneKey, currentConfig, kind.get(), area, bossSpec, worldId, state));
    }

    private void placeBossInTick(
            String zoneKey,
            MobConfig currentConfig,
            MobKind kind,
            SpawnArea area,
            BossSpec bossSpec,
            UUID worldId,
            BossState state) {
        World world = server.getWorld(worldId);
        if (world == null || state.alive().isPresent()) {
            // Zwischen dem Einplanen und diesem Tick koennte ein anderer Durchlauf schon gesetzt
            // haben - derselbe Race-Schutz wie das erneute Chunk-Budget unten.
            return;
        }
        Location location = bossLocationIn(area, bossSpec, world);
        long chunkKey = NearbyChunks.packBlock(location.getBlockX(), location.getBlockZ());
        if (registry.countInChunk(chunkKey) >= currentConfig.budget().perChunk()) {
            return;
        }
        placer.place(kind, location, zoneKey)
                .ifPresent(
                        entity -> {
                            registry.add(
                                    new HordeRegistry.Entry(
                                            entity.getUniqueId(),
                                            kind.key(),
                                            zoneKey,
                                            chunkKey,
                                            Instant.now(clock),
                                            HordeRegistry.Origin.BUDGET));
                            state.placed(entity.getUniqueId());
                        });
    }

    /** Der Bereichsschluessel plus Versatz, deterministisch - kein Wuerfeln wie bei einer Horde (research.md R10). */
    private Location bossLocationIn(SpawnArea area, BossSpec bossSpec, World world) {
        Cuboid part = area.area().parts().get(0);
        double x = part.minX() + bossSpec.offsetX();
        double z = part.minZ() + bossSpec.offsetZ();
        double y;
        if (part.boundedVertically()) {
            y = part.minY() + bossSpec.offsetY();
        } else {
            y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1 + bossSpec.offsetY();
        }
        return new Location(world, x + 0.5, y, z + 0.5);
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
    private void cleanup(
            String zoneKey, MobConfig currentConfig, boolean zoneAbandoned, Zones currentZones) {
        NearbyChunks nearby = nearbyByZone.computeIfAbsent(zoneKey, key -> new NearbyChunks());
        nearby.clear();
        for (Player player : server.getOnlinePlayers()) {
            Location at = player.getLocation();
            nearby.stampAround(at.getBlockX(), at.getBlockZ(), currentConfig.cleanupRadius());
        }
        Optional<Zone> zone = currentZones.byKey(zoneKey);
        if (zone.isEmpty()) {
            // Ein Nachladen kann die Zone entfernt haben - ohne Welt keine Position zum Entfernen.
            return;
        }
        UUID worldId = zone.get().worldId();
        // Eine Momentaufnahme, nicht die lebende Sicht: removeEntity() loest ueber
        // onEntityRemove synchron ein registry.remove() aus, und das wuerde die laufende
        // Iteration ueber registry.all() sonst mit einer ConcurrentModificationException
        // sprengen, sobald mehr als eine Kreatur in dieser Zone steht (derselbe Grund, aus dem
        // shutdown() bereits eine Kopie zieht).
        for (HordeRegistry.Entry entry : List.copyOf(registry.all())) {
            if (!entry.zoneKey().equals(zoneKey)) {
                continue;
            }
            boolean remove =
                    CleanupRule.shouldRemove(
                            zoneAbandoned, entry.chunkKey(), nearby, inCombat.test(entry.entityId()));
            if (remove) {
                removeEntity(entry.entityId(), worldId, entry.chunkKey());
            }
        }
    }

    /**
     * Entfernen ortsgebunden ueber B01s Scheduler, nie ueber den globalen (FR-042).
     *
     * <p><b>Ortsgebunden, nicht entitaetsgebunden.</b> {@code cleanup()} laeuft im selbst neu
     * eingeplanten Durchlauf dieser Klasse, und der ist ein Async-Task (R4) - genau dort darf
     * {@link org.bukkit.Server#getEntity(UUID)} nicht aufgeloest werden (siehe
     * {@link rpg.platform.scheduler.PaperSchedulerAdapter#resolve}), also liefe ein entitaetsgebundenes
     * Einplanen hier IMMER auf einen sofort verworfenen Auftrag hinaus - eine Kreatur ausser Reichweite
     * wuerde nie tatsaechlich entfernt, nur immer wieder als "weg" markiert. Der grobe Chunk-Mittelpunkt
     * reicht als Ortsbindung, genau wie {@code placeBossInTick} es fuer das Setzen schon vormacht.
     */
    private void removeEntity(UUID entityId, UUID worldId, long chunkKey) {
        int chunkX = (int) (chunkKey >> 32);
        int chunkZ = (int) chunkKey;
        WorldPosition at = new WorldPosition(worldId, chunkX * 16 + 8, 64, chunkZ * 16 + 8);
        scheduler.runSyncAtLocation(
                at,
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
                                                Instant.now(clock),
                                                HordeRegistry.Origin.BUDGET)));
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

    /**
     * Wie viele Spieler in dieser Zone sind - ueber B09s {@link ZonePresence}, nicht ueber eine
     * eigene Geometrieaufloesung (research.md, T073). {@code zoneKeyOf} liest nur zwei
     * Map-Eintraege je Spieler; {@code ZoneTracker} haelt sie ohnehin schon aktuell, weil B09 sie
     * bei jeder Bewegung nachfuehrt.
     */
    private int countPlayers(String zoneKey) {
        int count = 0;
        for (Player player : server.getOnlinePlayers()) {
            if (zoneKey.equals(zonePresence.zoneKeyOf(player.getUniqueId()))) {
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

    /**
     * Entfernt eine getaggte Kreatur, deren fluechtiger Registry-Eintrag einen Neustart nicht
     * ueberlebt hat (ADR-050).
     *
     * <p>Ein Admin-Spawn bleibt deshalb beim Chunk-Laden erhalten, solange er im gemeinsamen Bestand
     * steht. Nach einem Neustart steht er dort absichtlich nicht mehr und wird nicht adoptiert: seine
     * Ursprungszone und sein Setzzeitpunkt waeren nicht ehrlich rekonstruierbar.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (MobKindTag.isOurs(entity) && !registry.holds(entity.getUniqueId())) {
                placer.remove(entity);
                logger.info(
                        "[mob] removed tagged entity without registry entry on chunk load: "
                                + entity.getUniqueId());
            }
        }
    }

    /**
     * Haelt den Bestand ehrlich, wenn eine Kreatur auf einem fremden Weg verschwindet.
     *
     * <p>Jede Entfernung ohne Tod ist ein Aufraeumen im Sinn von FR-034 - egal ob durch {@link
     * #removeEntity}, durch {@link #shutdown}, oder auf einem Weg, den dieser Block nicht selbst
     * ausgeloest hat. War die entfernte Kreatur ein Boss, bleibt sein Timer unberuehrt: er ist
     * nicht gefallen (T083).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        HordeRegistry.Entry entry = registry.remove(entity.getUniqueId());
        if (entry != null && event.getCause() != EntityRemoveEvent.Cause.DEATH) {
            markBossIfNeeded(entry, BossState::cleanedUp);
        }
    }

    /**
     * Setzt den Respawn-Timer, wenn die gestorbene Kreatur ein Boss war - dasselbe Todesereignis
     * wie jede andere Kreatur, keine Sonderbehandlung im Zuschlagen selbst (T082, FR-031).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        HordeRegistry.Entry entry = registry.find(event.getEntity().getUniqueId());
        if (entry != null) {
            markBossIfNeeded(entry, state -> state.killed(Instant.now(clock)));
        }
    }

    private void markBossIfNeeded(HordeRegistry.Entry entry, java.util.function.Consumer<BossState> action) {
        config.get()
                .kind(entry.kindKey())
                .filter(MobKind::boss)
                .ifPresent(kind -> action.accept(bossStates.computeIfAbsent(entry.zoneKey(), BossState::new)));
    }
}

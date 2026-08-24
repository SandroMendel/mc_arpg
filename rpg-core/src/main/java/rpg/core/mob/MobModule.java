package rpg.core.mob;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.message.Messages;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zones;

/**
 * Verdrahtet B10 in den Server (B01s Modulvertrag).
 *
 * <p><b>Nach B09.</b> Die Bereiche müssen stehen, bevor dieser Block sie füllt — und die Prüfung,
 * dass jeder in {@code mobs.yml} genannte Bereich wirklich existiert, geht sonst ins Leere.
 *
 * <p><b>Ohne Persistenz.</b> Der erste Block seit B03, für den das gilt. Eine Kreatur überlebt
 * keinen Neustart (FR-023), also gibt es nichts zu migrieren und nichts zu laden — nur eine
 * Konfiguration und einen Bestand, der leer beginnt.
 *
 * <p><b>Zwei Prüfungen laufen erst hier und nicht im Schema.</b> Ob ein Bereichsschlüssel in
 * {@code zones.yml} existiert, weiß nur B09; ob jede Art einen Anzeigenamen hat, nur
 * {@code messages.yml}. Beides bricht den Start genauso ab wie ein Schemafehler — der Unterschied
 * ist nur, woher die Antwort kommt. Ein Bereich, den es nicht gibt, wäre sonst eine still leere
 * Horde, und ein fehlender Name eine Kreatur, die als {@code mob.x.name} durch die Welt läuft.
 */
public final class MobModule implements Module {

    /** Stabile Kennung, unabhängig vom Namen dieser Klasse (B01/FR-001a). */
    public static final String ID = "mob";

    private static final String CONFIG_FILE = "mobs.yml";

    private final Logger logger;
    private final Messages messages;
    private final java.util.function.Supplier<Zones> zones;
    private final HordeRegistry registry = new HordeRegistry();
    private final Map<String, BossState> bosses = new HashMap<>();
    private final List<Runnable> reloadListeners = new ArrayList<>();

    private ConfigHandle<MobConfig> configHandle;

    public MobModule(Logger logger, Messages messages, java.util.function.Supplier<Zones> zones) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of("zone");
    }

    @Override
    public void start(ModuleContext context) {
        this.configHandle = loadConfig(context);
        MobConfig config = configHandle.get();
        verifyAreasExist(config);
        verifyNamesExist(config);
        for (String zoneKey : config.hordes().keySet()) {
            bosses.put(zoneKey, new BossState(zoneKey));
        }
        logger.info(
                "[mob] phase=START state=LOADED - "
                        + config.kinds().size()
                        + " kinds ("
                        + countBosses(config)
                        + " bosses) across "
                        + config.hordes().size()
                        + " zones, budget "
                        + config.budget().serverWide()
                        + " server-wide / "
                        + config.budget().perZone()
                        + " per zone");
    }

    @Override
    public void stop() {
        // Die Entitaeten selbst raeumt die Plattformschicht ab (FR-023); hier faellt nur der
        // Bestand. Andersherum stuende beim naechsten Start eine Zaehlung ohne Kreaturen.
        registry.clear();
        bosses.clear();
    }

    /**
     * Jeder genannte Bereich muss es in {@code zones.yml} geben (FR-011).
     *
     * <p>Kann nicht im Schema stehen: dort ist B09 nicht bekannt. Und kann nicht warten: ein
     * Bereichsschlüssel mit Tippfehler wäre eine Horde, die nie erscheint, und das sieht aus wie ein
     * kaputter Spawn statt wie ein kaputter Buchstabe.
     */
    private void verifyAreasExist(MobConfig config) {
        Zones known = zones.get();
        List<String> missing = new ArrayList<>();
        for (HordeSpec horde : config.hordes().values()) {
            List<String> areaKeys = areaKeysOf(known, horde.zoneKey());
            if (areaKeys.isEmpty()) {
                missing.add(horde.zoneKey() + " (no such zone, or it has no spawn areas)");
                continue;
            }
            for (HordeSpec.Entry entry : horde.entries()) {
                if (!areaKeys.contains(entry.areaKey())) {
                    missing.add(horde.zoneKey() + "." + entry.areaKey());
                }
            }
            if (horde.boss() != null && !areaKeys.contains(horde.boss().areaKey())) {
                missing.add(horde.zoneKey() + ".boss -> " + horde.boss().areaKey());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    CONFIG_FILE
                            + " names spawn areas the zone configuration does not have: "
                            + missing
                            + " (FR-011). The geometry lives in B09; this block only points at it."
                            + " Check the spawn-areas of those regions.");
        }
    }

    private static List<String> areaKeysOf(Zones zones, String zoneKey) {
        List<String> keys = new ArrayList<>();
        for (SpawnArea area : zones.spawnAreasOf(zoneKey)) {
            keys.add(area.key());
        }
        return keys;
    }

    /** Jede Art braucht einen Namen in {@code messages.yml} (FR-010). */
    private void verifyNamesExist(MobConfig config) {
        List<String> missing = new ArrayList<>();
        for (String kindKey : config.kinds().keySet()) {
            if (!messages.contains(MobMessageKeys.nameOf(kindKey))) {
                missing.add(MobMessageKeys.nameOf(kindKey).value());
            }
        }
        if (!messages.contains(MobMessageKeys.NAMEPLATE)) {
            missing.add(MobMessageKeys.NAMEPLATE.value());
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    CONFIG_FILE
                            + " declares mob kinds without a name in messages.yml: "
                            + missing
                            + " (FR-010). A creature name is a player text and lives only there.");
        }
    }

    private static long countBosses(MobConfig config) {
        return config.kinds().values().stream().filter(MobKind::boss).count();
    }

    /** Die aktuelle Konfiguration. Nie {@code null} nach {@link #start}. */
    public MobConfig config() {
        return configHandle.get();
    }

    /** Der Bestand. Die Plattformschicht trägt hier ein und aus. */
    public HordeRegistry registry() {
        return registry;
    }

    /** Der Bosszustand je Region. */
    public Map<String, BossState> bosses() {
        return bosses;
    }

    /** Die öffentliche Artabfrage (contracts/mob-api.md §2). */
    public MobKinds kinds() {
        return MobKinds.backedBy(this::config, registry);
    }

    /** Die öffentliche Hordenabfrage (contracts/mob-api.md §3). */
    public Hordes hordes() {
        return Hordes.backedBy(registry, bosses);
    }

    /** Etwas, das von einer neu geladenen Konfiguration erfahren muss. */
    public void onReload(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Nimmt eine neu geladene {@code mobs.yml} auf. Eine abgelehnte erreicht das hier nie (FR-014).
     *
     * <p><b>Neues gilt für Neues, Laufendes behält, womit es gestartet ist.</b> Bereits stehende
     * Kreaturen werden nicht umgerechnet — das wäre die einzige Alternative, und sie würde einem
     * Spieler die Zahlen mitten im Kampf unter den Händen verändern. Was sofort greift: Budgets,
     * Raten, Fristen, und welche Arten <em>ab jetzt</em> gesetzt werden.
     */
    public void applyReloadedConfig() {
        if (configHandle == null) {
            return;
        }
        MobConfig config = configHandle.get();
        verifyAreasExist(config);
        verifyNamesExist(config);
        for (String zoneKey : config.hordes().keySet()) {
            bosses.computeIfAbsent(zoneKey, BossState::new);
        }
        logger.info(
                "[mob] phase=RELOAD state=APPLIED - "
                        + config.kinds().size()
                        + " kinds, "
                        + registry.total()
                        + " creatures keep the values they were placed with");
        for (Runnable listener : reloadListeners) {
            listener.run();
        }
    }

    private ConfigHandle<MobConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), MobConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[mob] configuration rejected", invalid);
            throw new IllegalStateException(
                    "mob configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}

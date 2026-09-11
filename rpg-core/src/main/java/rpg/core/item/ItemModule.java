package rpg.core.item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.zone.Zone;
import rpg.core.zone.Zones;

/**
 * Verdrahtet B11 in den Server (B01s Modulvertrag).
 *
 * <p><b>Nach B09 und B10.</b> Beutetabellen nennen Regionen und Arten, Händler stehen in
 * Safe-Cores — die Prüfung, dass es beides wirklich gibt, geht sonst ins Leere.
 *
 * <p><b>Drei Prüfungen laufen erst hier und nicht im Schema.</b> Ob ein Zonenschlüssel in
 * {@code zones.yml} existiert, weiß nur B09; ob eine Art in {@code mobs.yml} existiert, nur B10; ob
 * jede Vorlage einen Anzeigenamen hat, nur {@code messages.yml}. Alle drei brechen den Start
 * genauso ab wie ein Schemafehler — der Unterschied ist nur, woher die Antwort kommt. Dieselbe
 * Aufteilung, die {@code MobModule} trifft, und aus demselben Grund: eine Region mit Tippfehler
 * wäre eine still leere Beutetabelle, und ein fehlender Name ein Trank, der als
 * {@code item.potion.minor-healing.name} durchs Inventar läuft.
 *
 * <p><b>Ohne eigene Persistenz für Gegenstände.</b> Ein Item lebt im PDC innerhalb von B03s
 * Inventar-Blob (FR-004) — es gibt keine Item-Tabelle und keinen Item-Aggregattyp. Was dieser Block
 * persistiert, sind zwei <em>Charakterwerte</em>: der Ausrüstungszustand und der Kosmetikbesitz.
 * Beide kommen mit ihren eigenen Aufgaben, nicht mit dieser.
 */
public final class ItemModule implements Module, Items {

    /** Stabile Kennung, unabhängig vom Namen dieser Klasse (B01/FR-001a). */
    public static final String ID = "item";

    private static final String CONFIG_FILE = "items.yml";

    private final Logger logger;
    private final Messages messages;
    private final Supplier<Zones> zones;
    private final Supplier<Collection<String>> mobKindKeys;
    private final List<Runnable> reloadListeners = new ArrayList<>();

    private ConfigHandle<ItemConfig> configHandle;

    /**
     * @param mobKindKeys jede in {@code mobs.yml} konfigurierte Art — B10 besitzt die Liste, dieser
     *     Block prüft nur gegen sie
     */
    public ItemModule(
            Logger logger,
            Messages messages,
            Supplier<Zones> zones,
            Supplier<Collection<String>> mobKindKeys) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.mobKindKeys = Objects.requireNonNull(mobKindKeys, "mobKindKeys");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of("zone", "mob");
    }

    @Override
    public void start(ModuleContext context) {
        this.configHandle = loadConfig(context);
        ItemConfig config = configHandle.get();

        verifyZonesExist(config);
        verifyKindsExist(config);
        verifyNamesExist(config);

        logger.info(
                "[item] phase=START state=LOADED - "
                        + config.templates().size()
                        + " templates ("
                        + count(config, ItemCategory.CONSUMABLE)
                        + " consumable, "
                        + count(config, ItemCategory.COSMETIC)
                        + " cosmetic), "
                        + config.loot().referencedTemplates().size()
                        + " referenced by loot tables, "
                        + config.vendors().size()
                        + " vendors, wear floor "
                        + config.wear().floor()
                        + " below "
                        + config.wear().threshold());
    }

    @Override
    public void stop() {
        reloadListeners.clear();
    }

    private ConfigHandle<ItemConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), ItemConfigSchema.schema());
        } catch (ConfigValidationException failure) {
            // Fail-Fast: ein Server, der mit einer halb verstandenen Konfiguration startet, ist
            // schlimmer als einer, der es nicht tut (Prinzip V).
            throw new IllegalStateException(
                    CONFIG_FILE + " is not valid: " + failure.getMessage(), failure);
        }
    }

    /** Jede genannte Region muss es in {@code zones.yml} geben. */
    private void verifyZonesExist(ItemConfig config) {
        Zones known = zones.get();
        List<String> knownKeys = new ArrayList<>();
        for (Zone zone : known.all()) {
            knownKeys.add(zone.key());
        }

        List<String> missing = new ArrayList<>();
        for (String zoneKey : config.loot().byZone().keySet()) {
            if (!knownKeys.contains(zoneKey)) {
                missing.add("loot.by-zone." + zoneKey);
            }
        }
        for (String zoneKey : config.vendors().keySet()) {
            if (!knownKeys.contains(zoneKey)) {
                missing.add("vendors." + zoneKey);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    CONFIG_FILE
                            + " names regions the zone configuration does not have: "
                            + missing
                            + ". The regions live in B09; this block only points at them. Known: "
                            + knownKeys);
        }
    }

    /** Jede genannte Art muss es in {@code mobs.yml} geben. */
    private void verifyKindsExist(ItemConfig config) {
        Collection<String> known = mobKindKeys.get();
        List<String> missing = new ArrayList<>();
        for (String kindKey : config.loot().byKind().keySet()) {
            if (!known.contains(kindKey)) {
                missing.add("loot.by-kind." + kindKey);
            }
        }
        for (String bossKey : config.loot().byBoss().keySet()) {
            if (!known.contains(bossKey)) {
                missing.add("loot.by-boss." + bossKey);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    CONFIG_FILE
                            + " names mob kinds the mob configuration does not have: "
                            + missing
                            + ". A kind with a typo would be a loot table that never fires, and"
                            + " that looks like broken loot instead of a broken letter");
        }
    }

    /** Jede Vorlage und jede Raritätsstufe braucht einen Namen in {@code messages.yml}. */
    private void verifyNamesExist(ItemConfig config) {
        List<String> missing = new ArrayList<>();
        for (MessageKey key : ItemMessageKeys.all(config.templates().keySet())) {
            if (!messages.contains(key)) {
                missing.add(key.value());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    CONFIG_FILE
                            + " declares items without a name in messages.yml: "
                            + missing
                            + ". An item name is a player text and lives only there (Principle V)");
        }
    }

    private static long count(ItemConfig config, ItemCategory category) {
        long total = 0;
        for (ItemTemplate template : config.templates().values()) {
            if (template.category() == category) {
                total++;
            }
        }
        return total;
    }

    // -------------------------------------------------------------------------------------
    // Items - die oeffentliche Fassade
    // -------------------------------------------------------------------------------------

    /**
     * Die aktuell gültige Konfiguration.
     *
     * <p>Wird bei jedem Zugriff neu gelesen, nicht zwischengespeichert: nach einem Nachladen zeigt
     * der Handle auf das neue Dokument, und ein festgehaltener Verweis wäre die alte Fassung
     * (FR-003).
     */
    public ItemConfig config() {
        if (configHandle == null) {
            throw new IllegalStateException("ItemModule.start has not run yet");
        }
        return configHandle.get();
    }

    @Override
    public Optional<ItemTemplate> template(String templateKey) {
        return config().template(templateKey);
    }

    @Override
    public Collection<String> templateKeys() {
        return config().templates().keySet();
    }

    @Override
    public OptionalLong sellPriceOf(String templateKey) {
        return template(templateKey)
                .map(ItemTemplate::sellPriceOrNone)
                .orElseGet(OptionalLong::empty);
    }

    @Override
    public WearCurve wear() {
        return config().wear();
    }

    /** Wer beim Nachladen benachrichtigt werden will — dieselbe Bauart wie {@code ZoneModule}. */
    public void onReload(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Vom Plugin nach einem erfolgreichen {@code reloadAll()} aufgerufen.
     *
     * <p>Hieß bis B14 {@code notifyReloaded()} — als einziges der sechs Module. Wer die Reihe
     * abarbeitete, übersah damit ausgerechnet das Modul, dessen Haken zusätzlich Zonen, Arten und
     * Namen prüft. Umbenannt statt dokumentiert (T004, research.md §5).
     */
    public void applyReloadedConfig() {
        ItemConfig config = config();
        verifyZonesExist(config);
        verifyKindsExist(config);
        verifyNamesExist(config);
        for (Runnable listener : reloadListeners) {
            listener.run();
        }
    }

    /** Nur für Tests: eine Konfiguration ohne Loader setzen. */
    static ItemModule withConfig(
            Logger logger,
            Messages messages,
            Supplier<Zones> zones,
            Supplier<Collection<String>> kinds,
            ConfigHandle<ItemConfig> handle) {
        ItemModule module = new ItemModule(logger, messages, zones, kinds);
        module.configHandle = handle;
        return module;
    }

    /** Nur für Tests: die drei Startprüfungen ohne Loader auslösen. */
    void verifyAll() {
        ItemConfig config = config();
        verifyZonesExist(config);
        verifyKindsExist(config);
        verifyNamesExist(config);
    }

    /** Für die Startprüfung des Plugins: jede Vorlagenkennung, wie sie geladen wurde. */
    public Map<String, ItemTemplate> templates() {
        return config().templates();
    }
}

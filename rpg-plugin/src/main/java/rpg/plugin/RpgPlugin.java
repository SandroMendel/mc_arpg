package rpg.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

import rpg.core.ability.AbilityMessageKeys;
import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassRegistry;
import rpg.core.combat.CombatMessageKeys;
import rpg.core.combat.CombatModule;
import rpg.core.combat.CombatPipeline;
import rpg.core.config.ConfigLoader;
import rpg.core.config.ConfigValidationException;
import rpg.core.currency.CurrencyMessageKeys;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.MessageKeyValidator;
import rpg.core.message.Messages;
import rpg.core.module.BootstrapState;
import rpg.core.module.DefaultModuleRegistry;
import rpg.core.module.Module;
import rpg.core.module.ModuleBootstrap;
import rpg.core.progression.DefaultProgression;
import rpg.core.progression.PartyRegistry;
import rpg.core.progression.ProgressionMessageKeys;
import rpg.core.progression.XpDistributor;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.SessionMessageKeys;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatEngine;
import rpg.core.zone.ZoneMessageKeys;
import rpg.core.zone.ZoneModule;
import rpg.persistence.PersistenceMessageKeys;
import rpg.persistence.PersistenceModule;
import rpg.persistence.ability.AbilityModule;
import rpg.persistence.classes.ClassesModule;
import rpg.persistence.currency.CurrencyModule;
import rpg.persistence.inventory.InventoryModule;
import rpg.persistence.progression.ProgressionModule;
import rpg.persistence.session.SessionModule;
import rpg.persistence.stats.StatsModule;
import rpg.platform.PlatformMessageKeys;
import rpg.platform.PreJoinGuard;
import rpg.platform.classes.BoundItemFactory;
import rpg.platform.classes.ClassEquipmentApplier;
import rpg.platform.classes.ClassSelectionListener;
import rpg.platform.classes.ClassSelectionMenu;
import rpg.platform.classes.EquipmentLockListener;
import rpg.platform.classes.InventoryFullNoticeListener;
import rpg.platform.classes.NoCharacterGuardListener;
import rpg.platform.classes.PaperClassNotice;
import rpg.platform.classes.SelectionTimeout;
import rpg.platform.combat.CombatDeathListener;
import rpg.platform.combat.MobEquipmentListener;
import rpg.platform.combat.PaperDamageFeedback;
import rpg.platform.combat.PaperMobStatProvider;
import rpg.platform.combat.ProjectileCombatListener;
import rpg.platform.combat.ProjectileDamageTag;
import rpg.platform.combat.VanillaDamageListener;
import rpg.platform.combat.VanillaDamageMapping;
import rpg.platform.config.YamlConfigLoader;
import rpg.platform.hud.StatusActionBar;
import rpg.platform.progression.ExperienceBar;
import rpg.platform.progression.PaperProximityCheck;
import rpg.platform.progression.ProgressionDeathListener;
import rpg.platform.scheduler.PaperSchedulerAdapter;
import rpg.platform.session.PendingSessionStash;
import rpg.platform.session.SafeStateGuard;
import rpg.platform.session.SessionConnectionCloseListener;
import rpg.platform.session.SessionJoinListener;
import rpg.platform.session.SessionObserver;
import rpg.platform.session.SessionPreLoadListener;
import rpg.platform.session.SessionQuitListener;
import rpg.platform.stats.PaperVanillaAttributeBridge;
import rpg.platform.stats.VanillaRegenerationGuard;
import rpg.platform.zone.BukkitPositions;

/**
 * Plugin entry point: wires the five modules together and hands control to
 * {@link ModuleBootstrap}.
 *
 * <p>This class stays deliberately thin. Every rule worth testing - start order, fail-fast, the 10
 * second shutdown budget, config validation and rollback - lives in {@code rpg-core} and is covered
 * by server-free unit tests (Constitution VII.1). What is left here is exactly the part that needs a
 * running server: obtaining the Paper schedulers, registering the listener and reporting failure to
 * the server.
 */
public class RpgPlugin extends JavaPlugin {

    /** Bootstrap budget from plugin enable to readiness for the first join (SC-001, FR-013). */
    private static final Duration BOOTSTRAP_BUDGET = Duration.ofSeconds(30);

    private static final String MESSAGES_FILE = "messages.yml";

    /**
     * How often every online player's inventory is written down.
     *
     * <p>Bounds what a crash costs. Deliberately the same order as B02's autosave: a tighter interval
     * would serialise 41 slots per player more often for a smaller window than everything else already
     * accepts, and a wider one would make the inventory the weakest link.
     */
    private static final Duration INVENTORY_SWEEP = Duration.ofSeconds(45);

    /**
     * Configuration files written out on first start.
     *
     * <p>A module whose file is missing refuses to start, which is the right behaviour for a running
     * server and the wrong first impression for an operator who just dropped the jar in. Shipping a
     * default of each means the first start works and the file is there to be edited.
     */
    private static final List<String> DEFAULT_CONFIG_FILES =
            List.of(
                    "persistence.yml",
                    "session.yml",
                    "stats.yml",
                    "combat.yml",
                    "progression.yml",
                    "classes.yml",
                    "abilities.yml",
                    "currency.yml",
                    "zones.yml",
                    "mobs.yml",
                    "items.yml",
                    "statistics.yml",
                    "ui.yml");

    private final BootstrapState bootstrapState = new BootstrapState();

    /**
     * Puts a character into play. Held as a field because two callers need it: the class selection,
     * and {@link #enterCharacter} - see there for why the second one exists.
     */
    private rpg.platform.classes.CharacterEntry characterEntry;

    /** The puff and the click that tell a player their ability took hold. */
    private rpg.platform.ability.AbilityFeedback abilityFeedback;

    /**
     * The counter behind Rage. Held because the action bar draws it, and only this class sees both
     * the effect that keeps it and the readout that wants it.
     */
    private rpg.core.ability.effect.MeterEffect abilityMeter;

    private DefaultModuleRegistry registry;
    private EventBus eventBus;
    private Scheduler scheduler;
    private ConfigLoader configLoader;
    private Messages messages;
    private ModuleBootstrap bootstrap;
    private PersistenceModule persistenceModule;
    private SessionModule sessionModule;
    private StatsModule statsModule;
    private CombatModule combatModule;
    private ProgressionModule progressionModule;
    private CurrencyModule currencyModule;
    private ZoneModule zoneModule;
    private rpg.core.mob.MobModule mobModule;
    private rpg.core.item.ItemModule itemModule;
    private rpg.core.statistics.StatisticsModule statisticsModule;
    private rpg.core.ui.UiModule uiModule;

    /**
     * Was B13 je Spieler hält und beim Sitzungsende loswerden muss (FR-004c).
     *
     * <p>Als Liste von Aufräumern statt als vier Felder: sie entstehen an zwei verschiedenen
     * Stellen der Verdrahtung (der HUD in der Kampfschicht, das Fenster nach der Item-Schicht), und
     * eine Liste lässt beide dieselbe Zusage erfüllen, ohne dass {@code onSessionEnded} von der
     * Reihenfolge wüsste.
     *
     * <p><b>Kein {@code PlayerQuitEvent}-Handler in B13</b>: B03 besitzt den Lebenszyklus und lässt
     * genau einen zu (FR-007). Der vorgesehene Weg hinein ist der {@code SessionObserver}.
     */
    private final List<java.util.function.Consumer<java.util.UUID>> uiForgetters =
            new java.util.ArrayList<>();

    /**
     * Was B13 beim Betreten einer Sitzung wiederherstellen muss.
     *
     * <p>Heute genau eines: die <b>verbleibende</b> Cooldown-Anzeige (FR-033). Über den
     * {@code SessionObserver} und nicht über {@code PlayerJoinEvent} — B03 lässt dort genau einen
     * Handler zu (FR-007), und der Beitritt allein reichte ohnehin nicht: erst mit der fertigen
     * Sitzung steht fest, welcher Charakter gespielt wird.
     */
    private final List<java.util.function.Consumer<java.util.UUID>> uiOnJoin =
            new java.util.ArrayList<>();

    /**
     * Was B13 tun muss, wenn eine Fähigkeit <b>erfolgreich</b> ausgelöst wurde.
     *
     * <p>Heute genau eines: das Cooldown-Overlay über den Slot legen (FR-030). B08 veröffentlicht
     * dafür kein Ereignis — {@code AbilityRuntime.startCooldown} ist privat —, und die
     * Trigger-Stelle im Plugin ist die einzige, die vom Erfolg weiß.
     *
     * <p><b>Nicht über den HUD-Takt</b>: ein Cooldown, der erst beim nächsten Durchlauf grau wird,
     * ist bis zu eine Sekunde zu spät. Genau in dieser Sekunde drückt ein Spieler ein zweites Mal
     * und hält die Ablehnung für einen Fehler des Servers.
     */
    private final List<
                    java.util.function.BiConsumer<java.util.UUID, rpg.core.ability.Ability>>
            uiOnAbilityUsed = new java.util.ArrayList<>();
    private rpg.platform.statistics.PlaytimeAccrual playtimeAccrual;
    private rpg.core.statistics.LeaderboardCache leaderboardCache;
    private rpg.persistence.statistics.LeaderboardFill leaderboardFill;

    /** B12s Saisonabschluss — beim Start nachgeholt und im Auffrischungstakt mitgeführt (FR-058). */
    private rpg.persistence.statistics.SeasonClosingJob seasonClosing;

    /** B12s Anzeige im Hub, sofern eine konfiguriert und ihre Welt geladen ist (FR-064). */
    private rpg.platform.statistics.LeaderboardHologram leaderboardHologram;

    /**
     * Wo die Anzeige steht — gebraucht, um sie auf ihrem <b>eigenen</b> Tick neu zu beschriften.
     *
     * <p>Der Auffrischungstakt läuft asynchron; eine Entität von dort aus anzufassen ist der
     * Fehler, der auf Folia gar nicht und auf Paper nur meistens auffällt.
     */
    private rpg.core.scheduler.WorldPosition hologramAt;

    private rpg.platform.statistics.StatisticsMenuListener statisticsMenus;

    /**
     * B11s Vermerk über liegende Beute — gesetzt in {@link #assembleItemLayer()}.
     *
     * <p>Gebraucht wird er im Rückruf für den Charaktereintritt, der weiter oben in der
     * Zusammenstellung sitzt. Deshalb ein Feld und keine lokale Variable; {@code null} heißt
     * schlicht, dass B11 noch nicht verdrahtet ist.
     */
    private rpg.platform.drop.OwnedDropRegistry itemDropVisibility;

    /**
     * B11s zeitliche Trankwirkungen — gesetzt in {@link #wireConsumables}.
     *
     * <p>Feld, weil der Durchlauf, der sie ablaufen lässt, weiter oben sitzt: es ist <b>derselbe</b>,
     * der B08s Buffs ablaufen lässt, und genau das ist die Zusage aus FR-034.
     */
    private rpg.core.item.ConsumableBuffs consumableBuffs;

    /**
     * Das Cooldown-Overlay (B13 US3) — ein Feld, weil es in <b>zwei</b> Schritten fertig wird.
     *
     * <p>{@code wireUi} baut es und hängt es in den Takt; die Trankschicht kommt erst in
     * {@code wireConsumables}, weit später im Start. Ein lokales Ding wäre bis dahin weg.
     */
    private rpg.platform.ui.AbilityCooldownOverlay cooldownOverlay;

    /** Wer gerade ein Klon ist — B10s Liste, von B11 mitgelesen (FR-041a). */
    private rpg.platform.mob.CloneAggroListener cloneRegistry;

    /** B07s Warnung bei vollem Inventar — mit B11s konfigurierbarer Ruhezeit (US7). */
    private InventoryFullNoticeListener inventoryFullNotice;

    /** B11s Anzeige des Verschleisses — Balken und Lore auf der getragenen Ausruestung (US5). */
    private rpg.platform.item.GearConditionDisplay gearDisplay;

    /** B11s Mülleimer — der dritte Entsorgungsweg (US7). */
    private rpg.platform.item.TrashCommand trashCommand;

    /** B11s Trimfarben — Datenbankseite und Sitzungsgrenzen (US6). */
    private rpg.persistence.item.CosmeticModule cosmeticModule;

    /** B11s Verschleisszustand — Datenbankseite und Sitzungsgrenzen (US5). */
    private rpg.persistence.item.GearConditionModule gearConditionModule;

    /** B11s Händler — der Zuhörer und die gesetzten NPCs (US4). */
    private rpg.platform.item.VendorListener vendorListener;

    private rpg.platform.item.VendorNpc vendorNpcs;

    /**
     * Wie weit neben dem Ankunftspunkt der Händler steht.
     *
     * <p>Zwei Blöcke: nah genug, um ihn beim Ankommen zu sehen, weit genug, um nicht in ihm zu
     * stehen.
     */
    private static final double VENDOR_OFFSET = 2.0;
    /** Der selbst neu eingeplante Durchlauf je bevoelkerter Zone (B10, US2/US3). */
    private rpg.platform.mob.HordeSweep mobSweep;
    private rpg.persistence.zone.ZonePersistenceModule zonePersistenceModule;
    private rpg.core.zone.ZoneTracker zoneTracker;
    /** Moves a player. Held because US6 travel needs the same one the respawn path uses. */
    private rpg.core.zone.Teleporter zoneTeleporter;
    private rpg.core.zone.RespawnRouting zoneRespawnRouting;
    private rpg.core.zone.CombatLogoutRule zoneCombatLogout;
    /** Haelt B10 die Tuer offen, um den Klon-Aggro-Umlenker einzuhaengen (B10, US7). */
    private rpg.core.ability.effect.SummonEffect summonEffect;
    /** Cleans up after a character that left - the level-band guard's repeat block. */
    private java.util.function.Consumer<java.util.UUID> zoneForget = characterId -> {};

    private rpg.core.zone.Travel zoneTravel;

    /** Wer gerade in der Luft ist und beim Aufkommen noch etwas ausloest (FR-045d). */
    private rpg.platform.ability.LandingWatcher abilityLandings;

    /** The same for the two player-keyed maps of US6: the click cooldown and the open window. */
    private java.util.function.Consumer<java.util.UUID> zoneForgetPlayer = playerId -> {};
    private ClassesModule classesModule;
    private AbilityModule abilityModule;
    private rpg.core.ability.AbilityRuntime abilityRuntime;
    private rpg.platform.ability.AbilityHotbar abilityHotbar;
    private InventoryModule inventoryModule;
    private ExperienceBar experienceBar;

    @Override
    public void onEnable() {
        long startedAt = System.nanoTime();

        registry = new DefaultModuleRegistry();
        eventBus = new DefaultEventBus(getLogger());
        scheduler = new PaperSchedulerAdapter(this, getServer(), getLogger());
        YamlConfigLoader yamlLoader = new YamlConfigLoader(getDataFolder().toPath());
        configLoader = yamlLoader;

        // Defaults before anything reads them; saveResource leaves an existing file alone, so an
        // operator's edits survive every restart and every update.
        for (String file : DEFAULT_CONFIG_FILES) {
            if (!Files.exists(getDataFolder().toPath().resolve(file))) {
                saveResource(file, false);
            }
        }

        // Messages before anything else: the pre-login guard needs them, and a missing text must
        // stop the start rather than surface later as a blank kick screen (FR-023a).
        //
        // Die Sprache wird VOR dem try aufgeloest, damit die Abbruchmeldung die Datei nennen kann,
        // die wirklich gelesen wurde (FR-018). Vorher stand hier fest "messages.yml" - bei
        // language: de schickte das den Betreiber in die englische Vorlage, in der nichts fehlt.
        rpg.core.ui.LanguageSet language = configuredLanguage(yamlLoader);
        try {
            messages = loadMessages(yamlLoader, language);
        } catch (RuntimeException | ConfigValidationException failure) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            "RPG bootstrap failed - " + language.file() + " is unusable",
                            failure);
            bootstrapState.markFailed(language.file() + " is unusable: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bootstrap =
                new ModuleBootstrap(
                        registry, eventBus, scheduler, configLoader, bootstrapState, getLogger());

        // Refuse joins from the very first moment: the guard has to be live *before* the modules
        // start, otherwise the race it exists to close is still open during bootstrap (FR-013).
        getServer()
                .getPluginManager()
                .registerEvents(new PreJoinGuard(bootstrapState, messages), this);

        for (Module module : modules()) {
            bootstrap.add(module);
        }

        try {
            bootstrap.start();
        } catch (RuntimeException failure) {
            // Fail-fast: never leave the server half-initialised and misbehaving later (FR-013).
            getLogger().log(Level.SEVERE, "RPG bootstrap failed - disabling the plugin", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Before the session listeners, because it produces the observer they carry: B07 has to hear
        // about a ready session, and B03 allows exactly one join handler (FR-007).
        // Before the class layer, because its session observer places characters into their zone and
        // therefore needs the tracker to exist. B09 may not listen for a join itself: B03 owns the
        // session lifecycle and allows exactly one handler (FR-007), and
        // NoCompetingSessionListenersTest enforces it. The observer is the sanctioned way in.
        assembleZoneLayer();
        SessionObserver classes = assembleClassLayer();
        registerSessionListeners(classes);
        assembleStatLayer();
        assembleCombatLayer();
        assembleProgressionLayer();
        assembleMobLayer();
        // B11 nach B10, und das ist keine Formalie: die Beutetabellen nennen Arten und Regionen,
        // und ohne sie waere die Zuordnung ins Leere gelaufen.
        assembleItemLayer();
        // B12 zuletzt: es beobachtet alle vorigen Bloecke und wird von keinem gebraucht.
        wireStatistics();
        // B13s Fenster nach B11: die Charakteruebersicht liest Ausruestung und Zustand, und beides
        // entsteht erst in der Item-Schicht. Der HUD-Takt haengt dagegen schon in der Kampfschicht -
        // er braucht nur, was B04, B05 und B06 fuehren.
        wireCharacterSheet();

        // Same cadence as B02's autosave, and for the same reason: a crash should cost one interval,
        // not a whole session's loot. The quit path captures on its own; this is only for the case
        // where there is no quit path.
        startInventorySweep(INVENTORY_SWEEP);

        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
        if (took.compareTo(BOOTSTRAP_BUDGET) > 0) {
            getLogger()
                    .warning(
                            "[bootstrap] took "
                                    + took.toMillis()
                                    + "ms, exceeding the "
                                    + BOOTSTRAP_BUDGET.toSeconds()
                                    + "s budget (SC-001)");
        } else {
            getLogger()
                    .info(
                            "[bootstrap] ready for players after "
                                    + took.toMillis()
                                    + "ms (budget "
                                    + BOOTSTRAP_BUDGET.toSeconds()
                                    + "s)");
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap == null) {
            return; // enable never got far enough to build one
        }
        // Vor dem Modul-Shutdown: die Plattformschicht raeumt die Entitaeten selbst weg, bevor
        // MobModule.stop() nur noch den Bestand leert (FR-023). Synchron, weil onDisable schon im
        // Tick laeuft - kein weiterer Umweg ueber den Scheduler noetig.
        if (mobSweep != null) {
            mobSweep.shutdown();
        }
        // Die offenen Haendlerfenster vergessen. Es haengt kein Vorgang daran - genau das ist die
        // Zusage aus FR-065 -, also ist das Vergessen alles, was zu tun ist.
        if (vendorListener != null) {
            vendorListener.clear();
        }
        if (vendorNpcs != null) {
            vendorNpcs.clear();
        }
        // Vergessen, nicht entfernen: die Anzeige ist persistent und soll es bleiben. Aufgeraeumt
        // wird beim naechsten Setzen (FR-061) - ein Absturz hat kein Herunterfahren.
        if (leaderboardHologram != null) {
            leaderboardHologram.clear();
        }
        // Bounded by 10s per module inside ModuleBootstrap (FR-012, SC-007): a module that hangs is
        // abandoned on a daemon thread instead of blocking the server's shutdown indefinitely.
        bootstrap.shutdown();
    }

    /**
     * Internal reload entry point (FR-003).
     *
     * <p>Global by design (clarification 2026-08-19): every module's configuration is reloaded at
     * once, and a rejected document leaves the previously valid configuration active for all of them
     * (FR-004). Exposed as a method rather than a command because the operator-facing
     * {@code /rpg reload} command belongs to B14; this is what that command will call.
     *
     * @return {@code true} if the new configuration was applied, {@code false} if it was rejected and
     *     the previous one stays active
     */
    public boolean reloadConfiguration() {
        try {
            configLoader.reloadAll();
            // Modules that keep derived state from their configuration have to be told. B04 does:
            // its engine holds the attribute definitions and has to mark every holder so the new
            // numbers actually take effect (User Story 7, scenario 4).
            if (statsModule != null) {
                statsModule.applyReloadedConfig();
            }
            // B09 holds a chunk index derived from zones.yml, so it has to be told as well: the
            // index is rebuilt and everyone present is re-evaluated once (FR-014, research.md R6).
            // One pass is not a recurring task - this block registers nothing with the scheduler.
            if (zoneModule != null) {
                zoneModule.applyReloadedConfig();
            }
            getLogger().info("[config] phase=RELOAD state=APPLIED - all modules reloaded");
            return true;
        } catch (ConfigValidationException rejected) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            "[config] phase=RELOAD state=REJECTED - keeping the previously valid"
                                    + " configuration: "
                                    + rejected.getMessage(),
                            rejected);
            return false;
        }
    }

    /**
     * Loads the configured language file and verifies every declared key has a text.
     *
     * <p>The default file is written out on first start so an operator has something to edit
     * instead of having to guess the keys.
     *
     * @param language welcher Satz gilt — bestimmt die gelesene Datei und den Namen, den jede
     *     Fehlermeldung dieses Pfades trägt (FR-018)
     */
    private Messages loadMessages(YamlConfigLoader loader, rpg.core.ui.LanguageSet language)
            throws ConfigValidationException {
        // Die ausgelieferte englische Datei liegt immer da - auch wenn eine andere Sprache gilt.
        // Ein Betreiber, der uebersetzt, braucht sie als Vorlage, und ohne sie muesste er die
        // Schluessel raten.
        Path shipped = getDataFolder().toPath().resolve(MESSAGES_FILE);
        if (!Files.exists(shipped)) {
            saveResource(MESSAGES_FILE, false);
        }

        // WELCHE Datei gelesen wird, entscheidet ui.yml (FR-016, FR-017). Sie wird DIREKT gelesen
        // und nicht ueber UiModule: die Texte muessen stehen, bevor irgendein Modul startet - der
        // Pre-Login-Guard braucht sie, und ein fehlender Text soll den Start abbrechen statt
        // spaeter als leerer Kick-Bildschirm aufzutauchen.
        //
        // Das ist der EINZIGE Griff dieses Blocks an eine Konfiguration ausserhalb seines Moduls,
        // und er ist so klein wie moeglich gehalten: ein Feld, kein Schema. Aufgeloest wird er von
        // onEnable, damit auch der Abbruchpfad dort den Dateinamen kennt.
        Path languageFile = getDataFolder().toPath().resolve(language.file());
        if (!Files.exists(languageFile)) {
            throw new IllegalStateException(
                    "ui.yml: language ist '"
                            + language.code()
                            + "', aber "
                            + language.file()
                            + " gibt es nicht im Plugin-Ordner. Lege die Datei an oder stelle"
                            + " language auf 'en' zurueck (FR-018)");
        }
        Messages loaded = MapMessages.fromNested(loader.readDocument(Path.of(language.file())));

        // Collect the keys every module can ask for. A block that adds player-facing text adds its
        // keys here, and the check below then covers it too.
        List<MessageKey> declared = new ArrayList<>(PlatformMessageKeys.all());
        declared.addAll(PersistenceMessageKeys.all());
        declared.addAll(SessionMessageKeys.all());
        declared.addAll(ProgressionMessageKeys.all());
        declared.addAll(ClassMessageKeys.all());
        declared.addAll(CombatMessageKeys.all());
        declared.addAll(AbilityMessageKeys.all());
        declared.addAll(CurrencyMessageKeys.all());
        // B09s per-zone name keys are NOT listed here: the zone keys are only known once
        // zones.yml has been read, so ZoneModule.start verifies them itself (FR-003c).
        declared.addAll(ZoneMessageKeys.all(List.of()));
        // Dieselbe Bauart fuer B10: die Artnamen sind erst nach mobs.yml bekannt, und
        // MobModule.start prueft sie selbst (verifyNamesExist). Hier steht nur der feste
        // Schluessel NAMEPLATE, damit auch er in der allgemeinen Liste steht.
        declared.addAll(rpg.core.mob.MobMessageKeys.all(List.of()));
        // B12 braucht die Ausnahme von B09 und B10 NICHT: seine Schluessel haengen an zwei
        // Verzeichnissen im Code (Aggregation, Period) und nicht an einer Konfigurationsdatei.
        // Sie stehen also schon vor dem ersten Lesen einer YAML fest und koennen hier vollstaendig
        // geprueft werden.
        declared.addAll(rpg.core.statistics.StatisticsMessageKeys.all());
        // B13 wie B12 und nicht wie B09/B10: seine Schluessel haengen an einer Aufzaehlung im Code
        // (Attribute aus B04) und nicht an einer Konfigurationsdatei, stehen also schon vor dem
        // ersten Lesen einer YAML fest.
        //
        // Ab hier ist diese Pruefung zugleich die Pruefung des SPRACHSATZES (FR-018): wer eine
        // zweite Sprache anlegt, bekommt beim Start die vollstaendige Liste dessen, was ihm fehlt -
        // MessageKeyValidator meldet ALLE Luecken auf einmal und nicht die erste. Genau das macht
        // eine Uebersetzung ueberhaupt machbar; bei einer Meldung je Startversuch gaebe man nach
        // dem zwanzigsten auf.
        declared.addAll(rpg.core.ui.UiMessageKeys.all());
        MessageKeyValidator.verifyAllPresent(loaded, declared, language.file());

        getLogger()
                .info(
                        "[messages] "
                                + declared.size()
                                + " declared key(s) resolved from "
                                + language.file());
        return loaded;
    }

    /**
     * Welche Sprache in {@code ui.yml} steht — gelesen, bevor irgendein Modul startet.
     *
     * <p><b>Ohne Schema und ohne {@code UiModule}</b>, und das ist Absicht: die Texte müssen vor der
     * ersten Anmeldung stehen, die Module kommen später. Ein halbes Schema hier wäre eine zweite
     * Vorstellung davon, was {@code ui.yml} ist — {@code UiConfigSchema} bleibt die einzige, die die
     * Datei wirklich prüft, und sie tut es beim Start des Moduls.
     *
     * <p>Fehlt die Datei oder das Feld, gilt Englisch. Das ist kein Fehler: beim allerersten Start
     * ist {@code ui.yml} gerade erst geschrieben worden, und der ausgelieferte Wert <em>ist</em>
     * {@code en}.
     */
    private rpg.core.ui.LanguageSet configuredLanguage(YamlConfigLoader loader) {
        try {
            Object hud = loader.readDocument(Path.of("ui.yml")).get("language");
            return hud == null
                    ? rpg.core.ui.LanguageSet.defaultSet()
                    : new rpg.core.ui.LanguageSet(String.valueOf(hud));
        } catch (RuntimeException | ConfigValidationException unreadable) {
            // Eine kaputte ui.yml bricht den Start ohnehin ab - aber in UiModule, mit der Meldung,
            // die Datei, Schluessel und Grund nennt. Hier waere eine zweite, schlechtere Meldung.
            getLogger()
                    .warning(
                            "[messages] ui.yml is not readable yet - falling back to English;"
                                    + " UiModule will report why: "
                                    + unreadable.getMessage());
            return rpg.core.ui.LanguageSet.defaultSet();
        }
    }

    /**
     * The modules making up this server.
     *
     * <p>B01 owns no module of its own - it is the foundation everything else is built against.
     * B02 and B03 add theirs here, and nowhere else, which is what keeps the wiring in one
     * reviewable place instead of spread across static initialisers. The start order follows from
     * the declared dependencies, not from this list.
     */
    private List<Module> modules() {
        persistenceModule = new PersistenceModule(getLogger(), Clock.systemUTC());
        sessionModule = new SessionModule(persistenceModule, getLogger(), Clock.systemUTC());
        statsModule = new StatsModule(persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        combatModule =
                new CombatModule(sessionModule.registry(), getLogger(), Clock.systemUTC());
        progressionModule =
                new ProgressionModule(
                        persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        // B11 US6. Eigenes Modul neben dem Verschleiss, weil es ein eigenes Aggregat ist - ein
        // Aggregat, ein Modul, ein Platz in der Schreibreihenfolge (ADR-015).
        cosmeticModule =
                new rpg.persistence.item.CosmeticModule(
                        persistenceModule,
                        sessionModule,
                        // Ein Lambda und KEINE Methodenreferenz: itemModule wird weiter unten
                        // gebaut, und itemModule::config wuerde hier sofort auf null binden.
                        () -> itemModule.config(),
                        // Ob die Hoechststufe erreicht ist, weiss B07 - eine zweite Antwort hier
                        // waere eine zweite Wahrheit (FR-079).
                        this::isAtTopTier,
                        eventBus,
                        getLogger(),
                        Clock.systemUTC());
        // B11s Verschleiss haengt VOR B07 ein (Complexity Tracking, research.md R1). Als Funktion
        // und nicht als Modul: die Antwort wird beim Aufruf aufgeloest, und die Startreihenfolge
        // bleibt frei. Solange B11 nicht laeuft, ist es GearConditionFactor.NONE - und B07
        // verhaelt sich exakt wie vorher.
        gearConditionModule =
                new rpg.persistence.item.GearConditionModule(
                        persistenceModule,
                        sessionModule,
                        () -> itemModule.wear(),
                        eventBus,
                        getLogger(),
                        Clock.systemUTC());
        classesModule =
                new ClassesModule(
                        persistenceModule,
                        sessionModule,
                        statsModule,
                        progressionModule,
                        getLogger(),
                        Clock.systemUTC(),
                        this::gearFactorOf);
        inventoryModule =
                new InventoryModule(
                        persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        // After the classes: the cross-check between the loadouts and the ability definitions needs
        // both configurations, and it is the promise B07 could not keep - there an ability id travels
        // as an opaque string because this block did not exist yet.
        abilityModule =
                new AbilityModule(
                        persistenceModule,
                        sessionModule,
                        classesModule,
                        getLogger(),
                        Clock.systemUTC());
        // Layer 1, and it depends on nothing but the session (ADR-027). That is what lets it close
        // B07 and B08 instead of queueing behind them.
        currencyModule =
                new CurrencyModule(persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        // B09. Layer 2, and it depends on nothing but B01 - the world resolver is the only thing it
        // needs from Paper, and it gets it as a function so `rpg-core` never sees a World
        // (Constitution III.1, FR-002a).
        zonePersistenceModule =
                new rpg.persistence.zone.ZonePersistenceModule(
                        persistenceModule, sessionModule, getLogger(), Clock.systemUTC());
        zoneModule = new ZoneModule(getLogger(), BukkitPositions.resolver(), messages);
        // B10. Nach B09, und das ist keine Formalie: die Spawn-Bereiche muessen stehen, bevor
        // dieser Block sie fuellt - und seine Startpruefung, dass jeder in mobs.yml genannte
        // Bereich wirklich existiert, ginge sonst ins Leere. Die Abhaengigkeit steht auch in
        // MobModule.dependencies(); die Reihenfolge hier ist die zweite Absicherung.
        mobModule = new rpg.core.mob.MobModule(getLogger(), messages, () -> zoneModule.zones());
        // B11. Nach B09 UND B10, aus demselben Grund wie B10 nach B09: die Beutetabellen nennen
        // Regionen und Arten, und die Startpruefung, dass es beide wirklich gibt, ginge sonst ins
        // Leere. Eine Art mit Tippfehler waere eine still leere Beutetabelle - und das sieht aus
        // wie kaputte Beute statt wie ein kaputter Buchstabe.
        itemModule =
                new rpg.core.item.ItemModule(
                        getLogger(),
                        messages,
                        () -> zoneModule.zones(),
                        () -> mobModule.kindKeys());
        // B12. Nach B11, weil die Belohnungen einer Saison Vorlagen-IDs nennen und die
        // Startpruefung sonst gegen eine leere Liste liefe - dieselbe Ueberlegung, aus der B11
        // nach B09 und B10 kommt. Eine Vorlage mit Tippfehler waere ein Anspruch, der erst am
        // Saisonende ins Leere greift, beim Spieler, der drei Monate dafuer gespielt hat.
        statisticsModule =
                new rpg.core.statistics.StatisticsModule(
                        getLogger(), () -> itemModule.config().templates().keySet());
        // B13. Der letzte der Kette: er liest zehn Bloecke und wird von keinem gebraucht. Er
        // deklariert trotzdem KEINE Abhaengigkeiten - was er liest, holt er zur Laufzeit ueber
        // deren oeffentliche Naehte, und seine eigene Konfiguration braucht beim Laden keinen
        // anderen Block. Eine Abhaengigkeit, die nur "spaeter mal" bedeutet, verengt die
        // Startreihenfolge ohne Gegenwert.
        uiModule =
                new rpg.core.ui.UiModule(
                        getLogger(),
                        // Ein Lambda und KEINE Methodenreferenz: abilityModule ist an dieser
                        // Stelle noch nicht gebaut, und eine Referenz wuerde sofort auf null
                        // binden. Dieselbe Falle, die CosmeticModule oben schon benennt.
                        () ->
                                characterClass ->
                                        abilityModule.registry().abilitiesOf(characterClass).stream()
                                                .map(rpg.core.ui.MaterialUniqueness.SlotUse::of)
                                                .toList());
        return List.of(
                persistenceModule,
                sessionModule,
                statsModule,
                combatModule,
                progressionModule,
                classesModule,
                inventoryModule,
                abilityModule,
                currencyModule,
                zonePersistenceModule,
                zoneModule,
                mobModule,
                itemModule,
                gearConditionModule,
                cosmeticModule,
                statisticsModule,
                uiModule);
    }

    /**
     * Assembles the Paper-facing half of B03.
     *
     * <p>This is the one place that sees both sides: the listeners live in {@code rpg-platform} and
     * know only {@code rpg-core} interfaces, the lifecycle they drive lives in
     * {@code rpg-persistence}. Neither module may depend on the other (Constitution III.2), so the
     * plugin does the introduction.
     *
     * <p>Registered <strong>after</strong> {@code bootstrap.start()} on purpose. A pre-login event
     * arriving while the modules are still coming up would find a lifecycle that is not there yet;
     * until this call, B01's {@code PreJoinGuard} is what answers those connections.
     */
    private void registerSessionListeners(SessionObserver observer) {
        SafeStateGuard safeState = new SafeStateGuard(getLogger());
        PendingSessionStash stash =
                new PendingSessionStash(
                        sessionModule.config().pendingExpiry(), Clock.systemUTC(), getLogger());

        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionPreLoadListener(
                                sessionModule.lifecycle(),
                                stash,
                                messages,
                                sessionModule.config().loadTimeout(),
                                persistenceModule::loginRefusalReason,
                                getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionJoinListener(
                                sessionModule.lifecycle(),
                                stash,
                                safeState,
                                observer,
                                getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionQuitListener(
                                sessionModule.lifecycle(), safeState, observer, getLogger()),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new SessionConnectionCloseListener(sessionModule.lifecycle(), stash), this);
        getServer().getPluginManager().registerEvents(safeState, this);

        // The sweep needs to know who is actually connected, which only the server can answer.
        sessionModule.startReconciliation(
                () ->
                        getServer().getOnlinePlayers().stream()
                                .map(org.bukkit.entity.Player::getUniqueId)
                                .toList(),
                stash::expireStale);
    }

    /**
     * Assembles the Paper-facing half of B09 (ADR-012).
     *
     * <p>Two listeners for now - movement and the session edges. The rest arrives with its own user
     * story: the damage rule with US3, death with US4, the combat logout with US5, and the waypoint
     * crystals with US6.
     *
     * <p><b>The reload pass is wired here and nowhere else.</b> {@code ZoneModule} rebuilds the index
     * and then tells whoever asked; the one thing that needs telling is the tracker, and it needs the
     * list of who is present - which only this layer can produce. One pass over the online players is
     * not a recurring task, and this block registers nothing with the scheduler at all
     * (Constitution II, research.md R6).
     */
    private void assembleZoneLayer() {
        zoneTracker =
                new rpg.core.zone.ZoneTracker(zoneModule::zones, eventBus, getLogger());
        java.util.function.Function<org.bukkit.entity.Player, java.util.UUID> characters =
                player -> characterIdOf(player).orElse(null);

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.zone.ZoneMovementListener(
                                zoneModule::zones, zoneTracker, characters),
                        this);

        // US2: the warning under the level band. It hears the zone change on B01's bus rather than
        // being called by the tracker - the tracker announces what happened, it does not decide what
        // anybody makes of it (FR-021).
        rpg.core.zone.LevelBandGuard levelBandGuard =
                new rpg.core.zone.LevelBandGuard(
                        zoneModule::zones,
                        characterId -> progressionModule.progression().levelOf(characterId),
                        (characterId, key, placeholders) -> {
                            org.bukkit.entity.Player target = playerOfCharacter(characterId);
                            if (target != null) {
                                target.sendMessage(messages.get(key, placeholders));
                            }
                        },
                        Clock.systemUTC(),
                        () -> zoneModule.config().warningCooldown());
        eventBus.subscribe(rpg.core.zone.ZoneChangedEvent.class, levelBandGuard::onZoneChanged);
        zoneForget = levelBandGuard::forget;

        // US3: the damage permission becomes a zone rule (FR-026). This is the line B05 was built
        // for - it laid the decision out at one place and guards it with SinglePermissionPointTest,
        // so this REPLACES the rule rather than adding a second copy of it. The shipped
        // configuration has every region on pvp: false, so nothing about the game changes here; what
        // changes is that a PvP region is now one line of configuration away (SC-008, FR-031).
        combatModule
                .pipeline()
                .setPermission(
                        new rpg.core.zone.ZoneDamagePermission(zoneTracker, zoneModule::zones));
        // And the other half of the same promise. The permission is only consulted where there is an
        // attacker; environment damage - lava, fire, drowning, a fall - never reaches it, so a safe
        // core would have been safe from players and mobs and from nothing else (FR-028, SC-002).
        // The bootstrap test found that, which is the whole reason it exists (ADR-012).
        combatModule
                .pipeline()
                .registerInterceptor(new rpg.core.zone.SafeCoreDamageGuard(zoneTracker));

        // US4: a death goes back to the safe core of the region it happened in (FR-033). NORMAL
        // priority, because B05 already listens on this event at MONITOR to refill health and mana -
        // and MONITOR means look, do not touch, so the location has to be set before it runs. The two
        // answer different questions and neither reads the other's answer.
        zoneTeleporter = new rpg.platform.zone.BukkitTeleporter(getServer(), getLogger());
        zoneRespawnRouting = new rpg.core.zone.RespawnRouting(zoneTracker, zoneModule::zones);
        rpg.core.zone.RespawnRouting respawnRouting = zoneRespawnRouting;

        // US5: leaving in combat is a death (ADR-030). Applied from the session observer and NOT
        // from PlayerQuitEvent - B03 owns the session lifecycle and allows exactly one handler
        // (FR-007), which NoCompetingSessionListenersTest enforces and which the first draft of this
        // block learned the hard way.
        zoneCombatLogout =
                new rpg.core.zone.CombatLogoutRule(
                        () -> zoneModule.config().combatLogoutIsDeath(),
                        holderId -> combatModule.pipeline().isInCombat(holderId),
                        zoneTracker,
                        zonePersistenceModule.store(),
                        eventBus);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.zone.ZoneRespawnListener(
                                respawnRouting,
                                (player, key) -> player.sendMessage(messages.get(key))),
                        this);

        // US6: waypoint crystals (ADR-032). The travel sequence is domain logic and stays here; the
        // right-click and the window are the two pieces the ADR marks as temporary and hands to B13.
        zoneTravel =
                new rpg.core.zone.DefaultTravel(
                        zoneModule::zones,
                        zonePersistenceModule.store(),
                        holderId -> combatModule.pipeline().isInCombat(holderId),
                        currencyModule.currency(),
                        zoneTeleporter);
        java.util.function.Function<java.util.UUID, java.util.Optional<java.util.UUID>>
                characterOfPlayer =
                        playerId -> {
                            org.bukkit.entity.Player online = getServer().getPlayer(playerId);
                            return online == null
                                    ? java.util.Optional.empty()
                                    : characterIdOf(online);
                        };
        rpg.platform.ui.WaypointMenuListener waypointMenu =
                new rpg.platform.ui.WaypointMenuListener(
                        new rpg.platform.ui.WaypointMenu(messages, uiMenuFrame()),
                        zoneModule::zones,
                        zonePersistenceModule.store(),
                        zoneTravel,
                        characterOfPlayer,
                        messages);
        rpg.platform.ui.CrystalInteractListener crystalInteract =
                new rpg.platform.ui.CrystalInteractListener(
                        zoneModule::zones,
                        zonePersistenceModule.store(),
                        characterOfPlayer,
                        waypointMenu::open,
                        messages,
                        System::currentTimeMillis);
        getServer().getPluginManager().registerEvents(waypointMenu, this);
        getServer().getPluginManager().registerEvents(crystalInteract, this);
        // Both keep a small map per player - a cooldown stamp and an open window. Neither would ever
        // shrink on its own, so the session end clears them, the same way the warning's repeat block
        // is cleared.
        zoneForgetPlayer =
                playerId -> {
                    crystalInteract.forget(playerId);
                    waypointMenu.forget(playerId);
                };

        zoneModule.onReload(
                () -> {
                    java.util.List<rpg.core.zone.ZoneTracker.Presence> present =
                            new java.util.ArrayList<>();
                    for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                        java.util.UUID characterId = characters.apply(player);
                        if (characterId != null) {
                            present.add(
                                    new rpg.core.zone.ZoneTracker.Presence(
                                            characterId,
                                            rpg.platform.zone.BukkitPositions.of(
                                                    player.getLocation())));
                        }
                    }
                    zoneTracker.reevaluateAll(present);
                });
    }

    /**
     * Assembles the Paper-facing half of B04.
     *
     * <p>Same introduction the session layer needs, for the same reason: the mirror lives in
     * {@code rpg-platform} and knows only {@code rpg-core} interfaces, the engine lives in
     * {@code rpg-persistence}, and neither module may depend on the other (Constitution III.2).
     *
     * <p>The regeneration guard runs after the bootstrap because it writes a game rule, which needs
     * the worlds to exist. It handles regeneration and food and nothing else - vanilla damage
     * sources belong to B05, and a test enforces that (FR-030b).
     */
    private void assembleStatLayer() {
        statsModule.installVanillaBridge(
                new PaperVanillaAttributeBridge(getServer(), scheduler, getLogger()));

        VanillaRegenerationGuard regenerationGuard = new VanillaRegenerationGuard(getLogger());
        regenerationGuard.applyTo(getServer());
        getServer().getPluginManager().registerEvents(regenerationGuard, this);
    }

    /**
     * Assembles the Paper-facing half of B05.
     *
     * <p>Five listeners, each with one job. The mob equipping is the one that would be easy to
     * forget and impossible to notice missing: without it nothing has a stat holder, so the entire
     * combat pipeline would apply to nothing but players - working, tested, and invisible.
     *
     * <p>Creatures that were already loaded when the plugin started are equipped here too. On a
     * reload the world is full of mobs that will never fire a spawn event again.
     */
    private void assembleCombatLayer() {
        CombatPipeline pipeline = registry.getService(CombatPipeline.class);
        StatEngine stats = registry.getService(StatEngine.class);

        ProjectileDamageTag.initialise(this);
        pipeline.registerFeedback(new PaperDamageFeedback(getServer(), scheduler, getLogger()));

        // B10 loest die aelteste offene Zusage dieses Projekts ein. B05s Uebergangsanbieter aus
        // combat.yml bleibt als Rueckfall dahinter stehen - er ist das, was eine Kreatur ohne Art
        // bekommt (FR-009), und genau dafuer war er immer gedacht.
        //
        // Die Schnittstelle ist dieselbe geblieben; was sich geaendert hat, ist die Bedeutung des
        // Schluessels. MobKindTag.kindKeyOf macht die Umrechnung an genau einer Stelle.
        // Ob ein `base` wirklich ein Entity-Typ dieses Servers ist, weiss nur Bukkit - also nicht
        // das Schema in rpg-core. Geprueft wird es trotzdem beim Start und nicht beim ersten Spawn:
        // ein Tippfehler waere sonst eine Art, die nie erscheint, und das sieht aus wie ein kaputter
        // Spawn statt wie ein kaputter Buchstabe (FR-002).
        rpg.platform.mob.PaperMobPlacer.verifyBasesExist(mobModule.config().kinds().values());

        PaperMobStatProvider fallback =
                new PaperMobStatProvider(combatModule.config(), StatConfig.defaults());
        rpg.core.combat.MobStatProvider fromKinds =
                rpg.core.mob.MobProviders.stats(mobModule::config, StatConfig.defaults());
        rpg.core.combat.MobStatProvider mobStats =
                kindKey -> {
                    java.util.Optional<rpg.core.stats.ModifierSet> own = fromKinds.statsFor(kindKey);
                    return own.isPresent() ? own : fallback.statsFor(kindKey);
                };
        pipeline.setMobStatProvider(mobStats);

        // Dieselbe Ablösung fuer Erfahrung und Coins. Ein leeres Ergebnis heisst weiterhin "kein
        // eigener Eintrag" und niemals Null - die beiden Bloecke fallen dann auf ihren eigenen
        // konfigurierten Standardwert zurueck, wie sie es immer getan haben (FR-007).
        progressionModule.progression().setMobXpProvider(rpg.core.mob.MobProviders.xp(mobModule::config));

        MobEquipmentListener mobEquipment =
                new MobEquipmentListener(stats, pipeline, mobStats, getLogger());
        CombatDeathListener deaths = new CombatDeathListener(stats, pipeline, getLogger());
        deaths.applyTo(getServer());

        getServer()
                .getPluginManager()
                .registerEvents(
                        new VanillaDamageListener(
                                pipeline, new VanillaDamageMapping(getLogger()), getLogger()),
                        this);
        getServer().getPluginManager().registerEvents(new ProjectileCombatListener(stats), this);
        getServer().getPluginManager().registerEvents(mobEquipment, this);
        getServer().getPluginManager().registerEvents(deaths, this);

        int equipped = 0;
        for (org.bukkit.World world : getServer().getWorlds()) {
            for (org.bukkit.entity.LivingEntity entity :
                    world.getLivingEntities()) {
                if (mobEquipment.wouldEquip(entity)) {
                    mobEquipment.equip(entity);
                    equipped++;
                }
            }
        }
        getLogger()
                .info("[combat] listeners registered; " + equipped + " already-loaded creature(s) equipped");

        // The two readouts. Deliberately not called a HUD: that name and its layout belong to B13,
        // which will take both over. Until then these are one action bar line and one chat line.
        // B04 adapted to the three numbers a readout needs. The displays get a reading, not the
        // engine - twenty of its twenty-one methods are none of their business.
        rpg.platform.hud.CombatStatusSource statusSource =
                holderId ->
                        stats.findSnapshot(holderId)
                                .map(
                                        snapshot -> {
                                            var resources = stats.resources(holderId);
                                            // Mana comes from the same reading as health, so the two
                                            // halves of the line can never be from different rounds.
                                            // A mob's maximum is zero, and the readout leaves the
                                            // mana part out rather than printing 0/0.
                                            return new rpg.platform.hud.CombatStatusSource.Status(
                                                    resources.currentHealth(),
                                                    resources.maxHealth(),
                                                    resources.currentMana(),
                                                    resources.maxMana(),
                                                    snapshot.get(rpg.core.stats.Attribute.DEFENSE),
                                                    meterOf(holderId),
                                                    progressOf(holderId));
                                        });

        StatusActionBar actionBar =
                new StatusActionBar(getServer(), statusSource, scheduler, messages, getLogger());
        actionBar.subscribeTo(eventBus);
        // startRefresh(...) stand hier bis B13. Der Takt ist UMGEZOGEN: wireUi() unten baut daraus
        // den einen HUD-Takt, der alle drei Flaechen bedient (R1, FR-010). Es bleibt bei EINEM -
        // wer hier den alten wiederherstellt, hat zwei Durchlaeufe je Sekunde, und welcher zuletzt
        // sendet, haengt an der Registrierungsreihenfolge.
        wireUi(actionBar, statusSource, scheduler);

        // Und die dritte Anzeige: was eine Kreatur ist und wie viel von ihr uebrig ist, ueber ihrem
        // Kopf. B13 hat sie NICHT uebernommen: ein Namensschild steht ueber einer Kreatur und ist
        // keine der drei Flaechen (FR-001). Eine Zeile, kein zweiter Entitaetstyp je Mob
        // (Prinzip II).
        new rpg.platform.hud.MobNameplate(
                        getServer(),
                        stats,
                        statusSource,
                        scheduler,
                        messages,
                        mobModule.kinds(),
                        getLogger())
                .subscribeTo(eventBus);

        // KEINE Zielzeile im Chat mehr. Sie sagte dasselbe wie das Namensschild ueber der Kreatur,
        // nur einmal je halber Sekunde und untereinander - bei einem laengeren Kampf war der Chat
        // voll und die Zahl trotzdem schwerer zu lesen als ueber dem Kopf des Gegners.
        //
        // Der Sweep darunter bleibt: er schliesst die Schadensfenster, und daran haengen spaeter
        // Statistik und Quests. Ohne ihn hoert niemand mehr von einem Kampf, der einfach aufhoert.
        startDamageWindowSweep(combatModule.config().aggregationWindow(), combatModule.pipeline());
    }

    /**
     * Credits health and mana that accrued since the last pass, for everyone in play.
     *
     * <p>The characters, not the players: regeneration belongs to the character (ADR-011), and the
     * two ids are different things even though B05 keys damage by holder.
     */
    private void settleRegeneration() {
        rpg.core.ability.ResourceRegeneration regeneration =
                abilityModule == null ? null : abilityModule.regeneration();
        if (regeneration == null) {
            return;
        }
        regeneration.settleAll(charactersInPlay());
    }

    /**
     * B13: die drei Flächen unter einem Takt.
     *
     * <p><b>Der Takt ist die Erweiterung von {@code StatusActionBar.startRefresh}</b>, nicht ein
     * zweiter daneben (R1, FR-010). Er war schon eine Sekunde lang und plante sich schon selbst neu
     * ein; B13 gibt ihm die zwei anderen Flächen dazu.
     *
     * <p><b>Und die Ereignispfade.</b> Der Takt allein erfüllt FR-009 nicht: „unmittelbar" und „bis
     * zu eine Sekunde später" sind zwei verschiedene Zusagen. Ein Aufstieg, der eine Sekunde
     * braucht, bis er auf der Sidebar steht, sieht aus, als hätte der Server ihn verschluckt.
     *
     * <p><b>Der Coin-Stand bekommt hier bewusst nichts</b> (FR-009a): {@code rpg.core.currency}
     * führt keinen Ereignistyp, nur {@code CoinLedger} und {@code BookingResult}. Die Zeile folgt
     * dem Takt. Nachgerüstet wird nichts — ein Ereignis in B08b wäre der Eingriff in einen fremden
     * Block, den dieser Block an drei anderen Stellen ablehnt.
     */
    private void wireUi(
            StatusActionBar actionBar,
            rpg.platform.hud.CombatStatusSource statusSource,
            rpg.core.scheduler.Scheduler scheduler) {
        rpg.platform.ui.PaperBossBar bossBar =
                new rpg.platform.ui.PaperBossBar(getServer(), messages);
        rpg.platform.ui.PaperSidebar sidebar =
                new rpg.platform.ui.PaperSidebar(getServer(), messages);
        rpg.platform.ui.PaperHudRenderer renderer =
                new rpg.platform.ui.PaperHudRenderer(
                        getServer(), scheduler, messages, bossBar, sidebar, getLogger());

        java.util.function.Function<java.util.UUID, java.util.Optional<java.util.UUID>>
                characterOfPlayer =
                        playerId ->
                                sessionModule.registry().all().stream()
                                        .filter(s -> s.playerId().equals(playerId))
                                        .findFirst()
                                        .flatMap(rpg.core.session.PlayerSession::activeCharacter)
                                        .map(rpg.core.session.PlayerCharacter::characterId);
        java.util.function.Function<java.util.UUID, java.util.Optional<java.util.UUID>>
                playerOfCharacter =
                        characterId ->
                                sessionModule.registry().all().stream()
                                        .filter(
                                                s ->
                                                        s.activeCharacter()
                                                                .map(
                                                                        c ->
                                                                                c.characterId()
                                                                                        .equals(
                                                                                                characterId))
                                                                .orElse(false))
                                        .findFirst()
                                        .map(rpg.core.session.PlayerSession::playerId);

        rpg.platform.ui.ZoneNoticeSource zoneNotice =
                new rpg.platform.ui.ZoneNoticeSource(
                        () -> uiModule.config(), playerOfCharacter, java.time.Clock.systemUTC());
        rpg.platform.ui.BossFightSource bossFight =
                new rpg.platform.ui.BossFightSource(
                        mobModule.kinds(), statusSource, java.time.Clock.systemUTC());
        rpg.platform.ui.ChannellingSource channelling =
                new rpg.platform.ui.ChannellingSource(
                        abilityRuntime,
                        abilityModule.registry(),
                        characterOfPlayer,
                        java.time.Clock.systemUTC());
        zoneNotice.subscribeTo(eventBus);
        bossFight.subscribeTo(eventBus);
        // ChannellingSource abonniert NICHTS: sie rechnet gegen die Uhr aus RunningAbility, und der
        // Takt fragt ohnehin jede Sekunde (R4). Die einzige der drei ohne eigenen Zustand.

        rpg.platform.ui.HudRefresh refresh =
                new rpg.platform.ui.HudRefresh(
                        renderer,
                        () -> uiModule.config(),
                        actionBar::show,
                        playerId -> sidebarLinesFor(playerId, characterOfPlayer),
                        new rpg.platform.ui.BossBarOccasions(channelling, bossFight, zoneNotice),
                        getLogger());

        // Die Ereignispfade der Sidebar (FR-009). Sie sind aus StatusActionBar HIERHER umgezogen -
        // dort zeichneten sie nur die Actionbar, hier den ganzen HUD.
        eventBus.subscribe(
                rpg.core.progression.ProgressChangedEvent.class,
                event -> refresh.refresh(event.playerId()));
        eventBus.subscribe(
                rpg.core.progression.LevelUpEvent.class,
                event -> refresh.refresh(event.playerId()));
        eventBus.subscribe(
                rpg.core.zone.ZoneChangedEvent.class,
                event ->
                        playerOfCharacter
                                .apply(event.characterId())
                                .ifPresent(refresh::refresh));

        // B13 US3: das Cooldown-Overlay. Es setzt auf dem Material auf, das AbilityHotbar bereits
        // gelegt hat - die Leiste selbst wird NICHT angefasst (FR-024). VOR dem Takt gebaut, weil
        // der Takt es mitlaufen laesst.
        cooldownOverlay =
                new rpg.platform.ui.AbilityCooldownOverlay(
                        getServer(),
                        scheduler,
                        abilityModule.registry(),
                        characterOfPlayer,
                        java.time.Clock.systemUTC());

        rpg.platform.ui.HudTick tick =
                new rpg.platform.ui.HudTick(
                        scheduler,
                        () -> uiModule.config(),
                        // Die Liste kommt aus B03s Registry, die die Autoritaet darueber ist, wer
                        // spielt - nicht aus irgendeinem Modul, das zufaellig eine Map davon haelt.
                        this::playersInPlay,
                        refresh,
                        getLogger());
        // Der Sekundenabgleich des Cooldown-Overlays. Er faengt, was der Ausloesepfad nicht sieht:
        // anhaltende Faehigkeiten starten ihren Cooldown beim ENDEN, Ladungsfaehigkeiten erst bei
        // der letzten. Beim Krieger wurde deshalb ausschliesslich Leap grau - die einzige seiner
        // aktiven ohne sustained.
        //
        // Kein zweiter Takt (FR-010): er laeuft in DIESEM mit.
        tick.alsoPerPlayer(cooldownOverlay::refresh);
        tick.start();

        // Was der HUD je Spieler haelt, geht mit der Sitzung (FR-004c). Vier Dinge, und jedes
        // einzeln vergessen zu koennen ist der Punkt: eine entfernte Bossbar, deren Eintrag stehen
        // bleibt, ist ein Leck, das erst nach Stunden auffaellt.
        // Beim Anmelden die VERBLEIBENDE Restzeit wiederherstellen (FR-033). B08 fuehrt den
        // Cooldown ueber Zeitstempel, er ueberlebt die Abmeldung also von selbst - was fehlt, ist
        // nur die Anzeige. Ohne diese Zeile saehe der Spieler ein bereites Item, drueckte es, und
        // nichts geschaehe.
        uiOnJoin.add(cooldownOverlay::restore);
        // Und beim AUSLOESEN - das ist der Normalfall, den der erste Entwurf vergessen hatte:
        // verdrahtet war nur der Anmeldepfad, also erschien das Overlay ausschliesslich nach einem
        // Relog. Gefunden beim Spielen, Schritt 8.
        uiOnAbilityUsed.add(
                (playerId, ability) -> {
                    java.util.Optional<java.util.UUID> characterId =
                            characterOfPlayer.apply(playerId);
                    if (characterId.isEmpty()) {
                        return;
                    }
                    // Die Restzeit kommt aus B08 und wird NICHT zweitgerechnet (FR-031). Direkt
                    // nach dem Ausloesen steht sie bereits - startCooldown lief im selben Aufruf.
                    abilityModule
                            .registry()
                            .remainingCooldown(characterId.get(), ability.id())
                            .ifPresent(
                                    remaining ->
                                            cooldownOverlay.apply(playerId, ability, remaining));
                });

        // B13 US4: die Schadenszahlen. Sie haengen am EventBus und NICHT am HUD-Takt - und das ist
        // der Punkt: DamageDealtEvent kommt aus dem Tick, nicht aus dem asynchronen Durchlauf.
        // Damit ist die Falle aus T112 gar nicht erst betreten (R2), statt nur umgangen.
        new rpg.platform.ui.DamageNumbers(
                        this,
                        getServer(),
                        scheduler,
                        messages,
                        () -> uiModule.config(),
                        // Wo das Ziel steht: B05 nennt im Ereignis nur seine Kennung.
                        entityId -> {
                            org.bukkit.entity.Entity entity = getServer().getEntity(entityId);
                            return entity == null
                                    ? java.util.Optional.empty()
                                    : java.util.Optional.of(
                                            BukkitPositions.of(entity.getLocation()));
                        },
                        java.time.Clock.systemUTC(),
                        getLogger())
                .subscribeTo(eventBus);

        uiForgetters.add(refresh::forget);
        uiForgetters.add(cooldownOverlay::forget);
        uiForgetters.add(renderer::forget);
        uiForgetters.add(zoneNotice::forget);
        uiForgetters.add(bossFight::forget);
        // wireCharacterSheet steht NICHT hier, obwohl es zu B13 gehoert: es braucht gearDisplay und
        // itemModule, und beide entstehen erst in der Item-Schicht. Hier gerufen waere es ein
        // NullPointerException beim Start - und zwar erst nach der Haelfte der Verdrahtung, also an
        // der unuebersichtlichsten Stelle. Es laeuft nach assembleItemLayer().
    }

    /**
     * B13 US2: die Charakterübersicht — das einzige wirklich <em>fehlende</em> Fenster.
     *
     * <p>Vier Blöcke haben Daten, die nirgendwo zusammen zu sehen sind: B04 die Attribute, B07 die
     * Klasse, B08b die Coins, B11 die Ausrüstung und ihren Zustand. Hier werden sie zusammengeführt
     * — <b>gelesen, nicht gespiegelt</b> (FR-074).
     */
    /**
     * Der gemeinsame Rahmen der <b>drei</b> Fenster in B13s Hand (T122).
     *
     * <p>Die Charakterübersicht, das Reisefenster und das Kontofenster. <b>Nicht</b>
     * {@code ClassSelectionMenu} aus B07 und <b>nicht</b> B12s Fenster (FR-070, FR-071) — die laufen,
     * sind abgenommen und keine ist befristet.
     *
     * <p>Neu erzeugt statt als Feld gehalten: {@code MenuFrame} ist zustandslos (nur die Texte), und
     * die drei Aufrufstellen liegen in drei verschiedenen Schichten der Verdrahtung. Ein Feld
     * quer durch alle drei wäre mehr Kopplung für dasselbe Objekt.
     */
    private rpg.platform.ui.MenuFrame uiMenuFrame() {
        return new rpg.platform.ui.MenuFrame(messages);
    }

    private void wireCharacterSheet() {
        java.util.function.Function<java.util.UUID, java.util.Optional<java.util.UUID>>
                characterOfPlayer =
                        playerId ->
                                sessionModule.registry().all().stream()
                                        .filter(s -> s.playerId().equals(playerId))
                                        .findFirst()
                                        .flatMap(rpg.core.session.PlayerSession::activeCharacter)
                                        .map(rpg.core.session.PlayerCharacter::characterId);
        rpg.core.ui.CharacterSheets sheets =
                new rpg.core.ui.CharacterSheets(
                        characterId ->
                                statsModule
                                        .engine()
                                        .holderOf(characterId)
                                        .map(holderId -> statsModule.engine().snapshot(holderId)),
                        new rpg.core.ui.CharacterSheets.EquipmentSource() {

                            @Override
                            public java.util.Optional<String> tagOf(
                                    java.util.UUID characterId, rpg.core.classes.LadderSlot slot) {
                                return classesModule.boundEquipment().expectedTag(characterId, slot);
                            }

                            @Override
                            public java.util.Map<
                                            rpg.core.classes.LadderSlot,
                                            rpg.core.classes.TierAppearance>
                                    equipmentOf(java.util.UUID characterId) {
                                // B07 fuehrt, WAS ein Charakter traegt - als gebundene Ausruestung
                                // mit einem Aussehen je Platz. NICHT B11: dessen items.yml kennt
                                // nur Traenke und Trims, keine Ruestung (ItemCategory hat genau
                                // CONSUMABLE und COSMETIC).
                                //
                                // Das Aussehen geht UNVERAENDERT durch. Ein erster Entwurf hat hier
                                // appearance.material() genommen und damit Farbe und Trim
                                // weggeworfen - und vor allem eine FAMILIE ("IRON") an eine Stelle
                                // gegeben, die ein Material erwartete. Beim Magier ging es, weil
                                // seine Leiter durchgehend LEATHER ist und das zufaellig auch ein
                                // Material; bei Krieger und Schurke blieb der Platz leer.
                                return classesModule
                                        .boundEquipment()
                                        .expectedFor(characterId)
                                        .orElseGet(java.util.Map::of);
                            }

                            @Override
                            public double conditionOf(
                                    java.util.UUID characterId, rpg.core.classes.LadderSlot slot) {
                                // WearCurve.FULL und nicht 1.0: B11 fuehrt den Zustand als PROZENT.
                                return itemModule == null
                                        ? rpg.core.item.WearCurve.FULL
                                        : gearConditionModule
                                                .conditions()
                                                .conditionOf(characterId, slot);
                            }
                        },
                        characterId -> abilityModule.registry().classOf(characterId),
                        characterId ->
                                progressionModule
                                        .progression()
                                        .progressOf(characterId)
                                        .map(rpg.core.progression.ProgressView::level)
                                        .orElse(1),
                        characterId -> currencyModule.currency().balanceOrZero(characterId));

        rpg.platform.ui.MenuFrame frame = uiMenuFrame();
        // Eine eigene Factory und kein geteiltes Feld: sie ist zustandslos (Vorlagen plus Texte),
        // und ein Feld quer durch die Verdrahtung zu reichen waere mehr Kopplung fuer denselben
        // Gegenstand. B11s Verhalten kommt trotzdem unveraendert heraus - das ist der Punkt von
        // FR-021a.
        rpg.platform.ui.PaperItemRenderer itemRenderer =
                new rpg.platform.ui.PaperItemRenderer(
                        new rpg.platform.item.ItemStackFactory(itemModule, messages),
                        // B07s Factory: sie setzt Familie, Platz, Farbe und Trim zu einem Teil
                        // zusammen. Genau das, was ein zusammengebastelter Materialname verloren
                        // hatte.
                        new BoundItemFactory(messages),
                        gearDisplay);
        rpg.platform.ui.CharacterSheetMenu sheetMenu =
                new rpg.platform.ui.CharacterSheetMenu(frame, itemRenderer);
        rpg.platform.ui.CharacterSheetListener sheetListener =
                new rpg.platform.ui.CharacterSheetListener(sheetMenu);
        getServer().getPluginManager().registerEvents(sheetListener, this);

        rpg.plugin.command.CharacterSheetCommand sheetCommand =
                new rpg.plugin.command.CharacterSheetCommand(
                        characterOfPlayer, sheets, sheetMenu, sheetListener, messages);
        var charCommand = getCommand("char");
        if (charCommand == null) {
            // plugin.yml und diese Stelle muessen sich einig sein; sind sie es nicht, ist es besser
            // das zu sagen als ein Kommando zu haben, das still nicht existiert.
            getLogger().severe("[ui] /char is not declared in plugin.yml - not registered");
            return;
        }
        charCommand.setExecutor(sheetCommand);
        charCommand.setTabCompleter(sheetCommand);

        uiForgetters.add(sheetListener::sessionEnded);
    }

    /**
     * Die vier Sidebar-Zeilen eines Spielers, oder leer, wenn er keinen Charakter hat.
     *
     * <p><b>Leer heißt „kein Charakter"</b> (FR-008), nicht „keine Zeilen": Nullen, die wie echte
     * Werte aussehen, sind schlimmer als nichts.
     */
    private java.util.Optional<List<rpg.core.ui.SidebarLines.Line>> sidebarLinesFor(
            java.util.UUID playerId,
            java.util.function.Function<java.util.UUID, java.util.Optional<java.util.UUID>>
                    characterOfPlayer) {
        java.util.Optional<java.util.UUID> characterId = characterOfPlayer.apply(playerId);
        if (characterId.isEmpty()) {
            return java.util.Optional.empty();
        }
        rpg.core.progression.ProgressView progress =
                progressionModule.progression().progressOf(characterId.get()).orElse(null);
        if (progress == null) {
            return java.util.Optional.empty();
        }
        // balanceOf und NICHT balanceOrZero: der Unterschied zwischen "nicht geladen" und "pleite"
        // ist genau der, um den es bei FR-008 geht. Eine Null, die wie ein echter Wert aussieht,
        // ist schlimmer als keine Zeile.
        java.util.OptionalLong coins = currencyModule.currency().balanceOf(characterId.get());
        if (coins.isEmpty()) {
            return java.util.Optional.empty();
        }
        // Der Zonenname ist selbst ein Schluessel (ZoneMessageKeys.nameOf) - eine Region heisst,
        // was die Sprachdatei sagt, und nicht, wie ihr Konfigurationsschluessel lautet. Der Umweg
        // ueber den Tracker nimmt die HALTERkennung, nicht die des Charakters.
        String zoneKey = zoneTracker.zoneKeyOf(playerId);
        java.util.Optional<String> zoneName =
                zoneKey == null
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(
                                messages.get(
                                        rpg.core.zone.ZoneMessageKeys.nameOf(zoneKey),
                                        java.util.Map.of()));
        return java.util.Optional.of(
                rpg.core.ui.SidebarLines.of(progress, coins.getAsLong(), zoneName));
    }

    /** Every character currently being played. */
    private List<java.util.UUID> charactersInPlay() {
        return sessionModule.registry().all().stream()
                .map(rpg.core.session.PlayerSession::activeCharacter)
                .flatMap(java.util.Optional::stream)
                .map(rpg.core.session.PlayerCharacter::characterId)
                .toList();
    }

    /** Everyone currently playing a character, from the registry that decides it. */
    private List<java.util.UUID> playersInPlay() {
        return sessionModule.registry().all().stream()
                .filter(session -> session.activeCharacter().isPresent())
                .map(rpg.core.session.PlayerSession::playerId)
                .toList();
    }

    /**
     * Closes the damage windows whose time is up.
     *
     * <p>Without this nothing ever closes an idle window: B05 only closes one when the <em>next</em>
     * hit arrives after it expired, so hitting a mob and stopping published no event at all. Everything
     * that listens - the target line, and later statistics and quests - simply never heard about those
     * hits.
     *
     * <p>Runs at the aggregation window from {@code combat.yml}, because that is the delay the design
     * already accepts between a hit and the report of it. One pass over the open windows; with none
     * open it is a single empty map scan.
     */
    private void startDamageWindowSweep(
            Duration interval, rpg.core.combat.DefaultCombatPipeline pipeline) {
        scheduler.runAsyncDelayed(
                interval,
                () -> {
                    pipeline.publishExpiredDamageWindows();
                    if (isEnabled()) {
                        startDamageWindowSweep(interval, pipeline);
                    }
                });
    }

    /**
     * The one sweep that drives every timed ability effect (FR-010b).
     *
     * <p>Interval effects, expiring buffs and lost projectiles share it, because they share the reason for existing:
     * both are "something that ends later", and the alternative - a task per running effect - is the
     * shape Constitution II rules out. At two hundred poisons this is one pass; with none running it
     * is two empty map scans.
     *
     * <p>Half a second, not a tick. An effect configured at one second is late by at most half of one,
     * and nothing in this game is fine-grained enough for that to be visible.
     */
    private void startAbilitySweep(
            rpg.core.ability.effect.IntervalEffectRunner intervals,
            rpg.core.ability.effect.BuffEffect buffs,
            rpg.platform.ability.AbilityProjectile projectiles) {
        scheduler.runAsyncDelayed(
                Duration.ofMillis(500),
                () -> {
                    intervals.sweep();
                    buffs.expire();
                    // B11s Trankwirkungen laufen auf DEMSELBEN Durchlauf ab (FR-034). Ein eigener
                    // waere eine zweite Taktung fuer dieselbe Frage - und hundert Traenke waeren
                    // hundert Aufgaben.
                    if (consumableBuffs != null) {
                        consumableBuffs.expire();
                    }
                    projectiles.sweep();
                    // Regeneration belongs here for the same reason the three above do: it is
                    // "something that happens later". It was originally settled only when somebody
                    // asked - and a wounded player standing still asks nothing, so health never
                    // climbed unless they used an ability. Riding this sweep adds no task.
                    settleRegeneration();
                    if (isEnabled()) {
                        startAbilitySweep(intervals, buffs, projectiles);
                    }
                });
    }

    /**
     * Assembles the Paper-facing half of B08 (T056).
     *
     * <p>Runs from inside the class layer, because it needs what that one built: the ability items are
     * placed <em>after</em> B07's bound weapon in slot 0, and the trigger path asks B05 whether a
     * target may be attacked rather than deciding that itself.
     *
     * <p>Without this the whole block is inert however green its own tests are (ADR-012).
     */
    private void assembleAbilityLayer() {
        rpg.core.ability.AbilityRegistry abilities = abilityModule.registry();
        rpg.core.combat.CombatPipeline pipeline = combatModule.pipeline();

        // The rules live in rpg-core, the lookup here - the same split as MobStatProvider in B05.
        //
        // The permission predicate lets everything through for now, and that is a stated gap rather
        // than an oversight: B05 owns the rule but exposes no "may A attack B" read, only enforcement
        // inside the pipeline. Damage is therefore still refused correctly - abilityDamage checks it -
        // but a cone can currently NAME a target it may not hit. The pre-filter goes in when B05
        // exposes the query, which B09 needs anyway to make the rule per-zone (FR-023).
        rpg.platform.ability.PaperTargetResolver resolver =
                new rpg.platform.ability.PaperTargetResolver(getServer(), (caster, target) -> true);

        rpg.core.ability.effect.EffectDispatcher effects =
                new rpg.core.ability.effect.EffectDispatcher(getLogger());
        effects.register(
                rpg.core.ability.EffectType.DAMAGE,
                new rpg.core.ability.effect.DamageEffect(pipeline));
        effects.register(
                rpg.core.ability.EffectType.LIFESTEAL,
                new rpg.core.ability.effect.LifestealEffect(statsModule.engine()));
        effects.register(
                rpg.core.ability.EffectType.HEAL,
                new rpg.core.ability.effect.HealEffect(statsModule.engine()));
        effects.register(
                rpg.core.ability.EffectType.MANA_RESTORE,
                new rpg.core.ability.effect.ManaRestoreEffect(statsModule.engine()));
        effects.register(
                rpg.core.ability.EffectType.EVADE, new rpg.core.ability.effect.EvadeEffect());
        effects.register(
                rpg.core.ability.EffectType.MITIGATE,
                new rpg.core.ability.effect.MitigateEffect());
        // The shield keeps the absorption pool itself, so the instance is held rather than discarded -
        // the pipeline has to be able to ask it what it can take.
        rpg.core.ability.effect.ShieldEffect shields =
                new rpg.core.ability.effect.ShieldEffect(Clock.systemUTC());
        effects.register(rpg.core.ability.EffectType.SHIELD, shields);
        // UND der Abnehmer dazu. Ohne ihn fuellte sich der Vorrat bei jedem Wirken und niemand las
        // ihn je: Block und Magieschild waren acht Sekunden lang nichts. Registriert VOR den
        // Passiven, damit Zweites Leben sieht, was nach dem Schild uebrig ist.
        pipeline.registerInterceptor(shields.interceptor());

        // Buff and debuff differ only in who they land on, and the targeting decided that already.
        rpg.core.ability.effect.BuffEffect buffs =
                new rpg.core.ability.effect.BuffEffect(statsModule.engine(), Clock.systemUTC());
        effects.register(rpg.core.ability.EffectType.BUFF, buffs);
        effects.register(rpg.core.ability.EffectType.DEBUFF, buffs);
        rpg.core.ability.effect.MeterEffect meter =
                new rpg.core.ability.effect.MeterEffect(statsModule.engine(), Clock.systemUTC());
        abilityMeter = meter;
        effects.register(rpg.core.ability.EffectType.METER, meter);

        // The three that need the world. The two B10 gaps - mob aggression towards a clone, mobs
        // losing interest in someone who vanished - are named in the primitives' javadoc rather than
        // silently absent.
        rpg.platform.ability.PaperSummons summons =
                new rpg.platform.ability.PaperSummons(getServer(), scheduler, getLogger());
        summonEffect = new rpg.core.ability.effect.SummonEffect(summons);
        // Was der Klon hinterlaesst, wenn er geht (FR-016c). Aufgeloest wird um IHN herum, nicht um
        // den Rogue: dass die beiden auseinanderstehen, ist der ganze Zweck der Faehigkeit.
        summonEffect.setFarewell(
                (ability, summonerId, creatureId, rank, snapshot) ->
                        resolver.positionOf(creatureId)
                                .ifPresent(
                                        where ->
                                                effects.runAt(
                                                        ability,
                                                        rpg.core.ability.EffectPhase.SUMMON_END,
                                                        summonerId,
                                                        resolver.resolveAt(
                                                                summonerId, where, ability.target()),
                                                        rank,
                                                        snapshot)));
        effects.register(rpg.core.ability.EffectType.SUMMON, summonEffect);
        effects.register(
                rpg.core.ability.EffectType.INVISIBILITY,
                new rpg.core.ability.effect.InvisibilityEffect(summons));

        rpg.platform.ability.AbilityProjectile projectiles =
                new rpg.platform.ability.AbilityProjectile(getServer(), pipeline, getLogger());
        effects.register(
                rpg.core.ability.EffectType.PROJECTILE,
                new rpg.core.ability.effect.ProjectileEffect(projectiles));
        getServer().getPluginManager().registerEvents(projectiles, this);

        rpg.platform.ability.PaperMovementEffects movement =
                new rpg.platform.ability.PaperMovementEffects(getServer(), getLogger());
        // Was ein Sprung anrichtet, richtet er beim Aufkommen an (FR-045d). Der Waechter reitet auf
        // PlayerMoveEvent mit und kostet einen int-Vergleich, solange niemand in der Luft ist - eine
        // Aufgabe je Sprung waere die wiederkehrende Aufgabe, die Prinzip II ausschliesst.
        abilityLandings =
                new rpg.platform.ability.LandingWatcher(
                        (ability, holderId, rank, snapshot) ->
                                resolver.positionOf(holderId)
                                        .ifPresent(
                                                where ->
                                                        effects.runAt(
                                                                ability,
                                                                rpg.core.ability.EffectPhase.LANDING,
                                                                holderId,
                                                                resolver.resolveAt(
                                                                        holderId,
                                                                        where,
                                                                        ability.target()),
                                                                rank,
                                                                snapshot)));
        movement.setLandingWatcher(abilityLandings);
        getServer().getPluginManager().registerEvents(abilityLandings, this);
        effects.register(rpg.core.ability.EffectType.DASH, movement.dash());
        effects.register(rpg.core.ability.EffectType.KNOCKBACK, movement.knockback());
        effects.register(rpg.core.ability.EffectType.TELEPORT, movement.teleport());

        // ONE sweep for every interval effect in the game, and one for expiring buffs. Per target
        // would be a recurring task per entity - the shape that made damage over time unacceptable
        // the first time round (FR-010b).
        rpg.core.ability.effect.IntervalEffectRunner intervals =
                new rpg.core.ability.effect.IntervalEffectRunner(effects, Clock.systemUTC());
        // Both directions: the dispatcher hands periodic effects TO the runner, and the runner hands
        // each due application back THROUGH the dispatcher, so it stays behind the same error barrier.
        effects.setIntervalRunner(intervals);
        // Und die Zielsuche, die eine verankerte Flaeche braucht: der Blitzsturm fragt bei JEDEM Tick
        // neu, wer auf der Stelle steht. Ohne das merkte er sich die Mobs statt den Ort - wer
        // hinauslief, brannte weiter, und wer hineinlief, blieb trocken (FR-019b).
        intervals.setTargets(resolver);
        // Und der Beobachter, der zeichnet, was gelandet ist. Er haengt hier und nicht in den
        // Primitiven: die leben in rpg-core und koennen einen Partikel gar nicht sehen.
        effects.setObserver(
                (ability, spec, casterId, targets) ->
                        abilityFeedback.showImpact(ability, casterId, targets));
        // The sweep runs off the tick; a due application must not. It walks the combat pipeline,
        // which publishes a death, which reaches listeners that look up entities - and that is a
        // chunk read. Bound to the caster, who is a player and therefore resolvable from any thread.
        intervals.setOnTick(
                (casterId, task) ->
                        scheduler.runSyncOnEntity(new rpg.core.scheduler.EntityRef(casterId), task));
        startAbilitySweep(intervals, buffs, projectiles);

        abilityRuntime =
                new rpg.core.ability.AbilityRuntime(
                        abilities,
                        statsModule.engine(),
                        resolver,
                        effects,
                        abilityModule.repository(),
                        Clock.systemUTC());

        // Settled before every mana check (FR-037) - and never on a timer. This is also where a
        // wounded player finally heals at all: ADR-013 switched vanilla regeneration off and left the
        // gap open until ADR-023 made the two rates attributes.
        abilityRuntime.setRegeneration(abilityModule.regeneration());

        // The one place in this block that schedules anything: entity-bound, single-shot (ADR-024).
        // A character with nothing running has no task, which is what SC-005 asserts.
        abilityRuntime.setScheduling(
                (holderId, delay, task) ->
                        scheduler.runSyncOnEntityDelayed(
                                new rpg.core.scheduler.EntityRef(holderId), delay, task));

        abilityHotbar = new rpg.platform.ability.AbilityHotbar(messages, getLogger());
        abilityFeedback = new rpg.platform.ability.AbilityFeedback(getServer(), getLogger());
        // Die Haltung einer gehaltenen Faehigkeit - beim Warrior der hochgehaltene Schild. Beide
        // Enden, weil nur der Runtime weiss, wann sie aufhoert: nach ihrer Dauer ODER durch einen
        // zweiten Rechtsklick.
        abilityRuntime.setSustain(
                new rpg.core.ability.AbilityRuntime.Sustain() {
                    @Override
                    public void started(java.util.UUID characterId, rpg.core.ability.Ability ability) {
                        withPlayer(characterId, player -> abilityFeedback.holdPose(player, ability));
                    }

                    @Override
                    public void ended(java.util.UUID characterId, rpg.core.ability.Ability ability) {
                        withPlayer(characterId, player -> abilityFeedback.releasePose(player, ability));
                    }
                });
        // Und das, was die Haltung bisher nach einem Wimpernschlag wieder fallen liess: Vanilla haelt
        // einen Schild nur, solange die Maustaste gedrueckt ist. Der Zuhoerer nimmt genau dieses eine
        // Loslassen zurueck - ein Ereignis je Block, kein Taktgeber je Spieler.
        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.HeldPoseListener(abilityFeedback, scheduler), this);

        // The passive triggers, hung on the three hooks B05 already has (research.md R6). Which stage
        // each one uses is not interchangeable - see PassiveInterceptors.
        rpg.core.ability.PassiveDispatcher passives =
                new rpg.core.ability.PassiveDispatcher(
                        abilities,
                        effects,
                        statsModule.engine(),
                        abilityModule.repository(),
                        Clock.systemUTC(),
                        Math::random);
        // The three things the passive rules describe but cannot do themselves.
        rpg.platform.ability.PaperPassiveHooks hooks =
                new rpg.platform.ability.PaperPassiveHooks(getServer(), messages, getLogger());
        passives.setBehindTargetCheck(hooks.behindTarget());
        // B09's answer, and it replaces the default rather than agreeing with it by accident
        // (FR-052). Both say yes everywhere; the difference is that this one was decided - the
        // release ships no instances, so there is nowhere that is not the open world. When an
        // instance world arrives, one method body changes and this line stays (ADR-006, ADR-025).
        passives.setWorldCondition(new rpg.core.zone.ZoneWorldCondition());
        effects.register(
                rpg.core.ability.EffectType.STATUS_EFFECT,
                new rpg.core.ability.effect.StatusEffectEffect(hooks.statusEffects()));

        // ON_KILL is the one trigger that is not an interceptor: killing is not a stage of the damage
        // pipeline, it is what the pipeline concludes, and B05 announces it.
        new rpg.core.ability.OnKillSubscriber(passives).subscribeTo(eventBus);

        // Mit der Rueckmeldung, was eine Milderung wirklich abgefangen hat. Magisches Leben nimmt
        // zehn bis zwanzig Prozent und lehnt nie einen Schlag ab - ohne diese Zeile ist es von
        // einem Mob, der niedrig wuerfelt, nicht zu unterscheiden, und genau deshalb galt es als
        // kaputt, waehrend es lief.
        pipeline.registerInterceptor(
                rpg.core.ability.PassiveInterceptors.damageTaken(
                        passives,
                        (holderId, before, after) -> {
                            org.bukkit.entity.Player hurt = getServer().getPlayer(holderId);
                            if (hurt == null) {
                                return;
                            }
                            hurt.sendMessage(
                                    messages.get(
                                            rpg.core.ability.AbilityMessageKeys.MITIGATED,
                                            java.util.Map.of(
                                                    "absorbed", oneDecimal(before - after),
                                                    "left", oneDecimal(after))));
                        }));
        pipeline.registerInterceptor(rpg.core.ability.PassiveInterceptors.damageDealt(passives));
        pipeline.registerInterceptor(
                rpg.core.ability.PassiveInterceptors.lethalBlow(
                        passives,
                        statsModule.engine(),
                        hooks.secondLife()));

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.AbilityTriggerListener(
                                (player, abilityId) -> {
                                    java.util.UUID characterId =
                                            characterIdOf(player).orElse(player.getUniqueId());
                                    rpg.core.ability.AbilityResult result =
                                            abilityRuntime.trigger(characterId, abilityId);
                                    if (result.isSuccess()) {
                                        // Nur bei Erfolg. Eine Ablehnung hat schon Worte für sich,
                                        // und ein Puff dazu ließe "noch im Cooldown" aussehen wie
                                        // eine Fähigkeit, die gewirkt hat.
                                        abilities.find(abilityId)
                                                .ifPresent(
                                                        ability -> {
                                                            abilityFeedback.show(player, ability);
                                                            // B13: das graue Sweep ueber dem Slot
                                                            // (FR-030). HIER und nicht am HUD-Takt:
                                                            // ein Cooldown, der erst beim naechsten
                                                            // Durchlauf grau wird, ist bis zu eine
                                                            // Sekunde zu spaet - und genau die
                                                            // Sekunde druecken Spieler ein zweites
                                                            // Mal.
                                                            //
                                                            // B08 veroeffentlicht kein
                                                            // Cooldown-Ereignis, an das man sich
                                                            // haengen koennte; startCooldown ist
                                                            // privat. Diese Stelle ist die einzige
                                                            // im Plugin, die vom ERFOLG einer
                                                            // Ausloesung weiss - und sie gehoert
                                                            // bereits der Verdrahtung, nicht B08.
                                                            uiOnAbilityUsed.forEach(
                                                                    hook ->
                                                                            hook.accept(
                                                                                    player
                                                                                            .getUniqueId(),
                                                                                    ability));
                                                        });
                                    }
                                    // Asked straight after the trigger, while the state that caused
                                    // the refusal is still the state: the cooldown still running, the
                                    // sustained ability still sustaining.
                                    return new rpg.platform.ability.AbilityTriggerListener.Outcome(
                                            result,
                                            resolveNames(
                                                    abilityRuntime.placeholdersFor(
                                                            characterId, abilityId, result)));
                                },
                                (player, key, values) ->
                                        player.sendMessage(messages.get(key, values)),
                                getLogger()),
                        this);

        // The double jump asks the registry which ability grants it, rather than naming one in code -
        // otherwise a piece of content would live in the source (EffectType.DOUBLE_JUMP).
        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.DoubleJumpListener(
                                player -> doubleJumpOf(abilities, player).isPresent(),
                                player ->
                                        doubleJumpOf(abilities, player)
                                                .map(
                                                        ability ->
                                                                abilities.toggleOf(
                                                                                characterIdOf(player)
                                                                                        .orElse(
                                                                                                player.getUniqueId()),
                                                                                ability.id())
                                                                        != rpg.core.ability.ToggleState
                                                                                .PARTIAL)
                                        .orElse(false),
                                () -> 0.8,
                                () -> 60),
                        this);

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.ability.CastInterruptListener(
                                player -> characterIdOf(player).orElse(null),
                                characterId ->
                                        abilityRuntime
                                                .running(characterId)
                                                .map(
                                                        running ->
                                                                abilities.find(running.abilityId())
                                                                        .map(
                                                                                rpg.core.ability
                                                                                                .Ability
                                                                                        ::interruptOnMove)
                                                                        .orElse(false))
                                                .orElse(false),
                                abilityRuntime::end),
                        this);

        // The session end is B03's to announce, not ours to listen for (FR-007, FR-014). The module
        // hears about it through its attachment and stops whatever was running.
        // Everything a character can leave behind: the running ability, its interval effects, its
        // timed buffs and its meter. A missed one is a leak that only shows up after hours of
        // players coming and going, which is the worst kind of leak to look for.
        abilityModule.setRunningEnder(
                characterId -> {
                    abilityRuntime.end(characterId, rpg.core.ability.EndCause.DISCONNECTED);
                    intervals.forget(characterId);
                    buffs.forget(characterId);
                    meter.forget(characterId);
                });

        getLogger()
                .info(
                        "[abilities] listeners registered - trigger, left-click guard, double jump,"
                                + " cast interruption");
    }

    /** The ability granting this player a double jump, if any is unlocked and switched on. */
    private java.util.Optional<rpg.core.ability.Ability> doubleJumpOf(
            rpg.core.ability.AbilityRegistry abilities, org.bukkit.entity.Player player) {
        return characterIdOf(player)
                .flatMap(
                        characterId ->
                                abilities.capability(
                                        characterId, rpg.core.ability.EffectType.DOUBLE_JUMP));
    }

    /**
     * The player currently playing this character, or {@code null}.
     *
     * <p>The counterpart of {@link #characterIdOf}, and it goes through {@code StatEngine.holderOf}
     * rather than scanning the online players: the engine owns that relation and keeps a reverse
     * index for it. B08 once handed character ids to methods expecting holder ids and the result was
     * a server on which nothing worked - the translation belongs at the one place that owns it.
     */
    private org.bukkit.entity.Player playerOfCharacter(java.util.UUID characterId) {
        if (statsModule == null) {
            return null;
        }
        return statsModule
                .engine()
                .holderOf(characterId)
                .map(getServer()::getPlayer)
                .orElse(null);
    }

    /** The character a player is currently playing, for the trigger path. */
    private java.util.Optional<java.util.UUID> characterIdOf(org.bukkit.entity.Player player) {
        return sessionModule
                .registry()
                .find(player.getUniqueId())
                .flatMap(rpg.core.session.PlayerSession::activeCharacter)
                .map(rpg.core.session.PlayerCharacter::characterId);
    }

    /**
     * Assembles the Paper-facing half of B07, and hands back the seam B03 drives it through.
     *
     * <p>Four listeners and one observer. The observer is why this runs before
     * {@link #registerSessionListeners}: B07 has to act the moment a session is ready - open the
     * selection, or put the class equipment back on - and B03 permits exactly one join handler
     * (FR-007). So the class layer does not listen for joins; it is told about them.
     *
     * <p>{@link ClassSelectionListener} gets a {@link rpg.platform.classes.CharacterEntry} that is the
     * only path from "class chosen" to "in the game state": activating the character on the session runs
     * every attachment - B04's holder, B06's level, B07's tiers - and the equipment goes on afterwards,
     * because it is built from those tiers.
     */
    private SessionObserver assembleClassLayer() {
        ClassRegistry classes = classesModule.registry();
        NoCharacterGuardListener guard = new NoCharacterGuardListener(getLogger());
        // The vanilla bar shows B06's level and experience; it stores nothing of its own. Subscribed
        // here so every gain reaches it, and read once on entry for the character's starting value.
        experienceBar = new ExperienceBar(getServer(), scheduler, getLogger());
        experienceBar.subscribeTo(eventBus);

        // A level-up may unlock an ability, and then the hotbar has to grow by one slot (T123,
        // FR-059). The whole layout is redone rather than one slot appended: it costs nothing at this
        // frequency, and it cannot get out of step the way a patch can.
        eventBus.subscribe(
                rpg.core.progression.LevelUpEvent.class,
                event -> {
                    org.bukkit.entity.Player player = getServer().getPlayer(event.playerId());
                    if (player != null) {
                        scheduler.runSyncOnEntity(
                                new rpg.core.scheduler.EntityRef(event.playerId()),
                                () -> {
                                    layOutAbilities(player, event.characterId());
                                    announceUnlocks(player, event);
                                });
                    }
                });
        ClassEquipmentApplier equipment =
                new ClassEquipmentApplier(
                        classesModule.boundEquipment(),
                        new BoundItemFactory(messages),
                        // B11s gekaufte Trimfarbe. Ob sie ueberhaupt getragen werden darf, ist
                        // in CosmeticApplication entschieden - hier wird nur eingesetzt, was
                        // dort freigegeben wurde (FR-069, FR-070).
                        new rpg.platform.item.CosmeticOverride(cosmeticModule.cosmetics()),
                        getLogger());

        characterEntry = (player, character) -> enterGameState(player, character, equipment);

        // EIN Aufstieg, EIN neuer Satz Ausruestung - und zwar sofort.
        //
        // Bis hierher hoerte auf dieses Ereignis nur B07s Werteberechnung. Die Zahlen stiegen also
        // beim Kauf, die getragenen STUECKE aber nicht: sie werden ausschliesslich in
        // enterGameState gebaut, und das laeuft beim Eintritt. Wer eine Stufe kaufte, sah seine
        // neue Ruestung erst nach dem naechsten Einloggen - mit den neuen Werten daran, was den
        // Fehler noch schwerer erkennbar machte.
        //
        // Auf dem Entity-Scheduler, weil hier Inventarslots geschrieben werden (ADR-007), und
        // hinter einer Barriere, weil ein Fehler beim Anziehen keinen Kauf zurueckdrehen darf, der
        // bereits gebucht ist (Prinzip VI).
        eventBus.subscribe(
                rpg.core.classes.TierAdvancedEvent.class,
                event ->
                        onlinePlayerOfCharacter(event.characterId())
                                .ifPresent(
                                        player ->
                                                scheduler.runSyncOnEntity(
                                                        new rpg.core.scheduler.EntityRef(
                                                                player.getUniqueId()),
                                                        () ->
                                                                reapplyEquipment(
                                                                        equipment,
                                                                        player,
                                                                        event.characterId()))));

        ClassSelectionListener selection =
                new ClassSelectionListener(
                        classesModule.selection(),
                        new ClassSelectionMenu(classes, messages),
                        sessionModule.registry(),
                        guard,
                        characterEntry,
                        // The one place that sees all three blocks: B03 owns the characters, B06 the
                        // levels, B07 the tiers, and a menu entry needs all of it.
                        classesModule::slotsFor,
                        new SelectionTimeout(getServer(), scheduler, messages),
                        scheduler,
                        // Der Betreiber-Zugang. Die Berechtigung wird HIER geprueft und nicht im
                        // Listener: eine Berechtigung ist Paper-Sache, und der Listener soll die
                        // Antwort bekommen, nicht die Frage stellen muessen.
                        player -> player.hasPermission("rpg.admin.no-class"),
                        messages,
                        getLogger());

        getServer().getPluginManager().registerEvents(guard, this);
        getServer().getPluginManager().registerEvents(selection, this);
        getServer().getPluginManager().registerEvents(new EquipmentLockListener(getLogger()), this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        inventoryFullNotice =
                                new InventoryFullNoticeListener(
                                        new PaperClassNotice(getServer(), messages),
                                        Clock.systemUTC(),
                                        // FR-076: die Ruhezeit steht in items.yml. Als Funktion,
                                        // damit ein Nachladen sie wirklich aendert - ein hier
                                        // gezogener Wert bliebe bis zum Neustart der alte.
                                        () -> itemModule.config().inventoryFullCooldown()),
                        this);

        getLogger()
                .info(
                        "[classes] listeners registered - selection, guard, equipment lock, "
                                + "inventory notice");

        assembleAbilityLayer();

        return new SessionObserver() {
            @Override
            public void onSessionReady(org.bukkit.entity.Player player) {
                // Nothing of the previous character while choosing: no items, no hearts, no level. What
                // the client shows here is whatever vanilla saved for the *player*, and the real copies
                // are in the database, waiting for the character they belong to.
                resetToNeutralState(player, experienceBar);
                // Opens the selection for a player without a character, and releases the guard for one
                // who has. Either way the equipment is applied afterwards - and does nothing when there
                // is no character to build it from.
                selection.openIfNeeded(player);
                classesModule
                        .characterOf(player.getUniqueId())
                        .ifPresent(
                                characterId -> {
                                    equipment.apply(player, characterId);
                                    // Die Ausruestung ist gerade frisch gebaut und weiss nichts
                                    // von einem Zustand. Ohne diese Zeile saehe ein Spieler seine
                                    // Ruestung bis zum ersten Treffer als unbeschaedigt - und das
                                    // ist genau der Moment, in dem er entscheidet, ob er zum
                                    // Haendler geht (FR-050).
                                    refreshGearDisplay(player, characterId);
                                    // B13: die VERBLEIBENDE Cooldown-Anzeige (FR-033). Erst hier,
                                    // nachdem der Charakter feststeht - vorher gaebe es keine
                                    // Faehigkeiten, ueber die man etwas legen koennte.
                                    for (java.util.function.Consumer<java.util.UUID> restore :
                                            uiOnJoin) {
                                        restore.accept(player.getUniqueId());
                                    }
                                });
                // B09: place the character in their region. This is the sanctioned way in - B03 owns
                // the session lifecycle and allows exactly one join handler (FR-007), so the zone
                // block observes rather than listens. Without this a player would be in no zone
                // until their first step, because the movement guard is built to do nothing while
                // somebody stands still.
                if (playtimeAccrual != null) {
                    // VOR placeInZone: das Platzieren veroeffentlicht ein ZoneChangedEvent, und
                    // das schliesst den ersten Abschnitt und oeffnet ihn mit der richtigen Zone
                    // neu. Andersherum begaenne die Zeitrechnung erst NACH dem Wechsel, und die
                    // Sekunden davor gehoerten niemandem.
                    playtimeAccrual.begin(player.getUniqueId(), null);
                }
                placeInZone(player);
            }

            @Override
            public void onSessionEnded(java.util.UUID playerId) {
                // B13 zuerst, und ueber den Observer statt ueber PlayerQuitEvent: B03 besitzt den
                // Lebenszyklus und laesst dort genau einen Handler zu (FR-007). Was der HUD und die
                // Uebersicht je Spieler halten - Bossbar, Scoreboard, Zonenhinweis, Bosskampf und
                // das zwischengespeicherte Fenster -, geht hier weg (FR-004c). Beim naechsten
                // Anmelden steht dann keine alte Leiste.
                for (java.util.function.Consumer<java.util.UUID> forget : uiForgetters) {
                    forget.accept(playerId);
                }
                selection.onSessionEnded(playerId);
                // Und die Merkliste des Betreiber-Zugangs. Sie waechst sonst die ganze
                // Serverlaufzeit lang, und ein Wiedereinstieg soll ohnehin frisch entscheiden.
                selection.forget(playerId);
                // The tracker keys on the character, but a session ends with a player id - it keeps
                // the last translation itself for exactly this moment, and hands it back so the
                // warning's repeat block can be cleared too. Without that, the block's map would grow
                // for the whole uptime of the server.
                // ADR-030 first, and before anything is forgotten: the combat state and the
                // placement are both keyed by the holder and both go away with the session.
                characterIdOf(getServer().getPlayer(playerId))
                        .ifPresent(
                                characterId ->
                                        zoneCombatLogout.onSessionEnding(playerId, characterId));
                zoneTracker.forgetHolder(playerId).ifPresent(zoneForget::accept);
                zoneForgetPlayer.accept(playerId);
                if (trashCommand != null) {
                    // Eine offene Bestaetigung ueberlebt die Sitzung nicht. Sie tut es auch
                    // sonst nicht - die Frist laeuft nach dreissig Sekunden ab -, aber der
                    // Eintrag laege bis zum Neustart herum.
                    trashCommand.forget(playerId);
                }
                if (inventoryFullNotice != null) {
                    inventoryFullNotice.forget(playerId);
                }
                if (vendorListener != null) {
                    // Dieselbe Stelle und derselbe Grund: eine Karte je Spieler, die sonst bis zum
                    // Neustart waechst. Und ein eigener Quit-Zuhoerer waere ein zweiter Weg in den
                    // Sitzungslebenszyklus, den B01s Waechter zu Recht verbietet.
                    vendorListener.forget(playerId);
                }
                if (abilityLandings != null) {
                    // Wer mitten im Sprung geht, kommt beim naechsten Login auf dem Boden an - und
                    // ein Aufprall mitten in einen Login hinein ist nicht, was die Faehigkeit meint.
                    abilityLandings.forget(playerId);
                }
                if (abilityFeedback != null) {
                    // Und wer mitten im Block geht: die Haltung endet mit ihm, aber der Vermerk
                    // darueber laege sonst bis zum Neustart des Servers herum.
                    abilityFeedback.forget(playerId);
                }
                if (statisticsMenus != null) {
                    // Dieselbe Stelle und derselbe Grund wie beim Haendlerfenster: eine Karte je
                    // Spieler, die sonst bis zum Neustart waechst.
                    statisticsMenus.forget(playerId);
                }
                if (playtimeAccrual != null) {
                    // B12: der letzte Zeitabschnitt wird HIER geschlossen und nicht in einem
                    // eigenen PlayerQuitEvent-Handler. B11 hat fuer genau diesen zweiten
                    // Ausstiegspfad eine architektonische Zusicherung eingefuehrt - zwei Tueren
                    // heissen, dass eine von beiden irgendwann vergessen wird. Und der
                    // Aktivitaetszeitstempel faellt gleich mit, sonst wuechse seine Karte die
                    // ganze Serverlaufzeit lang.
                    playtimeAccrual.end(playerId);
                }
                // Before B03 starts the unload: the player is still here, so their inventory can still
                // be read - and this is the last moment that is true. The observer runs on the quit
                // event, which is the player's own tick.
                org.bukkit.entity.Player leaving = getServer().getPlayer(playerId);
                if (leaving != null) {
                    captureInventory(leaving);
                }
            }
        };
    }

    /**
     * Tells the player which abilities the new level opened (FR-060).
     *
     * <p>Every level in the gap, not just the one reached: an admin command or a large kill can move
     * a character several levels at once, and a player who never hears about the ability they just
     * got will not use it.
     */
    private void announceUnlocks(
            org.bukkit.entity.Player player, rpg.core.progression.LevelUpEvent event) {
        if (abilityModule == null) {
            return;
        }
        abilityModule
                .registry()
                .classOf(event.characterId())
                .ifPresent(
                        characterClass -> {
                            for (rpg.core.classes.AbilityBinding binding :
                                    classesModule.config().definition(characterClass).abilities()) {
                                if (binding.unlockLevel() > event.previousLevel()
                                        && binding.unlockLevel() <= event.newLevel()) {
                                    announceUnlock(player, binding.abilityId());
                                }
                            }
                        });
    }

    private void announceUnlock(org.bukkit.entity.Player player, String abilityId) {
        abilityModule
                .registry()
                .find(abilityId)
                .ifPresent(
                        ability ->
                                player.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                messages.get(
                                                        rpg.core.ability.AbilityMessageKeys.UNLOCKED,
                                                        java.util.Map.of(
                                                                "ability",
                                                                messages.get(
                                                                        ability.displayNameKey()))))));
    }

    /**
     * Turns the one placeholder value that is a message key into the text it names.
     *
     * <p>B08 owns no wording (Constitution V), so when a refusal has to name an ability - "Finish
     * Whirl first" - the block hands over the display-name KEY and this is where it becomes a word.
     * Everything else in the map is already a number and passes through untouched.
     */
    private java.util.Map<String, String> resolveNames(java.util.Map<String, String> values) {
        String abilityKey = values.get("ability");
        if (abilityKey == null) {
            return values;
        }
        java.util.Map<String, String> resolved = new java.util.HashMap<>(values);
        resolved.put("ability", messages.get(rpg.core.message.MessageKey.of(abilityKey)));
        return resolved;
    }

    /**
     * Der Zaehler dieses Traegers, oder null, wenn er keinen hat.
     *
     * <p><b>Ohne die Klasse zu nennen.</b> Ein Charakter hat einen Zaehler, wenn eine seiner
     * freigeschalteten Faehigkeiten einen METER-Effekt traegt - heute die Raserei des Warriors,
     * morgen vielleicht etwas anderes, ohne dass hier eine Zeile geaendert werden muss. Genau so
     * fragt der Doppelsprung nach seiner Faehigkeit, statt {@code mage.rise-and-fall} in den Code zu
     * schreiben (SC-001).
     *
     * <p>Null fuer jeden anderen: ein Mob, ein Magier, ein Rogue. Die Actionbar laesst den Teil dann
     * weg, statt eine Null zu zeigen, die nichts bedeutet.
     */
    private double meterOf(java.util.UUID holderId) {
        if (abilityModule == null || abilityMeter == null) {
            return 0.0;
        }
        java.util.UUID characterId =
                statsModule.engine().characterIdOf(holderId).orElse(null);
        if (characterId == null) {
            return 0.0;
        }
        return abilityModule
                .registry()
                .capability(characterId, rpg.core.ability.EffectType.METER)
                .flatMap(
                        ability ->
                                ability.effects().stream()
                                        .filter(
                                                spec ->
                                                        spec.type()
                                                                == rpg.core.ability.EffectType.METER)
                                        .findFirst())
                .map(spec -> abilityMeter.valueAt(holderId, spec, Clock.systemUTC().instant()))
                .orElse(0.0);
    }

    /**
     * Stufe und Erfahrung des Charakters hinter diesem Traeger, oder null, wenn es keinen gibt.
     *
     * <p>Denselben Weg wie {@link #meterOf}: der Halter ist die Id, unter der ein Spieler
     * adressierbar ist, der Fortschritt gehoert dem Charakter (ADR-011), und B04 kennt die
     * Zuordnung ohnehin schon.
     *
     * <p>Null fuer einen Mob und fuer einen Betreiber, der ohne Klasse in der Welt steht. Die
     * Actionbar laesst den Teil dann weg - eine Stufe 1 mit 0 Erfahrung anzuzeigen, wo es keinen
     * Charakter gibt, waere eine Zahl, die etwas behauptet.
     *
     * <p>{@code progressOf} rechnet nichts: B06 haelt den Stand im Speicher und beantwortet ihn
     * ohne Datenbankzugriff (FR-026, FR-028). Das ist die Voraussetzung dafuer, dass diese Zeile
     * einmal je Sekunde je Spieler gezeichnet werden darf.
     */
    private rpg.core.progression.ProgressView progressOf(java.util.UUID holderId) {
        if (progressionModule == null) {
            return null;
        }
        java.util.UUID characterId = statsModule.engine().characterIdOf(holderId).orElse(null);
        if (characterId == null) {
            return null;
        }
        return progressionModule.progression().progressOf(characterId).orElse(null);
    }

    /**
     * Fuehrt etwas am Spieler hinter einem Charakter aus, wenn er da ist.
     *
     * <p>Ueber den Halter, denn das ist die Id, unter der ein Spieler adressierbar ist - und die
     * Uebersetzung gehoert dem Stat-Engine (siehe {@code StatEngine#holderOf}).
     */
    private void withPlayer(
            java.util.UUID characterId, java.util.function.Consumer<org.bukkit.entity.Player> action) {
        statsModule
                .engine()
                .holderOf(characterId)
                .map(getServer()::getPlayer)
                .ifPresent(action);
    }

    /**
     * Puts this character's ability items into the hotbar (T123, T124).
     *
     * <p><b>Laid out from the reached level, never patched from an event.</b> Called on entry and
     * again on every level-up, and both calls do the same complete thing - so a level-up that was
     * missed, or one that happened while the ability layer was still starting, cannot leave a slot
     * empty for the rest of the session. There is no state here to get out of step.
     */
    private void layOutAbilities(org.bukkit.entity.Player player, java.util.UUID characterId) {
        if (abilityHotbar == null || abilityModule == null) {
            return;
        }
        abilityHotbar.layOut(player, abilityModule.registry().unlockedFor(characterId));
    }

    /**
     * Takes a freshly chosen character into play.
     *
     * <p>Activation first, equipment second, and the order is the whole point: the items are built from
     * the tiers, and the tiers only exist once the session activated the character.
     *
     * <p>A failure to put the equipment on is <b>not</b> a failure to enter. The character exists, has
     * stats and a level, and can play; the applier logs what it could not place, and the next login
     * applies it again. Refusing the entry over it would leave a stored character no session can reach.
     */
    private boolean enterGameState(
            org.bukkit.entity.Player player,
            rpg.core.session.PlayerCharacter character,
            ClassEquipmentApplier equipment) {
        if (!sessionModule.lifecycle().activateCharacter(player.getUniqueId(), character)) {
            return false;
        }
        java.util.UUID characterId = character.characterId();

        // Three steps, and the order is the whole of it.
        //
        // Emptied first, every time: in Minecraft both containers belong to the *player*, and the
        // selection is how someone switches between their characters - keeping them would carry the
        // warrior's loot into the mage. In practice they are already empty, because they are cleared
        // when the selection opens; this is the guarantee rather than the mechanism.
        clearCarriedItems(player);
        // Then what this character was carrying and storing when it was last put down. Bound items are
        // not in there; they are rebuilt below from the reached tier.
        inventoryModule
                .contentsOf(characterId)
                .ifPresent(
                        stored -> {
                            rpg.platform.inventory.PlayerInventoryContents.restore(
                                    player, stored.contents(), getLogger());
                            rpg.platform.inventory.PlayerInventoryContents.restoreEnderChest(
                                    player, stored.enderChest(), getLogger());
                        });
        // Class equipment last, so it always wins the slots it owns.
        equipment.apply(player, characterId);
        // Und der Zustand darauf, aus demselben Grund wie oben.
        refreshGearDisplay(player, characterId);
        // And the ability items on top of it, because they sit in the hotbar slots the weapon does not
        // own. Laid out from the reached level rather than patched from events (T124): a missed
        // level-up would otherwise leave a slot empty for the rest of the session, and nothing would
        // ever notice.
        layOutAbilities(player, characterId);

        // The bar, now that B06 has loaded this character's progress. From here on the subscription
        // keeps it current; this is only the starting value.
        registry.getService(rpg.core.progression.Progression.class)
                .progressOf(characterId)
                .ifPresent(
                        view ->
                                experienceBar.show(
                                        player.getUniqueId(), view.level(), view.fraction()));
        // B09/B10: the real entry point a player takes - through the selection menu - never called
        // this before. onSessionReady calls placeInZone too, but always too early for a session that
        // still needs a class chosen: no character is active there yet, so it silently does nothing.
        // Without this, a returning player's holder-to-character mapping in ZoneTracker never gets
        // established at all, which is invisible everywhere that only reads the character-keyed zone
        // (walking still updates it), but breaks anything reading it by holder id - including B10's
        // player count per zone.
        placeInZone(player);
        return true;
    }

    /**
     * Empties both containers that belong to the player rather than to a character.
     *
     * <p>Called when the selection opens and again on entry. The first is what the player sees: nobody
     * stands in the menu looking at the last character's backpack, and nothing from it can be reached
     * while choosing. The second is the guarantee that entry starts from a known state.
     *
     * <p>Safe to do at the menu because the contents were written down on the way out of the previous
     * session and are read back on entry. The one exception is the very first start with this build:
     * whatever players were carrying then belongs to no character - there was no character-level store
     * to attribute it to - and it is cleared.
     */
    private void clearCarriedItems(org.bukkit.entity.Player player) {
        player.getInventory().clear();
        player.getEnderChest().clear();
    }

    /**
     * Wipes everything the client shows that belongs to the player rather than to a character.
     *
     * <p>Items, hearts and the experience bar are all saved by vanilla per player, so at the moment a
     * session becomes ready they still show the character that was last played. Someone choosing a
     * character must not be looking at another one's health and level.
     *
     * <p>None of it is lost: the items come out of the database on entry, the hearts out of B04's
     * stored resources, and the bar out of B06's stored progress. This only clears the display until
     * the character that owns those values is in play.
     */
    private void resetToNeutralState(org.bukkit.entity.Player player, ExperienceBar bar) {
        clearCarriedItems(player);
        bar.reset(player);
        // Full hearts, because a half-empty bar belongs to the character that emptied it. The real
        // value arrives with the stat holder, which only exists once a character is chosen.
        org.bukkit.attribute.AttributeInstance maxHealth =
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
    }

    /**
     * Writes down what a player is carrying, for the character they are playing.
     *
     * <p>Must run on the player's tick - reading an inventory is a tick-only call - and must run while
     * they are still there. Both are why this is called from the quit observer and from a periodic
     * sweep rather than from the module that stores the result.
     */
    private void captureInventory(org.bukkit.entity.Player player) {
        inventoryModule
                .characterOf(player.getUniqueId())
                .ifPresent(
                        characterId ->
                                inventoryModule.store(
                                        characterId,
                                        rpg.platform.inventory.PlayerInventoryContents.capture(
                                                player, getLogger()),
                                        rpg.platform.inventory.PlayerInventoryContents
                                                .captureEnderChest(player, getLogger())));
    }

    /**
     * Snapshots every online player's inventory, again and again.
     *
     * <p>Without this a crash costs the whole session's loot: the quit path captures, but a crash has no
     * quit path. With it the loss is bounded by the interval, which is the same promise B02 makes for
     * everything else it writes.
     *
     * <p>Re-schedules itself instead of using a repeating task, because the scheduler has none - and
     * deliberately so (ADR-007). Each capture hops onto the owning player's tick; the waiting happens
     * off it.
     */
    /**
     * Verdrahtet B12 — die Erfassung, mehr nicht.
     *
     * <p>Vier Nähte von außen, und keine davon ist neu: B05s Kampfereignisse, B09s Zonenwechsel,
     * B04s {@code holderOf} und B06s Party. Dieser Block hört zu; er greift nirgends ein.
     *
     * <p><b>Keine eigene wiederkehrende Aufgabe</b> (Prinzip II, R6): die Fortschreibung der
     * Spielzeit hängt sich an {@link #startInventorySweep}, der ohnehin im Autosave-Takt über
     * genau die richtige Spielerliste läuft. {@code PlaytimeRidesTheExistingSweepTest} hält das
     * mechanisch fest.
     */
    private void wireStatistics() {
        rpg.core.persistence.StatisticsRepository repository =
                registry.getService(rpg.core.persistence.StatisticsRepository.class);
        rpg.core.statistics.Statistics statistics =
                new rpg.core.statistics.RecordedStatistics(repository, getLogger());

        rpg.core.statistics.AccountLookup accounts =
                rpg.core.statistics.AccountLookup.backedBy(registry.getService(StatEngine.class));
        rpg.core.statistics.ActivityClock activity = new rpg.core.statistics.ActivityClock();

        playtimeAccrual =
                new rpg.platform.statistics.PlaytimeAccrual(
                        statistics,
                        new rpg.core.statistics.Playtime(),
                        activity,
                        () -> statisticsModule.config().capture().idleAfter(),
                        Clock.systemUTC());

        // Dieselbe Party, dieselbe Reichweitenpruefung, dieselbe Zahl wie bei Erfahrung und Coins
        // (FR-007b). Eine eigene Reichweite in statistics.yml waere ein zweiter Begriff von
        // "dabei gewesen" - und niemand hielte ihn fuer eine Einstellung.
        rpg.platform.statistics.PaperPartyInRange partyInRange =
                new rpg.platform.statistics.PaperPartyInRange(
                        getServer(),
                        registry.getService(PartyRegistry.class),
                        new rpg.platform.progression.PaperProximityCheck(getServer()),
                        () -> progressionModule.config().partyRange(),
                        () -> progressionModule.config().partyMaxSize());

        new rpg.platform.statistics.KillStatListener(
                        statistics,
                        mobModule.kinds(),
                        accounts,
                        partyInRange,
                        () -> statisticsModule.config().capture().killCreditShare())
                .subscribeTo(eventBus);

        // Der Klon leistet fuer den Spieler (ADR-047). B08s Beschwoerung fuehrt die Zuordnung
        // bereits; eine zweite hier waere eine zweite Wahrheit ueber dieselbe Kreatur.
        new rpg.platform.statistics.DamageStatListener(
                        statistics,
                        entityId ->
                                cloneRegistry == null
                                        ? java.util.Optional.empty()
                                        : cloneRegistry.summonerOf(entityId))
                .subscribeTo(eventBus);

        new rpg.platform.statistics.ZoneTimeListener(playtimeAccrual, accounts)
                .subscribeTo(eventBus);

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.statistics.ActivityListener(activity, Clock.systemUTC()),
                        this);

        wireLeaderboards();

        getLogger().info("[statistics] capture wired - kills, deaths, damage, two clocks");
    }

    /**
     * Die Ranglisten: Speicherstand, Auffrischungstakt, Fenster und {@code /top}.
     *
     * <p><b>Ein Takt für alle Sichten und beide Quellen.</b> Zwei Takte hätten zwei Alter ergeben,
     * und das Fenster müsste erklären, welches gemeint ist.
     */
    private void wireLeaderboards() {
        leaderboardCache = rpg.core.statistics.LeaderboardCache.empty();
        rpg.core.statistics.Leaderboards leaderboards =
                rpg.core.statistics.Leaderboards.backedBy(leaderboardCache);

        // Die DataSource bleibt in rpg-persistence: NoDirectDatabaseAccessTest haelt seit B02
        // fest, dass java.sql nur dort vorkommt, und ein Pool, den sich das Plugin selbst holt,
        // waere der erste Schritt daran vorbei. Nach aussen geht eine fertige Auffrischung.
        rpg.persistence.statistics.StatisticsPersistenceModule statisticsPersistence =
                new rpg.persistence.statistics.StatisticsPersistenceModule(
                        persistenceModule,
                        leaderboardCache,
                        // Welche Art ein Boss ist, weiss B10 - eine zweite Antwort hier
                        // waere eine zweite Wahrheit (FR-009a).
                        kindKey ->
                                mobModule
                                        .kinds()
                                        .find(kindKey)
                                        .map(rpg.core.mob.MobKind::boss)
                                        .orElse(false),
                        () -> statisticsModule.config(),
                        // Namensaufloesung beim FUELLEN, ausserhalb des Ticks (FR-040).
                        playerId -> getServer().getOfflinePlayer(playerId).getName(),
                        getLogger(),
                        Clock.systemUTC());
        leaderboardFill = statisticsPersistence.fill();
        seasonClosing = statisticsPersistence.closing();

        statisticsMenus =
                new rpg.platform.statistics.StatisticsMenuListener(
                        new rpg.platform.statistics.LeaderboardMenu(leaderboards, messages),
                        Clock.systemUTC());
        getServer().getPluginManager().registerEvents(statisticsMenus, this);

        rpg.plugin.command.TopCommand top = new rpg.plugin.command.TopCommand(statisticsMenus);
        if (getCommand("top") != null) {
            getCommand("top").setExecutor(top);
            getCommand("top").setTabCompleter(top);
        }

        wireOwnProfile(leaderboards);

        // Faellige Saisonabschluesse NACHHOLEN, und zwar sofort (FR-058). Ein Quartalsende faellt
        // selten auf einen Moment, in dem der Server gerade laeuft - die Nachholung beim Start ist
        // der Normalfall, nicht die Ausnahme. Asynchron, weil hier abgefragt und geschrieben wird;
        // ein Spieler, der in derselben Sekunde hereinkommt, hat damit nichts zu tun.
        scheduler.runAsync(this::closeDueSeasons);

        // Die Welten sind zu diesem Zeitpunkt geladen - dieselbe Stelle im Start, an der auch die
        // Haendler gesetzt werden. Frueher gaebe es keine Welt, spaeter stuende die Anzeige erst
        // da, wenn die ersten Spieler schon durch den Hub gelaufen sind.
        placeLeaderboardHologram(leaderboards);

        startLeaderboardRefresh(statisticsModule.config().leaderboards().refreshInterval());
    }

    /**
     * Setzt die Anzeige im Hub, sofern eine konfiguriert ist (FR-060 bis FR-064).
     *
     * <p><b>Keine Anzeige ist ein gültiger Zustand</b>, und eine unerreichbare Welt ebenfalls: der
     * Server startet in beiden Fällen. Ein Tippfehler in {@code statistics.yml} darf niemanden vom
     * Spielen abhalten — die Anzeige ist Zierde, nicht Spielmechanik.
     */
    private void placeLeaderboardHologram(rpg.core.statistics.Leaderboards leaderboards) {
        java.util.Optional<rpg.core.statistics.StatisticsConfig.Hologram> settings =
                statisticsModule.config().hologram();
        if (settings.isEmpty()) {
            getLogger()
                    .info("[statistics] phase=START state=HOLOGRAM_SKIPPED - none configured");
            return;
        }

        rpg.platform.statistics.LeaderboardHologram hologram =
                new rpg.platform.statistics.LeaderboardHologram(leaderboards, messages, getLogger());
        if (hologram.place(settings.get(), Clock.systemUTC().instant()).isEmpty()) {
            // Die Warnung steht schon im Log, samt dem Namen der Welt. Hier bleibt nur, sie nicht
            // in den Auffrischungstakt zu haengen (FR-064).
            return;
        }

        org.bukkit.World world = getServer().getWorld(settings.get().world());
        leaderboardHologram = hologram;
        hologramAt =
                new rpg.core.scheduler.WorldPosition(
                        world.getUID(), settings.get().x(), settings.get().y(), settings.get().z());

        getLogger()
                .info(
                        "[statistics] phase=START state=HOLOGRAM_PLACED board="
                                + settings.get().board().key()
                                + " period="
                                + settings.get().period()
                                + " - reading the same cache as the windows, counted against no mob"
                                + " budget (FR-060, FR-062)");
    }

    /**
     * Beschriftet die Anzeige neu — im Auffrischungstakt, aber auf <b>ihrem</b> Tick.
     *
     * <p>Der Takt läuft asynchron; eine Entität von dort aus anzufassen ist der Fehler, der auf
     * Folia gar nicht und auf Paper nur meistens auffällt. Der Sprung geht über die Position und
     * nicht über die Entität: {@code runSyncOnEntity} aus einem Hintergrundfaden scheitert still.
     */
    private void refreshLeaderboardHologram() {
        if (leaderboardHologram == null || hologramAt == null) {
            return;
        }
        scheduler.runSyncAtLocation(
                hologramAt, () -> leaderboardHologram.refresh(Clock.systemUTC().instant()));
    }

    /**
     * Faellige Saisonabschlüsse — beim Start und in jedem Auffrischungstakt (FR-058).
     *
     * <p>Zweimal zu laufen kostet nichts: der Beleg für „abgeschlossen" sind die Zeilen im
     * Endstand, und ein zweiter Durchlauf findet sie und tut nichts. Deshalb braucht das hier
     * keinen Zeitplan, der sich merkt, wann er zuletzt lief.
     */
    private void closeDueSeasons() {
        if (seasonClosing == null) {
            return;
        }
        try {
            seasonClosing.closeDueSeasons();
        } catch (RuntimeException failure) {
            // Ein gescheiterter Abschluss darf weder den Start noch den Auffrischungstakt
            // mitnehmen: die Ranglisten funktionieren ohne ihn weiter, und der naechste Takt
            // versucht es erneut.
            getLogger()
                    .log(java.util.logging.Level.WARNING, "[statistics] season closing failed", failure);
        }
    }

    /**
     * Das eigene Profil: Lesefassade, Lader, Fenster und {@code /stats}.
     *
     * <p><b>Kein Cache</b> — anders als bei den Ranglisten. Das Profil fragt nach <em>einem</em>
     * Konto, und der Fragende ist der, dessen Zahlen es sind; ein Speicherstand zeigte ausgerechnet
     * ihm veraltete Werte, direkt nachdem er etwas getan hat.
     */
    private void wireOwnProfile(rpg.core.statistics.Leaderboards leaderboards) {
        rpg.core.statistics.StatisticsView statisticsView =
                new rpg.core.statistics.StatisticsView(
                        // Die DataSource bleibt hinter der Modulgrenze; hier kommt eine fertige
                        // rohe Sicht heraus (NoDirectDatabaseAccessTest).
                        rpg.persistence.statistics.StatisticsPersistenceModule.rawView(
                                persistenceModule, scheduler),
                        () -> statisticsModule.config().seasons(),
                        Clock.systemUTC());

        rpg.platform.statistics.ProfileLoader loader =
                new rpg.platform.statistics.ProfileLoader(
                        statisticsView,
                        leaderboards,
                        kindKey ->
                                mobModule
                                        .kinds()
                                        .find(kindKey)
                                        .map(rpg.core.mob.MobKind::boss)
                                        .orElse(false));

        rpg.platform.statistics.StatisticsMenu profileMenu =
                new rpg.platform.statistics.StatisticsMenu(messages);

        rpg.plugin.command.StatisticsCommand stats =
                new rpg.plugin.command.StatisticsCommand(
                        loader,
                        (player, profile) ->
                                // Das Laden lief asynchron; ein Inventar darf nur auf dem Tick
                                // geoeffnet werden - und zwar auf dem des Spielers.
                                scheduler.runSyncOnEntity(
                                        new rpg.core.scheduler.EntityRef(player.getUniqueId()),
                                        () ->
                                                statisticsMenus.openProfile(
                                                        player, profileMenu, profile, player.getName())),
                        // Name -> Konto. getOfflinePlayer(String) fragt den Namens-Cache des
                        // Servers; hasPlayedBefore() trennt einen echten Namen von einem
                        // Tippfehler, der sonst als leeres Profil durchginge.
                        name -> {
                            org.bukkit.OfflinePlayer found = getServer().getOfflinePlayer(name);
                            return found.hasPlayedBefore() || found.isOnline()
                                    ? java.util.Optional.of(found.getUniqueId())
                                    : java.util.Optional.empty();
                        },
                        // Das fremde Profil ist bereits fertig - es kommt aus dem Speicherstand.
                        (player, profile) ->
                                statisticsMenus.openProfile(
                                        player,
                                        profileMenu,
                                        profile,
                                        java.util.Optional.ofNullable(
                                                        getServer()
                                                                .getOfflinePlayer(profile.account())
                                                                .getName())
                                                .orElse(profile.account().toString())),
                        (player, name) ->
                                player.sendMessage(
                                        messages.get(
                                                rpg.core.statistics.StatisticsMessageKeys
                                                        .UNKNOWN_PLAYER,
                                                java.util.Map.of("player", name))));

        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(stats);
            getCommand("stats").setTabCompleter(stats);
        }
    }

    /**
     * Der Auffrischungstakt — nach dem Muster der vorhandenen Sweeps.
     *
     * <p>Asynchron: {@code REFRESH MATERIALIZED VIEW} rechnet, und der Tick hat damit nichts zu
     * tun (FR-031). Der erste Durchlauf ist ebenfalls verzögert — bis dahin sagt jedes Fenster,
     * dass der Stand noch aufgebaut wird (FR-035), statt eine Ersatzabfrage zu stellen.
     */
    private void startLeaderboardRefresh(Duration interval) {
        scheduler.runAsyncDelayed(
                interval,
                () -> {
                    if (leaderboardFill != null) {
                        leaderboardFill.refreshNow();
                    }
                    // Im SELBEN Takt und im selben Faden: ein Server, der ueber den Jahreswechsel
                    // durchlaeuft, wuerde sonst nie abschliessen. Eine eigene Aufgabe waere ein
                    // zweiter Takt fuer dieselbe Sache (R6, Prinzip II).
                    closeDueSeasons();
                    // Und die Anzeige im Hub bekommt denselben Stand wie die Fenster - im selben
                    // Takt, damit niemand erklaeren muss, welches der beiden Alter gemeint ist.
                    refreshLeaderboardHologram();
                    if (isEnabled()) {
                        startLeaderboardRefresh(
                                statisticsModule.config().leaderboards().refreshInterval());
                    }
                });
    }

    private void startInventorySweep(Duration interval) {
        scheduler.runAsyncDelayed(
                interval,
                () -> {
                    // The module's list, not the server's: asking the server for its online players
                    // from off the tick is not safe, and this is the more precise question anyway -
                    // someone still sitting in the selection has no character to capture for.
                    for (java.util.UUID playerId : inventoryModule.playersInPlay()) {
                        scheduler.runSyncOnEntity(
                                new rpg.core.scheduler.EntityRef(playerId),
                                () -> {
                                    org.bukkit.entity.Player player = getServer().getPlayer(playerId);
                                    if (player != null) {
                                        captureInventory(player);
                                        if (playtimeAccrual != null) {
                                            // B12 reitet hier mit und legt KEINE eigene Aufgabe an
                                            // (R6, Prinzip II). Derselbe Takt, dieselbe Liste -
                                            // und ein Absturz kostet ein Autosave-Intervall, wie
                                            // Prinzip IV es ohnehin zusagt.
                                            playtimeAccrual.accrue(playerId);
                                        }
                                    }
                                });
                    }
                    if (isEnabled()) {
                        startInventorySweep(interval);
                    }
                });
    }

    /**
     * Assembles the Paper-facing half of B06.
     *
     * <p>Two extension points and one subscriber. The proximity check is the only part of this block
     * that needs Bukkit at all; the death listener hangs off the <b>core</b> event bus, because that
     * is where B05 publishes - and it does so while Bukkit's death handling is still running, which
     * is what makes reading the creature's location safe.
     */
    private void assembleProgressionLayer() {
        DefaultProgression progression = progressionModule.progression();
        StatEngine stats = registry.getService(StatEngine.class);

        progression.setProximityCheck(new PaperProximityCheck(getServer()));

        PartyRegistry parties =
                new PartyRegistry(
                        sessionModule.registry(),
                        eventBus,
                        Clock.systemUTC(),
                        progressionModule.config().partyMaxSize(),
                        progressionModule.config().inviteTimeout());
        registry.registerService(ProgressionModule.ID, PartyRegistry.class, parties);
        sessionModule.lifecycle().addAttachment(new PartySessionAttachment(parties));

        XpDistributor distributor =
                new XpDistributor(
                        progression,
                        parties,
                        stats,
                        progressionModule.config(),
                        getLogger());
        ProgressionDeathListener deaths =
                new ProgressionDeathListener(getServer(), distributor, getLogger());
        deaths.subscribeTo(eventBus);

        getLogger()
                .info(
                        "[progression] listeners registered - max level "
                                + progression.maxLevel()
                                + ", party range "
                                + progressionModule.config().partyRange()
                                + " blocks");

        registerCurrencyListeners(distributor, stats);
    }

    /**
     * B10s Coins, und dahinter B08bs eigene Konfiguration.
     *
     * <p>Kein zweiter Anbieter neben dem ersten, sondern eine Kette: die Art antwortet, und wenn es
     * keine gibt, antwortet das, was vorher schon antwortete. Ein leeres Ergebnis heisst weiterhin
     * "kein eigener Eintrag" und niemals Null (FR-007).
     */
    private rpg.core.currency.MobCoinProvider coinsFromKindsOr(
            rpg.core.currency.MobCoinProvider fallback) {
        rpg.core.currency.MobCoinProvider fromKinds =
                rpg.core.mob.MobProviders.coins(mobModule::config);
        return kindKey -> {
            java.util.OptionalLong own = fromKinds.coinsFor(kindKey);
            return own.isPresent() ? own : fallback.coinsFor(kindKey);
        };
    }

    /**
     * The Paper-facing half of B08b: coin piles fall, and picking one up books it.
     *
     * <p>Wired after B06's, and from its pieces: the entitlement rule is
     * {@code XpDistributor}'s own {@code ShareCalculator} (ADR-029), so coins and experience value
     * the same kill identically. A second implementation would have agreed only until somebody
     * edited one.
     */
    private void registerCurrencyListeners(XpDistributor distributor, StatEngine stats) {
        rpg.core.currency.CurrencyConfig config = currencyModule.config();
        rpg.core.currency.DefaultCurrency currency = currencyModule.currency();

        rpg.core.currency.CoinDropPlanner planner =
                new rpg.core.currency.CoinDropPlanner(
                        distributor.shareCalculator(),
                        // B10 zuerst, B08bs eigene Konfiguration als Rueckfall dahinter. Eine
                        // Kreatur ohne Art bekommt weiterhin, was in currency.yml steht (FR-009).
                        coinsFromKindsOr(new rpg.core.currency.ConfigMobCoinProvider(config, getLogger())),
                        config,
                        // The one question this block asks B04, as a one-method interface rather
                        // than a dependency on the whole engine.
                        stats::characterIdOf);

        rpg.platform.currency.CoinPileRegistry pileRegistry =
                new rpg.platform.currency.CoinPileRegistry(
                        config,
                        // Through the admin path, not through Currency directly: the owner of the
                        // oldest pile is very likely logged out - that is often why it is the oldest
                        // - and they still have to be credited (FR-030c).
                        (characterId, amount, reason) ->
                                currencyModule
                                        .admin()
                                        .creditWhereverTheyAre(characterId, amount, reason)
                                        .isSuccess(),
                        Clock.systemUTC(),
                        getLogger(),
                        // Seit ADR-039 geteilt mit B11: die Mechanik fuer liegende Gegenstaende,
                        // die genau einem Charakter gehoeren, liegt in rpg.platform.drop und
                        // gehoert keinem Block allein (research.md R5).
                        rpg.platform.drop.OwnedDropPlatform.vanilla(this));

        rpg.platform.currency.CoinPile piles =
                new rpg.platform.currency.CoinPile(
                        this, getServer(), config, Clock.systemUTC(), getLogger());

        // A pile is invisible by default and shown to one player; that showing lives on the
        // connection and dies with it. Without this, a relogin left a pile invisible but still
        // collectable - the worst of both, and exactly what was reported from the server.
        currencyModule.setCharacterEntered(
                (playerId, characterId) -> {
                    org.bukkit.entity.Player player = getServer().getPlayer(playerId);
                    if (player != null) {
                        pileRegistry.showPilesTo(player, characterId);
                        // Und B11s Beute gleich mit. Dieselbe Falle, derselbe Moment - beide
                        // Mechanismen haengen an showEntity, und das ist Zustand der VERBINDUNG.
                        //
                        // Der Haken gehoert B08b, aber diese Zeile steht im Plugin, und das
                        // Plugin ist der Kompositionswurzel: es darf beide Bloecke kennen. B11
                        // haengt dadurch NICHT an B08b - es haengt an einem Rueckruf, den die
                        // Verdrahtung setzt.
                        if (itemDropVisibility != null) {
                            itemDropVisibility.showTo(player, characterId);
                        }
                    }
                });

        rpg.platform.currency.CoinDropListener coinDrops =
                new rpg.platform.currency.CoinDropListener(
                        getServer(), planner, piles, pileRegistry, currency, getLogger());
        coinDrops.subscribeTo(eventBus);

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.currency.CoinPickupListener(
                                currency, sessionModule.registry(), pileRegistry, messages),
                        this);

        registerCurrencyWindow(config, currency);
        closeTheTwoShippedBlocks(currency);

        getLogger()
                .info(
                        "[currency] listeners registered - piles despawn after "
                                + config.pileDespawn().toSeconds()
                                + "s, at most "
                                + config.maxPiles()
                                + " at once");
    }

    /**
     * B10s zweite Haelfte: Horden entstehen in B09s Bereichen, und Vanillas eigenes Spawnen ist aus
     * (US2, US2b).
     *
     * <p>Nach {@link #assembleCombatLayer()} und {@link #assembleProgressionLayer()}, weil die drei
     * uebernommenen Anbieter (Werte, Erfahrung, Coins) dort schon aus {@code mobs.yml} bedient
     * werden - eine gesetzte Kreatur soll sofort die richtigen Zahlen tragen.
     *
     * <p>{@code VanillaSpawnSuppressor.applyTo} laeuft VOR {@code HordeSweep.ensureScheduledForPopulatedZones}:
     * die Spielregeln sollen greifen, bevor dieser Block anfaengt, selbst zu setzen.
     */
    private void assembleMobLayer() {
        rpg.platform.mob.VanillaSpawnSuppressor suppressor =
                new rpg.platform.mob.VanillaSpawnSuppressor(getLogger());
        suppressor.applyTo(getServer());
        getServer().getPluginManager().registerEvents(suppressor, this);

        getServer()
                .getPluginManager()
                .registerEvents(new rpg.platform.mob.DaylightBurnSuppressor(), this);

        rpg.platform.mob.PaperMobPlacer placer = new rpg.platform.mob.PaperMobPlacer(getLogger());
        CombatPipeline mobCombatPipeline = registry.getService(CombatPipeline.class);
        mobSweep =
                new rpg.platform.mob.HordeSweep(
                        getServer(),
                        scheduler,
                        zoneModule::zones,
                        zoneTracker,
                        mobModule::config,
                        mobModule.registry(),
                        mobModule.bosses(),
                        // B05 rechnet den Kampfzustand ohnehin lazy aus Zeitstempeln - eine zweite
                        // Buchfuehrung waere eine zweite Wahrheit (FR-022, research.md).
                        mobCombatPipeline::isInCombat,
                        placer,
                        Clock.systemUTC(),
                        getLogger());
        mobSweep.subscribeTo(eventBus);
        getServer().getPluginManager().registerEvents(mobSweep, this);
        // Fuer Zonen, die beim Start schon Spieler haben - fuer die feuert kein ZoneChangedEvent
        // mehr, das dieser Zuhoerer sehen koennte (etwa nach einem /rpg reload waehrend Betrieb
        // waere das nicht noetig, aber beim allerersten Start schon).
        mobSweep.ensureScheduledForPopulatedZones();

        // US7: die letzte offene Zusage aus B08 - solange ein Klon steht, ziehen eigene
        // Kreaturen ihn an statt des Rogue (FR-039 bis FR-041, research.md R9). Nach
        // assembleAbilityLayer(), das summonEffect erst anlegt.
        rpg.platform.mob.CloneAggroListener cloneAggro =
                new rpg.platform.mob.CloneAggroListener(
                        getServer(), mobModule::config, Clock.systemUTC());
        getServer().getPluginManager().registerEvents(cloneAggro, this);
        summonEffect.setAggressionRedirect(cloneAggro::registerClone);
        // Und B11 fragt dieselbe Liste: ein Klon nutzt weder Waffe noch Ruestung seines
        // Beschwoerers ab (FR-041a). Eine zweite Liste dafuer waere eine zweite Wahrheit.
        this.cloneRegistry = cloneAggro;

        getLogger()
                .info(
                        "[mob] phase=START state=SWEEP_ARMED - the budget is now the only source of"
                                + " living creatures (FR-018c)");
    }

    /**
     * B11s Beute: was ein Tod hinterlässt, und wem es gehört (US2).
     *
     * <p><b>Nach B10</b>, weil die Beutetabellen Arten und Regionen nennen — und nach B06, weil die
     * Party mitentscheidet, wer den nächsten Gegenstand bekommt.
     *
     * <p><b>Zwei Dinge werden hier geteilt statt gebaut.</b> Die Eigentumsmechanik für liegende
     * Gegenstände kommt aus {@code rpg.platform.drop} und ist dieselbe, die B08b für Coin-Haufen
     * benutzt (ADR-039). Und wer worauf Anspruch hat, entscheidet {@code LootPlanner} in
     * {@code rpg-core} — bukkit-frei, wie {@code CoinDropPlanner}.
     */
    private void assembleItemLayer() {
        rpg.platform.item.ItemStackFactory itemFactory =
                new rpg.platform.item.ItemStackFactory(itemModule, messages);

        rpg.platform.drop.OwnedDropPlatform dropPlatform =
                rpg.platform.drop.OwnedDropPlatform.vanilla(this);
        rpg.platform.drop.OwnedDropRegistry dropRegistry =
                new rpg.platform.drop.OwnedDropRegistry(dropPlatform);
        // Vorgealtert wie ein Coin-Haufen: es gibt keinen Setter fuer die Verfallszeit, und Beute
        // soll nicht laenger liegen als Coins.
        rpg.platform.drop.OwnedDrops drops =
                new rpg.platform.drop.OwnedDrops(dropPlatform, dropRegistry, 0);

        PartyRegistry parties = registry.getService(PartyRegistry.class);
        StatEngine stats = registry.getService(StatEngine.class);

        rpg.core.item.LootPlanner lootPlanner =
                new rpg.core.item.LootPlanner(
                        itemModule::config,
                        parties,
                        // Dieselbe Reichweitenpruefung, die B06 fuer Erfahrung benutzt.
                        () -> new PaperProximityCheck(getServer()),
                        // Und dieselbe Zahl aus progression.yml. Eine zweite in items.yml waere
                        // eine zu viel: niemand koennte erklaeren, warum Erfahrung und Beute
                        // unterschiedlich weit reichen.
                        () -> progressionModule.config().partyRange(),
                        stats::characterIdOf,
                        new rpg.core.item.PartyLootRotation(),
                        new java.util.Random());

        rpg.platform.item.LootDropListener lootDrops =
                new rpg.platform.item.LootDropListener(
                        getServer(),
                        lootPlanner,
                        itemFactory,
                        drops,
                        this::onlinePlayerOfCharacter,
                        this::isBossKind,
                        eventBus,
                        getLogger());
        lootDrops.subscribeTo(eventBus);

        // Das zweite Schloss. Unsichtbarkeit ist Darstellung, und Darstellung ist niemals die
        // Autoritaet (Constitution VI) - hier wird der CHARAKTER geprueft, den Vanillas setOwner
        // nicht kennt (ADR-011).
        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.drop.OwnedDropPickupListener(
                                dropRegistry, this::activeCharacterOf),
                        this);

        // showEntity haengt an der VERBINDUNG. Ohne diese Zeile bliebe Beute nach einem Relogin
        // unsichtbar, waehrend beide Schloesser weiter passen - unsichtbar aber aufsammelbar ist
        // das Schlechteste von beidem. Dieselbe Falle, die B08b fuer Coin-Haufen gefunden hat.
        itemDropVisibility = dropRegistry;

        wireConsumables(stats, itemFactory);
        wireVendors(itemFactory);
        wireWear(stats);

        getLogger()
                .info(
                        "[item] phase=START state=LOOT_ARMED - loot belongs to one character,"
                                + " and in a party it rotates (FR-026b)");
    }

    /**
     * Verschleiß: Ausrüstung wird schwächer statt kaputt, und der Tod kostet ein Vielfaches (US5).
     *
     * <p><b>Zwei Anknüpfungen, und die erste ist nicht die aus dem Aufgabenzettel.</b> Der laufende
     * Verschleiß hängt an B05s {@code DamageInterceptor} und nicht an {@code DamageDealtEvent}: das
     * aggregierte Ereignis trägt keine {@code DamageOrigin}, also ließe sich ein Autoattack nicht von
     * einer Fähigkeit unterscheiden (FR-041), und es trägt den Schaden nach der Abwehr, wo FR-040a
     * den davor verlangt. Der Tod dagegen hängt am Ereignis, weil er nichts davon braucht.
     */
    private void wireWear(StatEngine stats) {
        rpg.core.item.DefaultGearConditions conditions = gearConditionModule.conditions();

        rpg.core.combat.CombatPipeline pipeline =
                registry.getService(rpg.core.combat.CombatPipeline.class);
        pipeline.registerInterceptor(
                new rpg.platform.item.WearInterceptor(
                        conditions,
                        stats::characterIdOf,
                        holderId -> cloneRegistry != null && cloneRegistry.isClone(holderId)));

        new rpg.platform.item.WearListener(conditions, getLogger()).subscribeTo(eventBus);

        // Die Warnung an den Spieler. Die Entscheidung "jetzt sagen" steckt schon im Ereignis
        // (FR-051) - sie hier ein zweites Mal zu treffen waere die zuverlaessigste Art, zwei
        // Meldungen fuer einen Treffer zu erzeugen.
gearDisplay =
                new rpg.platform.item.GearConditionDisplay(
                        messages,
                        conditions,
                        this::onlinePlayerOfCharacter,
                        // Welcher getragene Gegenstand zu welcher Leiter gehoert, sagt B07 - der
                        // Vermerk hat ein Format, und es gehoert dort hin (FR-079).
                        classesModule.boundEquipment()::expectedTag);
        gearDisplay.subscribeTo(eventBus);

        // FR-056: der bezahlte Weg beim Haendler ist die EINZIGE Instandsetzung. Ein offener Amboss
        // waere der billigere, und niemand ginge je zum Haendler - die Coin-Senke aus ADR-017 haette
        // dann kein Wasser.
        getServer()
                .getPluginManager()
                .registerEvents(new rpg.platform.item.RepairRouteLockListener(messages), this);

        getLogger()
                .info(
                        "[item] phase=START state=WEAR_ARMED - gear gets weaker, never broken"
                                + " (FR-038), and a death costs a multiple of a fight (FR-043)");
    }

    /**
     * Der Händler: einer je Region, und beide Kaufwege gehen durch die vorhandenen Routen (US4).
     *
     * <p><b>Er ist kein Mob.</b> Er wird nie in B10s {@code HordeRegistry} eingetragen und zählt
     * deshalb nicht gegen das Budget (FR-059) — das folgt daraus, dass B10 nur zählt, was B10 selbst
     * gesetzt hat, und ist keine Ausnahme, die jemand pflegen muss.
     *
     * <p><b>Zwei Nähte, beide auf vorhandene Blöcke gerichtet</b> (FR-079): der Stufenaufstieg ist
     * B08bs {@code EquipmentPurchase::buyNext}, die Bindungsfrage ist B07s
     * {@code BoundEquipment::isBound}. Beide werden hier verknüpft und nirgends nachgebaut.
     */
    private void wireVendors(rpg.platform.item.ItemStackFactory itemFactory) {
        rpg.core.currency.Currency currency = registry.getService(rpg.core.currency.Currency.class);
        rpg.core.classes.BoundEquipment boundEquipment =
                classesModule.boundEquipment();
        rpg.core.currency.EquipmentPurchase tierPurchase =
                new rpg.core.currency.EquipmentPurchase(
                        classesModule.tierAdvance(),
                        currency,
                        this::classOfCharacter,
                        classesModule::progressOf,
                        getLogger());

        rpg.core.item.VendorTransaction transactions =
                new rpg.core.item.VendorTransaction(
                        itemModule::config,
                        currency,
                        // Platz im Inventar - gefragt VOR der Buchung (FR-064). Ohne Spieler online
                        // gibt es kein Inventar, und dann kommt der Kauf ohnehin nicht zustande.
                        (characterId, templateKey, amount) ->
                                onlinePlayerOfCharacter(characterId)
                                        .map(player -> player.getInventory().firstEmpty() >= 0)
                                        .orElse(false));

        vendorListener =
                new rpg.platform.item.VendorListener(
                        itemModule::config,
                        new rpg.platform.item.VendorMenu(itemFactory, messages),
                        transactions,
                        tierPurchase::buyNext,
                        // Was der naechste Aufstieg kostet und ab welchem Level er geht - damit
                        // der Knopf es SAGT, statt dass ein Klick es herausfindet.
                        (characterId, slot) -> upgradeOfferFor(tierPurchase, characterId, slot),
                        new rpg.core.item.GearRepair(
                                itemModule::config,
                                gearConditionModule.conditions(),
                                // Welche Stufe erreicht ist, weiss B07 - eine zweite Antwort hier
                                // waere eine zweite Wahrheit (FR-079).
                                (characterId, slot) ->
                                        classesModule
                                                .progressOf(characterId)
                                                .map(progress -> progress.tierOf(slot))
                                                .orElse(rpg.core.classes.ClassProgress.INITIAL_TIER),
                                currency),
                        gearConditionModule.conditions(),
                        cosmeticModule.cosmetics(),
                        boundEquipment::isBound,
                        currency,
                        itemFactory,
                        this::activeCharacterOf,
                        messages,
                        getLogger());
        getServer().getPluginManager().registerEvents(vendorListener, this);

        trashCommand =
                new rpg.platform.item.TrashCommand(
                        messages, Clock.systemUTC(), boundEquipment::isBound);
        var trash = getCommand("trash");
        if (trash == null) {
            // plugin.yml und diese Stelle muessen sich einig sein. Es zu sagen ist besser als ein
            // Befehl, den es still nicht gibt - dasselbe Muster wie bei /coins.
            getLogger().severe("[item] /trash is not declared in plugin.yml - not registered");
        } else {
            trash.setExecutor(trashCommand);
        }

        vendorNpcs = new rpg.platform.item.VendorNpc(getLogger());
        placeVendors();
    }

    /**
     * Setzt je Region mit Safe-Core einen Händler, ein paar Schritte neben dem Ankunftspunkt.
     *
     * <p><b>Neben, nicht auf.</b> Wer nach einer Reise ankommt, soll nicht in einem Dorfbewohner
     * stehen — und der Ankunftspunkt ist der einzige Ort, den B09 je Region kennt.
     */
    private void placeVendors() {
        int placed = 0;
        for (rpg.core.zone.Zone zone : zoneModule.zones().all()) {
            java.util.Optional<rpg.core.scheduler.WorldPosition> point =
                    zoneModule.zones().respawnPointOf(zone.key());
            if (point.isEmpty()) {
                continue;
            }
            org.bukkit.World world = getServer().getWorld(point.get().worldId());
            if (world == null) {
                getLogger()
                        .warning(
                                "[item] vendor: the world of "
                                        + zone.key()
                                        + " is not loaded - no merchant there this session");
                continue;
            }
            org.bukkit.Location where =
                    new org.bukkit.Location(
                            world, point.get().x() + VENDOR_OFFSET, point.get().y(), point.get().z());
            if (vendorNpcs.place(where, zone.key()).isPresent()) {
                placed++;
            }
        }
        getLogger()
                .info(
                        "[item] phase=START state=VENDORS_PLACED count="
                                + placed
                                + " - one per region, none of them counted against the mob budget"
                                + " (FR-057, FR-059)");
    }

    /**
     * Verbrauchbares: Tränke wirken, sind danach verbraucht, und ein zweiter direkt hinterher ist
     * nicht der Weg (US3).
     *
     * <p><b>Der zeitliche Beitrag reitet auf B08s Durchlauf</b>, nicht auf einem eigenen — das ist
     * es, was hundert Tränke davon abhält, hundert Aufgaben zu werden (FR-034, Prinzip II).
     */
    private void wireConsumables(StatEngine stats, rpg.platform.item.ItemStackFactory itemFactory) {
        rpg.core.item.ConsumableCooldown cooldowns =
                new rpg.core.item.ConsumableCooldown(Clock.systemUTC());

        // Zwei Fragen an B04, nicht die ganze Engine - dieselbe Ueberlegung wie bei B08bs
        // CharacterLookup.
        consumableBuffs =
                new rpg.core.item.ConsumableBuffs(
                        new rpg.core.item.ConsumableBuffs.BuffSink() {
                            @Override
                            public void apply(
                                    java.util.UUID holderId, rpg.core.stats.ModifierSet set) {
                                stats.apply(holderId, set);
                            }

                            @Override
                            public void remove(
                                    java.util.UUID holderId, rpg.core.stats.SourceId source) {
                                stats.remove(holderId, source);
                            }
                        },
                        Clock.systemUTC());

        rpg.platform.item.ConsumableUseListener.Resources resources =
                new rpg.platform.item.ConsumableUseListener.Resources() {
                    @Override
                    public double currentHealth(java.util.UUID holderId) {
                        return stats.resources(holderId).currentHealth();
                    }

                    @Override
                    public double maxHealth(java.util.UUID holderId) {
                        return stats.resources(holderId).maxHealth();
                    }

                    @Override
                    public double currentMana(java.util.UUID holderId) {
                        return stats.resources(holderId).currentMana();
                    }

                    @Override
                    public double maxMana(java.util.UUID holderId) {
                        return stats.resources(holderId).maxMana();
                    }

                    @Override
                    public void changeHealth(java.util.UUID holderId, double delta) {
                        stats.changeHealth(holderId, delta);
                    }

                    @Override
                    public void changeMana(java.util.UUID holderId, double delta) {
                        stats.changeMana(holderId, delta);
                    }
                };

        java.util.function.Function<java.util.UUID, java.util.Optional<java.util.UUID>> holderOf =
                stats::holderOf;

        rpg.core.item.ConsumableUse rule =
                new rpg.core.item.ConsumableUse(
                        cooldowns,
                        rpg.platform.item.ConsumableUseListener.wouldDoSomething(resources, holderOf));

        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.item.ConsumableUseListener(
                                itemModule,
                                rule,
                                consumableBuffs,
                                resources,
                                this::activeCharacterOf,
                                holderOf,
                                this::levelOfCharacter,
                                this::classOfCharacter,
                                messages,
                                getLogger()),
                        this);

        // Der Aufschlag eines geworfenen Tranks. Vanilla wirft und verbraucht; hier wirkt es -
        // auf JEDEN Getroffenen, nicht nur auf den Werfer.
        getServer()
                .getPluginManager()
                .registerEvents(
                        new rpg.platform.item.PotionSplashListener(
                                itemModule,
                                consumableBuffs,
                                resources,
                                stats::characterIdOf,
                                messages,
                                getLogger()),
                        this);

        showConsumableCooldowns(cooldowns);
    }

    /**
     * Hängt die Trank-Abklingzeiten an das Cooldown-Overlay (B13 US3, Erweiterung von FR-030).
     *
     * <p><b>Warum überhaupt hier und nicht in {@code wireUi}</b>: {@code ConsumableCooldown} entsteht
     * erst in der Gegenstandsschicht, lange nach dem HUD. Das Overlay wartet als Feld darauf — und
     * bis dahin zeigt es die Fähigkeiten, was der ältere und wichtigere Teil ist.
     *
     * <p><b>Nur Vorlagen mit einer Abklingzeit.</b> {@code items.yml} gibt sie nicht jedem Trank; wer
     * keine hat, kühlt nie ab, und ihn jede Sekunde zu fragen wäre Arbeit für eine Antwort, die
     * immer null lautet.
     */
    private void showConsumableCooldowns(rpg.core.item.ConsumableCooldown cooldowns) {
        if (cooldownOverlay == null) {
            // Kann nicht vorkommen - wireUi laeuft vor der Gegenstandsschicht. Falls doch, ist die
            // Trankanzeige das Falsche, um daran den Start scheitern zu lassen.
            getLogger()
                    .warning(
                            "[ui] phase=START state=CONSUMABLE_OVERLAY_SKIPPED"
                                    + " - the overlay was not built, potions will not grey out");
            return;
        }
        cooldownOverlay.alsoShow(
                new rpg.platform.ui.AbilityCooldownOverlay.ConsumableCooldowns() {

                    @Override
                    public java.util.Map<String, java.time.Duration> remainingFor(
                            java.util.UUID characterId) {
                        java.util.Map<String, java.time.Duration> cooling =
                                new java.util.LinkedHashMap<>();
                        for (java.util.Map.Entry<String, rpg.core.item.ItemTemplate> entry :
                                itemModule.templates().entrySet()) {
                            rpg.core.item.ItemTemplate template = entry.getValue();
                            if (template.category() != rpg.core.item.ItemCategory.CONSUMABLE
                                    || template.effect() == null
                                    || template.effect().cooldown() == null) {
                                continue;
                            }
                            // Die Restzeit kommt aus B11 - dieselbe Methode, die auch entscheidet,
                            // ob ein zweiter Schluck abgelehnt wird. Eine zweite Rechnung hier
                            // waere eine zweite Wahrheit darueber, wann der Trank bereit ist.
                            java.time.Duration left =
                                    cooldowns.remaining(
                                            characterId, entry.getKey(), template.effect().cooldown());
                            if (!left.isZero() && !left.isNegative()) {
                                cooling.put(entry.getKey(), left);
                            }
                        }
                        return cooling;
                    }

                    @Override
                    public java.util.Optional<String> materialOf(String templateKey) {
                        return itemModule.template(templateKey).map(rpg.core.item.ItemTemplate::material);
                    }
                });
    }

    /** Das Level eines Charakters — B06 besitzt die Antwort. */
    private int levelOfCharacter(java.util.UUID characterId) {
        return progressionModule.progression().levelOf(characterId).orElse(1);
    }

    /** Die Klasse eines Charakters — B07 besitzt die Antwort. */
    private java.util.Optional<rpg.core.session.CharacterClass> classOfCharacter(
            java.util.UUID characterId) {
        return abilityModule.registry().classOf(characterId);
    }

    /**
     * Baut die getragene Ausrüstung neu — nach einem Stufenaufstieg.
     *
     * <p><b>Dieselbe Reihenfolge wie beim Eintritt</b>: erst die Stücke, dann der Zustand darauf.
     * Andersherum stünde die Zustandszeile auf Stücken, die gleich überschrieben werden.
     *
     * <p>Hinter einer Barriere: der Kauf ist zu diesem Zeitpunkt gebucht und die Stufe vergeben.
     * Ein Fehler beim Anziehen darf daran nichts ändern — der Spieler sieht dann seine alte
     * Rüstung, was beim nächsten Eintritt von selbst richtig wird (Prinzip VI).
     */
    private void reapplyEquipment(
            ClassEquipmentApplier equipment, org.bukkit.entity.Player player, java.util.UUID characterId) {
        try {
            equipment.apply(player, characterId);
            refreshGearDisplay(player, characterId);
        } catch (RuntimeException failure) {
            getLogger()
                    .warning(
                            "[classes] could not re-apply equipment after a tier advance for "
                                    + characterId
                                    + ": "
                                    + failure);
        }
    }

    /**
     * Zeichnet Haltbarkeitsbalken und Zustandszeile auf die getragene Ausrüstung.
     *
     * <p>Still, solange B11 nicht verdrahtet ist — dieselbe Richtung wie beim Verschleißfaktor:
     * eine fehlende Anzeige ist unschön, eine Ausnahme im Eintrittspfad wäre ein Spieler, der
     * nicht in die Welt kommt.
     */
    private void refreshGearDisplay(org.bukkit.entity.Player player, java.util.UUID characterId) {
        if (gearDisplay == null) {
            return;
        }
        try {
            gearDisplay.refresh(player, characterId);
        } catch (RuntimeException failure) {
            getLogger()
                    .warning(
                            "[item] could not draw the gear condition of "
                                    + characterId
                                    + ": "
                                    + failure);
        }
    }

    /**
     * Was der nächste Stufenaufstieg dieser Leiter kostet — für die Beschreibung am Knopf.
     *
     * <p>Leer heißt „Höchststufe": {@code costOfNext} antwortet dort leer, weil es keine nächste
     * Stufe zu bepreisen gibt. Das ist kein Fehler, sondern die Auskunft, die der Knopf braucht.
     */
    private rpg.platform.item.VendorMenu.UpgradeOffer upgradeOfferFor(
            rpg.core.currency.EquipmentPurchase tiers,
            java.util.UUID characterId,
            rpg.core.classes.LadderSlot slot) {
        java.util.Optional<rpg.core.currency.CostSpec> cost = tiers.costOfNext(characterId, slot);
        if (cost.isEmpty()) {
            return rpg.platform.item.VendorMenu.UpgradeOffer.atTop();
        }
        int nextTier =
                classesModule
                                .progressOf(characterId)
                                .map(progress -> progress.tierOf(slot))
                                .orElse(rpg.core.classes.ClassProgress.INITIAL_TIER)
                        + 1;
        java.util.OptionalInt requiredLevel =
                classesModule
                        .classOf(characterId)
                        .map(
                                characterClass ->
                                        java.util.OptionalInt.of(
                                                classesModule
                                                        .tierAdvance()
                                                        .requiredLevelFor(
                                                                characterClass, slot, nextTier)))
                        .orElseGet(java.util.OptionalInt::empty);
        return new rpg.platform.item.VendorMenu.UpgradeOffer(
                java.util.OptionalLong.of(cost.get().coins()),
                requiredLevel,
                java.util.OptionalInt.of(nextTier));
    }

    /**
     * Ob dieser Charakter in dieser Leiter die Hoechststufe traegt — B07s Antwort.
     *
     * <p>Falsch fuer einen Charakter ohne Klasse und fuer einen, der nicht geladen ist. Das ist die
     * sichere Richtung: eine Kosmetik nicht anwenden zu koennen ist eine Auskunft, sie faelschlich
     * anzuwenden waere ein ueberschriebener Stufentrim (B07/FR-016).
     */
    private boolean isAtTopTier(java.util.UUID characterId, rpg.core.classes.LadderSlot slot) {
        return classesModule
                .classOf(characterId)
                .flatMap(
                        characterClass ->
                                classesModule
                                        .progressOf(characterId)
                                        .map(
                                                progress ->
                                                        classesModule
                                                                .config()
                                                                .definition(characterClass)
                                                                .ladder(slot)
                                                                .isTop(progress.tierOf(slot))))
                .orElse(false);
    }

    /**
     * Der Verschleissfaktor einer Leiter — B07s Naht, aufgeloest beim Aufruf.
     *
     * <p>Vor dem Start des Moduls und fuer einen unbekannten Charakter ist es {@code 1.0}: volle
     * Werte. Die Alternative — einen Faktor unter eins anzunehmen, solange nichts geladen ist —
     * uebersetzte einen Ladefehler in eine stille Schwaechung, und niemand kaeme auf die Idee, dort
     * zu suchen.
     */
    private double gearFactorOf(java.util.UUID characterId, rpg.core.classes.LadderSlot slot) {
        if (gearConditionModule == null || gearConditionModule.conditions() == null) {
            return 1.0;
        }
        return gearConditionModule.conditions().factorOf(characterId, slot);
    }

    /** Der Charakter, den dieser Spieler gerade spielt — B03 besitzt die Antwort. */
    private java.util.Optional<java.util.UUID> activeCharacterOf(java.util.UUID playerId) {
        org.bukkit.entity.Player player = getServer().getPlayer(playerId);
        return player == null ? java.util.Optional.empty() : characterIdOf(player);
    }

    /** Der Spieler, der diesen Charakter gerade spielt — leer, wenn er offline ist. */
    private java.util.Optional<org.bukkit.entity.Player> onlinePlayerOfCharacter(
            java.util.UUID characterId) {
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            if (characterIdOf(online).filter(characterId::equals).isPresent()) {
                return java.util.Optional.of(online);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Ob diese Artkennung zu einem Boss gehört.
     *
     * <p>B10 führt die Bosse in {@code mobs.yml} unter {@code hordes.<zone>.boss.kind}, nicht über
     * ein Kennzeichen an der Art — die Antwort kommt deshalb von dort und nicht aus einer zweiten
     * Liste.
     */
    private boolean isBossKind(String kindKey) {
        for (rpg.core.mob.HordeSpec horde : mobModule.config().hordes().values()) {
            if (horde.boss() != null && horde.boss().kindKey().equals(kindKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What this whole block was built for: B07 and B08 stop being unfinished.
     *
     * <p>Both shipped with a hole in them, named rather than filled (Workflow rule 5). B07 carries a
     * {@code cost} block on every equipment tier and passes it through unread; B08's rank advance
     * could not fail for want of money because there was no money. Neither guessed a price, and this
     * is where the guessing would have shown - it does not, because there is nothing to reconcile.
     *
     * <p>Two lines, and they are the point of ADR-027.
     */
    private void closeTheTwoShippedBlocks(rpg.core.currency.DefaultCurrency currency) {
        // FR-050: every configured price is checked now, not the first time somebody tries to pay
        // one. A key nobody can charge would leave a tier quietly free.
        rpg.core.currency.CostBlockValidator.validateClasses(classesModule.config());

        // FR-051: the rank advance gets its price check - last, after unlock and maximum rank, so a
        // refusal for any other reason still costs nothing (FR-052).
        abilityRuntime.setRankCost(
                new rpg.core.currency.AbilityRankCost(currency, getLogger()));

        getLogger().info("[currency] B07 tier costs validated, B08 rank costs installed");
    }

    /**
     * The window and the command, both provisional (ADR-028).
     *
     * <p>B14 replaces the command and B13 takes over the display; what stays is
     * {@code CurrencyAdmin} and {@code CoinLedger} underneath. That is the whole point of keeping
     * this method short: everything it builds is meant to be thrown away.
     */
    private void registerCurrencyWindow(
            rpg.core.currency.CurrencyConfig config, rpg.core.currency.DefaultCurrency currency) {
        rpg.platform.ui.CurrencyMenu menu =
                new rpg.platform.ui.CurrencyMenu(
                        messages, uiMenuFrame(), config.historyPageSize());
        rpg.platform.ui.CurrencyMenuListener menuListener =
                new rpg.platform.ui.CurrencyMenuListener(
                        menu, currencyModule.ledger(), scheduler, getLogger());
        getServer().getPluginManager().registerEvents(menuListener, this);

        rpg.plugin.command.CoinsCommand coins =
                new rpg.plugin.command.CoinsCommand(
                        getServer(),
                        sessionModule.registry(),
                        currency,
                        currencyModule.admin(),
                        menuListener,
                        messages,
                        // A character who is not loaded still has a balance, and the window has to
                        // show it (FR-042). The lookup is a database read - fine here, because a
                        // command is not a game event.
                        characterId ->
                                currencyModule
                                        .balances()
                                        .find(characterId)
                                        .join()
                                        .map(rpg.core.currency.CharacterBalance::balance)
                                        .orElse(0L));

        var command = getCommand("coins");
        if (command == null) {
            // plugin.yml and this method have to agree; if they do not, saying so beats a command
            // that silently does not exist.
            getLogger().severe("[currency] /coins is not declared in plugin.yml - not registered");
            return;
        }
        command.setExecutor(coins);
        command.setTabCompleter(coins);

        registerXpCommand();
    }

    /**
     * {@code /xp} - befristet hier, wie {@code /coins} (ADR-028).
     *
     * <p>Eine Korrektur, die nur mit einem Datenbankwerkzeug moeglich ist, macht niemand. Geben laeuft
     * ueber den gewoehnlichen Weg mit {@code XpSource.ADMIN}; Nehmen kann das nicht, weil B06 einen
     * negativen Betrag ausdruecklich abweist - dafuer gibt es {@code setProgress}, und das schreibt
     * mit, wer es war (FR-024b).
     */
    private void registerXpCommand() {
        if (progressionModule == null) {
            return;
        }
        rpg.plugin.command.XpCommand xp =
                new rpg.plugin.command.XpCommand(
                        getServer(),
                        sessionModule.registry(),
                        progressionModule.progression(),
                        progressionModule.config().curve(),
                        messages);
        var command = getCommand("xp");
        if (command == null) {
            getLogger().severe("[progression] /xp is not declared in plugin.yml - not registered");
            return;
        }
        command.setExecutor(xp);
        command.setTabCompleter(xp);
    }

    /**
     * Removes a player from their party when their session ends (FR-034).
     *
     * <p>Separate from the attachment inside {@code ProgressionModule}, which handles the progress
     * state: the party lives in the plugin layer because it is assembled here, and one attachment
     * reaching across both would tie two lifetimes together that have nothing to do with each other.
     */
    private record PartySessionAttachment(PartyRegistry parties)
            implements rpg.core.session.SessionAttachment {

        @Override
        public String id() {
            return ProgressionModule.ID + "-party";
        }

        @Override
        public void onSessionOpened(
                rpg.core.session.PlayerSession session, rpg.core.session.SessionBundle bundle) {
            // Nothing to restore - a party is never persisted (FR-029).
        }

        @Override
        public void onSessionClosing(java.util.UUID playerId) {
            parties.onSessionEnded(playerId);
        }
    }

    /** The configuration loader; also the entry point B14's reload command will use. */
    public ConfigLoader configLoader() {
        return configLoader;
    }

    /** The registry other modules resolve services through. */
    public DefaultModuleRegistry registry() {
        return registry;
    }

    /** The internal event bus. */
    public EventBus eventBus() {
        return eventBus;
    }

    /** The scheduler abstraction; the only sanctioned way to schedule work (ADR-007). */
    public Scheduler scheduler() {
        return scheduler;
    }

    /**
     * The session lifecycle, so a bootstrap test can assert which blocks hooked into it.
     *
     * <p>Deliberately not published as a service - only the plugin assembles attachments, and a
     * block reaching for the lifecycle through the registry would be able to add one from anywhere.
     */
    public rpg.core.session.DefaultSessionLifecycle sessionLifecycle() {
        return sessionModule == null ? null : sessionModule.lifecycle();
    }

    /**
     * The stat engine as it was assembled, for the bootstrap test.
     *
     * <p>Not the {@link StatEngine} from the registry: what needs asserting is which base-value
     * suppliers a fully wired server ends up with, and that is not part of the interface other blocks
     * use.
     */
    public rpg.core.stats.DefaultStatEngine statEngine() {
        return statsModule.engine();
    }

    /**
     * The damage pipeline as it was assembled, for the bootstrap test.
     *
     * <p>Same reason as {@link #statEngine()}: what needs asserting is which interceptors a fully
     * wired server ends up with, and that is not part of the {@code CombatPipeline} interface other
     * blocks use.
     */
    public rpg.core.combat.DefaultCombatPipeline combatPipeline() {
        return combatModule == null ? null : combatModule.pipeline();
    }

    /**
     * The regeneration as it was assembled, for the bootstrap test.
     *
     * <p>Same reason as {@link #statEngine()}, plus one of its own: this is the piece that rides the
     * ability sweep, and whether a wounded player standing still actually heals is a property of the
     * WIRED server - it rode the right sweep with the wrong ids for a whole release and looked fine
     * in every unit test.
     */
    public rpg.core.ability.ResourceRegeneration abilityRegeneration() {
        return abilityModule == null ? null : abilityModule.regeneration();
    }

    /**
     * Eine Nachkommastelle, mit Punkt statt Komma.
     *
     * <p>Ganze Zahlen wären hier falsch: eine Milderung von zehn Prozent auf einen Schlag von vier
     * Herzen ist 0,4 — gerundet null, und die Meldung sagte dann, es sei nichts abgefangen worden.
     * {@code Locale.ROOT}, weil ein deutsches Komma in einer englischen Zeile falsch aussieht.
     */
    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    /**
     * B09's placement, for the bootstrap test.
     *
     * <p>Same reason as {@link #statEngine()} and {@link #combatPipeline()}: what needs asserting is
     * that a fully wired server actually knows where a player stands. That is not part of the
     * {@code Zones} contract other blocks use, and without it the only observable consequence of the
     * whole placement chain would be a damage refusal - which fails for half a dozen unrelated
     * reasons and would send the next reader hunting in the wrong block.
     */
    public rpg.core.zone.ZoneTracker zoneTracker() {
        return zoneTracker;
    }

    /**
     * B11s Fassade, wie sie verdrahtet wurde — für den Bootstrap-Test.
     *
     * <p>Aus demselben Grund wie {@link #zones()}: dass die Modultests von B11 grün sind, sagt
     * nichts darüber, ob {@code items.yml} beim echten Start gelesen wird und die Fassade danach
     * antwortet. Genau diese Lücke hat B10 zweimal Rot gekostet.
     */
    public rpg.core.item.Items items() {
        return itemModule;
    }

    /**
     * B11s Verschleißzustand, für den Bootstrap-Test.
     *
     * <p><b>Warum gerade der eine Zugriff nach außen ist.</b> Die Naht aus dem Complexity Tracking
     * ist so gebaut, dass B07 sich mit {@code GearConditionFactor.NONE} <em>bitgenau</em> wie vorher
     * verhält — was heißt, dass eine vergessene Verdrahtung nichts rot macht. Der ganze Verschleiß
     * wäre gebaut und wirkungslos, und kein Test außer diesem würde es merken.
     */
    public rpg.core.item.GearConditions gearConditions() {
        return gearConditionModule == null ? null : gearConditionModule.conditions();
    }

    /** The zone query as it was assembled, for the bootstrap test. */
    public rpg.core.zone.Zones zones() {
        return zoneModule == null ? null : zoneModule.zones();
    }

    /**
     * Puts a character into play exactly as choosing one in the menu does, for the bootstrap test.
     *
     * <p><b>Not a shortcut, the real path</b> - session activation, inventory, class equipment,
     * ability hotbar, experience bar, in that order. A test that assembled a character by hand would
     * prove only that its own assembly works, and this is the one thing that has to be proven about
     * the wired server: that a player who joins can actually use what the blocks built.
     */
    public boolean enterCharacter(
            org.bukkit.entity.Player player, rpg.core.session.PlayerCharacter character) {
        // The zone placement (FR-017) lives inside enterGameState itself now, so it happens the same
        // way here and through the real selection menu - not as a second step only this method knew
        // to take.
        return characterEntry != null && characterEntry.enter(player, character);
    }

    /**
     * Places whoever this player is currently playing into their region (FR-017).
     *
     * <p>Called from the session observer and from {@code enterGameState} - the two moments a
     * holder's character can change. The first finds nobody to place for a session that still needs
     * a class chosen: no character is active yet at that point, so this silently does nothing and
     * the second call - once a character actually enters play - is what places them for real.
     */
    private void placeInZone(org.bukkit.entity.Player player) {
        if (zoneTracker == null) {
            return;
        }
        characterIdOf(player)
                .ifPresent(
                        characterId -> {
                            rpg.core.zone.ZoneStateStore store = zonePersistenceModule.store();
                            // A character this block has never placed starts in the start region
                            // (FR-037b). Absence of a row is the signal - the same inference B08b
                            // makes from a missing balance row.
                            if (store.isNewCharacter(characterId)) {
                                zoneTeleporter.teleport(
                                        player.getUniqueId(), zoneModule.zones().startPoint());
                                store.markSeen(characterId);
                            } else {
                                // A combat logout owes them a trip home (ADR-030, FR-041). Applied
                                // here rather than at the logout, because a teleport in that moment
                                // is not reliable on Paper.
                                store.pendingRespawnOf(characterId)
                                        .ifPresent(
                                                zoneKey -> {
                                                    zoneTeleporter.teleport(
                                                            player.getUniqueId(),
                                                            zoneRespawnRouting.respawnForZone(
                                                                    zoneKey));
                                                    store.clearPendingRespawn(characterId);
                                                    player.sendMessage(
                                                            messages.get(
                                                                    rpg.core.zone.ZoneMessageKeys
                                                                            .DIED_LOGOUT));
                                                });
                            }
                            zoneTracker.place(
                                    player.getUniqueId(),
                                    characterId,
                                    rpg.platform.zone.BukkitPositions.of(player.getLocation()));
                        });
    }

    /** The bootstrap phase, which decides whether the server accepts player sessions (FR-013). */
    public BootstrapState bootstrapState() {
        return bootstrapState;
    }
}

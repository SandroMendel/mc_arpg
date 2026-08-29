package rpg.core.statistics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;

/**
 * Start und Nachladen von B12 — nach dem Muster von {@code MobModule}.
 *
 * <h2>Was hier geprüft wird und nicht im Schema</h2>
 *
 * <p>Ob eine Vorlagen-ID aus {@code rewards} bei B11 existiert. Das Schema kann es nicht wissen:
 * {@code rpg.core.statistics} kennt {@code items.yml} nicht, und ein Verweis darauf wäre genau die
 * Abhängigkeit, die die Paketgrenze verhindert. Also läuft die Prüfung hier — und sie <b>bricht den
 * Start ab</b>, statt zu warten. Eine Belohnung mit Tippfehler wäre sonst ein Anspruch, der sich
 * erst am Saisonende als nicht einlösbar herausstellt, und zwar für den Spieler, der drei Monate
 * dafür gespielt hat.
 *
 * <h2>Was das Nachladen NICHT anfasst</h2>
 *
 * <p>Zwei Dinge (contracts/stats-config.md, Abschnitt „Nachladen"):
 *
 * <ul>
 *   <li><b>Ein eingefrorener Saisonendstand.</b> Er trägt seine eigene Gewichtung (FR-050); eine
 *       neue Konfiguration darf eine vergebene Platzierung nicht rückwirkend umsortieren.
 *   <li><b>Ein bereits angelegter Anspruch.</b> Er ist vergeben und verfällt nicht (ADR-045). Was
 *       sich nach dem Nachladen ändert, sind die Belohnungen <em>künftiger</em> Saisons.
 * </ul>
 *
 * <p>Beides ist kein Zufall der Umsetzung, sondern der Grund, aus dem der Endstand überhaupt in
 * einer eigenen Tabelle liegt statt jedes Mal neu gerechnet zu werden.
 */
public final class StatisticsModule implements Module {

    /** Modulkennung in der Registry. */
    public static final String ID = "statistics";

    private static final String CONFIG_FILE = "statistics.yml";

    private final Logger logger;
    private final Supplier<Set<String>> knownTemplates;
    private final List<Runnable> reloadListeners = new ArrayList<>();

    private ConfigHandle<StatisticsConfig> configHandle;

    /**
     * @param knownTemplates die Vorlagen-IDs aus B11, für die Belohnungsprüfung — als
     *     {@link Supplier}, weil B11 beim Bau dieses Moduls noch nicht geladen sein muss
     */
    public StatisticsModule(Logger logger, Supplier<Set<String>> knownTemplates) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.knownTemplates = Objects.requireNonNull(knownTemplates, "knownTemplates");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        // Erfassung haengt an B05/B06/B09, die Belohnungen an B11 - aber die Konfiguration dieses
        // Moduls braucht beim Start nur die Vorlagenliste.
        return List.of("item");
    }

    @Override
    public void start(ModuleContext context) {
        this.configHandle = loadConfig(context);
        StatisticsConfig config = configHandle.get();
        verifyRewardTemplatesExist(config);
        logger.info(
                "[statistics] phase=START state=LOADED - "
                        + config.seasons().all().size()
                        + " seasons, "
                        + config.score().weights().size()
                        + " weighted boards, "
                        + config.rewards().size()
                        + " rewarded places, refresh every "
                        + config.leaderboards().refreshInterval().toSeconds()
                        + "s");
    }

    @Override
    public void stop() {
        // Nichts zu leeren: dieser Block haelt keinen eigenen Bestand (FR-002). Was gezaehlt wurde,
        // liegt in player_statistic_daily und gehoert B02.
    }

    /** Die geltende Konfiguration. */
    public StatisticsConfig config() {
        if (configHandle == null) {
            throw new IllegalStateException("statistics module not started");
        }
        return configHandle.get();
    }

    /** Wird nach jedem erfolgreichen Nachladen gerufen. */
    public void onReload(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Nimmt eine neu geladene {@code statistics.yml} auf. Eine abgelehnte erreicht das hier nie.
     *
     * <p>Getauscht wird das Ganze. Was <b>nicht</b> mitgeht, steht im Klassenkommentar.
     */
    public void applyReloadedConfig() {
        if (configHandle == null) {
            return;
        }
        StatisticsConfig config = configHandle.get();
        verifyRewardTemplatesExist(config);
        logger.info(
                "[statistics] phase=RELOAD state=APPLIED - "
                        + config.seasons().all().size()
                        + " seasons; frozen season results and existing claims keep the weighting"
                        + " they were awarded with");
        for (Runnable listener : reloadListeners) {
            listener.run();
        }
    }

    /**
     * Jede Vorlage einer Belohnung muss es in {@code items.yml} geben.
     *
     * <p>Kann nicht im Schema stehen: dort ist B11 nicht bekannt. Und kann nicht warten: ein
     * Tippfehler wäre ein Anspruch, der am Saisonende ins Leere greift.
     */
    private void verifyRewardTemplatesExist(StatisticsConfig config) {
        Set<String> known = knownTemplates.get();
        if (known.isEmpty()) {
            // Ohne geladene Vorlagen kann hier nichts bewiesen werden - und eine Pruefung, die
            // stillschweigend nichts prueft, ist schlimmer als keine.
            throw new IllegalStateException(
                    "statistics configuration cannot be verified: no item templates loaded");
        }

        List<String> missing = new ArrayList<>();
        config.rewards()
                .forEach(
                        (place, reward) -> {
                            for (StatisticsConfig.ItemGrant grant : reward.items()) {
                                if (!known.contains(grant.template())) {
                                    missing.add("rewards." + place + ".items: " + grant.template());
                                }
                            }
                        });

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    CONFIG_FILE + ": unknown item template(s) - " + String.join(", ", missing));
        }
    }

    private ConfigHandle<StatisticsConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of(CONFIG_FILE), StatisticsConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[statistics] configuration rejected", invalid);
            throw new IllegalStateException(
                    "statistics configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}

package rpg.core.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;

/**
 * Start und Nachladen von B13 — nach dem Muster von {@code MobModule} und
 * {@code StatisticsModule}.
 *
 * <h2>Dieses Modul hält keinen Bestand</h2>
 *
 * <p>Es lädt eine Konfiguration und sonst nichts. <b>Kein Schema, keine Tabelle, keine
 * Migration</b> (FR-013b, SC-011) — B13 zeichnet nur, und ein Block, der nur zeichnet, speichert
 * nichts. {@link #stop()} hat deshalb wirklich nichts zu tun, und das ist eine Zusage und kein
 * Versehen: der Block lässt sich vollständig entfernen, ohne dass Spielerdaten fehlen.
 *
 * <h2>Warum die Konfiguration hier abgeholt und nicht weitergereicht wird</h2>
 *
 * <p>{@link #config()} liefert bei jedem Aufruf den aktuellen Stand. Wer sie sich einmal in ein
 * Feld legt, bekommt nach dem Nachladen die alte zurück — der Betreiber sieht dann eine Änderung,
 * die nicht stattfindet (FR-013c). Der Sammeltakt holt sie deshalb <b>je Durchlauf</b>; das kostet
 * einen Feldzugriff je Sekunde und spart die Fehlersuche, die sonst irgendwann kommt.
 *
 * <h2>Was das Nachladen nicht kann</h2>
 *
 * <p><b>Die Sprache wechselt erst beim Neustart.</b> {@code language} wird beim Start gelesen und
 * beim Nachladen bewusst nicht angewandt: die Sprachdatei ist zu diesem Zeitpunkt längst in
 * {@code Messages} gebunden, und ein halber Wechsel — neue Datei, alte Texte in schon gebauten
 * Fenstern — wäre schlechter als gar keiner. Der Start protokolliert das ausdrücklich.
 */
public final class UiModule implements Module {

    /** Modulkennung in der Registry. */
    public static final String ID = "ui";

    private static final String CONFIG_FILE = "ui.yml";

    private final Logger logger;
    private final List<Runnable> reloadListeners = new ArrayList<>();

    private ConfigHandle<UiConfig> configHandle;

    public UiModule(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        // Dieses Modul liest zehn Bloecke - aber erst zur Laufzeit, ueber deren oeffentliche
        // Naehte. Zum LADEN seiner Konfiguration braucht es keinen davon. Eine Abhaengigkeit, die
        // nur "wird spaeter mal gebraucht" bedeutet, verengt die Startreihenfolge ohne Gegenwert;
        // dieselbe Ueberlegung wie bei StatisticsModule, das nur "item" nennt.
        return List.of();
    }

    @Override
    public void start(ModuleContext context) {
        this.configHandle = loadConfig(context);
        UiConfig config = configHandle.get();
        logger.info(
                "[ui] phase=START state=LOADED - language="
                        + config.language()
                        + ", tick="
                        + config.tick().toMillis()
                        + "ms, surfaces="
                        + enabledSurfaces(config)
                        + ", damage numbers "
                        + (config.damageNumbers().enabled()
                                ? "on for " + config.damageNumbers().lifetime().toMillis() + "ms"
                                : "off"));
    }

    @Override
    public void stop() {
        // Nichts zu leeren. Dieser Block haelt keinen Bestand und keinen dauerhaften Zustand je
        // Spieler (FR-013b) - die Bossbars und Scoreboards haengen an den Spielerobjekten und gehen
        // mit ihnen. Ein leeres stop() ist hier die Zusage und nicht die Luecke.
    }

    /**
     * Die geltende Konfiguration.
     *
     * <p><b>Bei jedem Durchlauf neu abholen</b>, nicht einmal merken (FR-013c).
     */
    public UiConfig config() {
        if (configHandle == null) {
            throw new IllegalStateException("ui module not started");
        }
        return configHandle.get();
    }

    /** Wird nach jedem erfolgreichen Nachladen gerufen. */
    public void onReload(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Nimmt eine neu geladene {@code ui.yml} auf. Eine abgelehnte erreicht das hier nie.
     *
     * <p>Getauscht wird <b>das Ganze</b> — kein Feld einzeln. Was nicht mitgeht, ist die Sprache;
     * der Grund steht im Klassenkommentar.
     */
    public void applyReloadedConfig() {
        if (configHandle == null) {
            return;
        }
        UiConfig config = configHandle.get();
        logger.info(
                "[ui] phase=RELOAD state=APPLIED - tick="
                        + config.tick().toMillis()
                        + "ms, surfaces="
                        + enabledSurfaces(config)
                        + "; language stays "
                        + config.language()
                        + " until restart");
        for (Runnable listener : reloadListeners) {
            listener.run();
        }
    }

    private static String enabledSurfaces(UiConfig config) {
        List<String> on = new ArrayList<>();
        for (HudSurface surface : HudSurface.values()) {
            if (config.isEnabled(surface)) {
                on.add(surface.name().toLowerCase().replace('_', '-'));
            }
        }
        return on.isEmpty() ? "none" : String.join("+", on);
    }

    private ConfigHandle<UiConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), UiConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[ui] configuration rejected", invalid);
            throw new IllegalStateException(
                    "ui configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}

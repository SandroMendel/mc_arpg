package rpg.core.ui;

import java.time.Duration;
import java.util.Objects;

/**
 * Der geprüfte Inhalt von {@code ui.yml} (contracts/ui-config.md §1).
 *
 * <h2>Modell und Datei haben nicht dieselbe Gestalt — mit Absicht</h2>
 *
 * <p>Hier stehen {@link #tick} und {@link #zoneNoticeDuration} als {@link Duration}; in der Datei
 * stehen sie als {@code hud.tick-ms} und {@code hud.boss-bar.zone-notice-seconds}, also als Zahl
 * mit der Einheit im Namen. Das ist die Konvention jeder Konfiguration dieses Projekts
 * ({@code cooldown-ms}, {@code cleanup-after-seconds}, {@code respawn-minutes}), und
 * {@link UiConfigSchema} ist die <b>einzige</b> Stelle, die beide Formen kennt. Eine zweite hieße,
 * zwei Vorstellungen von derselben Zahl zu pflegen.
 *
 * <h2>Die Konfiguration wird je Durchlauf gelesen, nicht beim Start festgehalten</h2>
 *
 * <p>FR-013c: Nachladen muss den laufenden Sammeltakt <em>erreichen</em>. Ein Takt, der sich diesen
 * Record einmal in ein Feld legt, meldet nach {@code /reload} Erfolg und arbeitet weiter mit den
 * alten Werten — der Betreiber sieht dann eine Änderung, die nicht stattfindet. Der Record ist
 * deshalb unveränderlich und wird <b>im Ganzen getauscht</b>; wer ihn benutzt, holt ihn bei jedem
 * Durchlauf frisch von {@code UiModule.config()}.
 *
 * @param language das Kürzel des Sprachsatzes, der gilt; die Datei muss vollständig sein (FR-018)
 * @param tick der eine Sammeltakt — kürzer heißt mehr Pakete ohne Gewinn, länger heißt, die
 *     Actionbar blinkt, weil Minecraft sie nach etwa zwei Sekunden ausblendet
 * @param actionBar ob die Actionbar bespielt wird
 * @param bossBar ob die Bossbar bespielt wird
 * @param sidebar ob das Scoreboard bespielt wird
 * @param zoneNoticeDuration wie lange ein Zonenname steht, wenn ihn nichts Wichtigeres verdrängt
 * @param damageNumbers die Einstellungen der Schadenszahlen
 */
public record UiConfig(
        String language,
        Duration tick,
        SurfaceSetting actionBar,
        SurfaceSetting bossBar,
        SurfaceSetting sidebar,
        Duration zoneNoticeDuration,
        DamageNumberSetting damageNumbers) {

    public UiConfig {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(tick, "tick");
        Objects.requireNonNull(actionBar, "actionBar");
        Objects.requireNonNull(bossBar, "bossBar");
        Objects.requireNonNull(sidebar, "sidebar");
        Objects.requireNonNull(zoneNoticeDuration, "zoneNoticeDuration");
        Objects.requireNonNull(damageNumbers, "damageNumbers");
        if (language.isBlank()) {
            throw new IllegalArgumentException("language ist leer");
        }
        if (tick.isZero() || tick.isNegative()) {
            throw new IllegalArgumentException(
                    "tick ist " + tick + " - erlaubt ist groesser als null");
        }
        if (zoneNoticeDuration.isZero() || zoneNoticeDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "zoneNoticeDuration ist "
                            + zoneNoticeDuration
                            + " - erlaubt ist groesser als null");
        }
    }

    /**
     * Ob diese Fläche bespielt wird.
     *
     * <p>Damit ein Aufrufer nicht drei Felder auseinanderhalten muss — und damit die Prüfung
     * <em>vor</em> der Berechnung genau eine Zeile ist (FR-013a).
     */
    public boolean isEnabled(HudSurface surface) {
        return switch (surface) {
            case ACTION_BAR -> actionBar.enabled();
            case BOSS_BAR -> bossBar.enabled();
            case SIDEBAR -> sidebar.enabled();
        };
    }
}

package rpg.core.ui;

import java.time.Duration;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;

/**
 * Schema für {@code ui.yml} (contracts/ui-config.md).
 *
 * <p><b>Jede Meldung nennt Datei, Schlüssel und Grund</b> (Prinzip V, Fail-Fast) — nach dem Muster
 * von {@code MobConfigSchema}, {@code ItemConfigSchema} und {@code StatisticsConfigSchema}. Eine
 * Meldung, die nur sagt, dass etwas falsch ist, verlangt vom Leser, die Datei selbst zu
 * durchsuchen, und zwar an dem Tag, an dem der Server nicht startet.
 *
 * <h2>Die einzige Stelle, die beide Formen einer Dauer kennt</h2>
 *
 * <p>In der Datei stehen Dauern als Zahl mit Einheit im Schlüssel ({@code tick-ms},
 * {@code zone-notice-seconds}, {@code lifetime-ms}), im Modell als {@link Duration}. Die
 * Umrechnung passiert hier und nirgends sonst — sonst pflegte man zwei Vorstellungen von derselben
 * Zahl.
 *
 * <h2>Was hier absichtlich NICHT geprüft wird</h2>
 *
 * <p><b>Ob es die Sprachdatei zu {@code language} wirklich gibt.</b> Dieses Paket kennt kein
 * Dateisystem und keinen Datenordner; ein Verweis darauf wäre genau die Abhängigkeit, die die
 * Schichtgrenze verhindert. Die Prüfung läuft beim Start in {@code UiModule} beziehungsweise in
 * {@code RpgPlugin.loadMessages} und <b>bricht ihn genauso ab</b> (FR-018) — dieselbe Aufteilung,
 * die {@code ItemConfigSchema} und {@code StatisticsConfigSchema} treffen.
 *
 * <p><b>Ob zwei Fähigkeiten einer Klasse dasselbe Material tragen</b> (FR-032). Das ist eine Regel
 * über B08s Verzeichnis, keine Zahlenwahl in dieser Datei — sie steht in {@code MaterialUniqueness}
 * und läuft ebenfalls beim Start.
 */
public final class UiConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private static final String FILE = "ui.yml";

    private UiConfigSchema() {}

    public static ConfigSchema<UiConfig> schema() {
        ConfigSchema.Builder<UiConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        builder.required("language", FieldType.STRING);
        builder.required("hud", FieldType.MAP);
        builder.required("damage-numbers", FieldType.MAP);
        return builder.boundTo(UiConfigSchema::bind).build();
    }

    private static UiConfig bind(ConfigView view) {
        String language = view.getString("language");
        if (language.isBlank()) {
            throw new IllegalArgumentException(
                    FILE
                            + ": language ist leer - erlaubt ist ein Sprachkuerzel wie 'en'. Zu ihm"
                            + " muss eine vollstaendige Sprachdatei existieren, sonst startet der"
                            + " Server nicht (FR-018)");
        }

        Map<?, ?> hud = view.getMap("hud");
        Map<?, ?> bossBar = requireMap(hud, "boss-bar", "hud");

        return new UiConfig(
                language,
                requireMillis(hud, "tick-ms", "hud"),
                new SurfaceSetting(requireBoolean(requireMap(hud, "action-bar", "hud"), "enabled", "hud.action-bar")),
                new SurfaceSetting(requireBoolean(bossBar, "enabled", "hud.boss-bar")),
                new SurfaceSetting(requireBoolean(requireMap(hud, "sidebar", "hud"), "enabled", "hud.sidebar")),
                requireSeconds(bossBar, "zone-notice-seconds", "hud.boss-bar"),
                readDamageNumbers(view.getMap("damage-numbers")));
    }

    private static DamageNumberSetting readDamageNumbers(Map<?, ?> body) {
        double offset = requireDouble(body, "offset", "damage-numbers");
        if (!Double.isFinite(offset)) {
            throw new IllegalArgumentException(
                    FILE
                            + ": damage-numbers.offset ist "
                            + offset
                            + " - erlaubt ist eine endliche Kommazahl (Hoehe ueber dem"
                            + " Trefferpunkt, in Bloecken)");
        }
        return new DamageNumberSetting(
                requireBoolean(body, "enabled", "damage-numbers"),
                requireMillis(body, "lifetime-ms", "damage-numbers"),
                offset);
    }

    // --- Leser mit Meldungen, die Datei, Schluessel und Grund nennen ---------

    private static Map<?, ?> requireMap(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + (value == null ? " fehlt" : " ist keine Zuordnung")
                            + " - erwartet wird ein Abschnitt");
        }
        return map;
    }

    private static boolean requireBoolean(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + (value == null ? " fehlt" : " ist '" + value + "'")
                            + " - erlaubt ist true oder false");
        }
        return flag;
    }

    private static Number requireNumber(Map<?, ?> body, String field, String where) {
        Object value = body.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + (value == null ? " fehlt" : " ist '" + value + "'")
                            + " - erwartet wird eine Zahl");
        }
        return number;
    }

    private static double requireDouble(Map<?, ?> body, String field, String where) {
        return requireNumber(body, field, where).doubleValue();
    }

    private static Duration requireMillis(Map<?, ?> body, String field, String where) {
        long millis = requireNumber(body, field, where).longValue();
        if (millis <= 0) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + " ist "
                            + millis
                            + " - erlaubt ist groesser als 0");
        }
        return Duration.ofMillis(millis);
    }

    private static Duration requireSeconds(Map<?, ?> body, String field, String where) {
        long seconds = requireNumber(body, field, where).longValue();
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    FILE
                            + ": "
                            + where
                            + "."
                            + field
                            + " ist "
                            + seconds
                            + " - erlaubt ist groesser als 0");
        }
        return Duration.ofSeconds(seconds);
    }
}

package rpg.plugin.command.framework;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Was in {@code commands.yml} unter {@code rate-limit} steht (T042, FR-032, Prinzip V).
 *
 * <h2>Gelesen, nicht geschätzt</h2>
 *
 * <p>Die Sperrzeiten sind Konfiguration und keine Konstanten: welche Abfrage teuer ist, hängt an
 * der Datenbank des Betreibers und nicht an einer Zahl im Quelltext.
 *
 * <h2>Ein Feld, kein Schema — vorerst</h2>
 *
 * <p>Dieselbe Entscheidung wie bei {@code RpgPlugin.configuredLanguage}: ein eigenes
 * {@code ConfigSchema} wäre eine zweite Vorstellung davon, was {@code commands.yml} ist, für eine
 * Zahl je Kommando. <b>Wächst die Datei um etwas, das Prüfregeln braucht, gehört ein Schema dazu</b>
 * — und dann eines, nicht zwei.
 *
 * <p>Ein fehlender Wert ist <b>kein Fehler</b>: ein Kommando ohne Eintrag hat die Voreinstellung,
 * und eine fehlende Datei bedeutet die Voreinstellung für alle. Eine Sperrzeit ist eine Bremse, und
 * eine fehlende Bremse darf keinen Start verhindern.
 */
public final class RateLimitConfig {

    /** Was gilt, wenn {@code commands.yml} nichts sagt. */
    public static final Duration FALLBACK = Duration.ofSeconds(3);

    private final Duration fallback;
    private final Map<String, Duration> perCommand;

    private RateLimitConfig(Duration fallback, Map<String, Duration> perCommand) {
        this.fallback = fallback;
        this.perCommand = Map.copyOf(perCommand);
    }

    /** Die Voreinstellung, ohne jede Datei. */
    public static RateLimitConfig defaults() {
        return new RateLimitConfig(FALLBACK, Map.of());
    }

    /**
     * Liest den Abschnitt {@code rate-limit} aus der schon geladenen Form.
     *
     * @param document der Inhalt von {@code commands.yml}, oder {@code null}
     */
    @SuppressWarnings("unchecked")
    public static RateLimitConfig from(Map<String, Object> document) {
        if (document == null) {
            return defaults();
        }
        Object section = document.get("rate-limit");
        if (!(section instanceof Map<?, ?> limits)) {
            return defaults();
        }

        Duration fallback = seconds(((Map<String, Object>) limits).get("default-seconds"), FALLBACK);

        Map<String, Duration> perCommand = new LinkedHashMap<>();
        Object each = ((Map<String, Object>) limits).get("per-command");
        if (each instanceof Map<?, ?> entries) {
            ((Map<String, Object>) entries)
                    .forEach((name, value) -> perCommand.put(name, seconds(value, fallback)));
        }
        return new RateLimitConfig(fallback, perCommand);
    }

    /**
     * Die Sperrzeit für ein Kommando.
     *
     * <p>{@code Duration.ZERO} heißt <b>keine</b> Sperre — {@code RpgCommand.rateLimit} behandelt
     * Null und Negatives gleich, damit eine 0 in der Konfiguration abschaltet statt zu sperren.
     *
     * @param command der Name ohne Schrägstrich; bei Unterkommandos der Pfad mit Punkten
     */
    public Duration forCommand(String command) {
        Objects.requireNonNull(command, "command");
        return perCommand.getOrDefault(command, fallback);
    }

    private static Duration seconds(Object raw, Duration fallback) {
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value <= 0 ? Duration.ZERO : Duration.ofSeconds(value);
        }
        return fallback;
    }
}

package rpg.core.statistics;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bildet und zerlegt die Schlüssel dimensionierter Metriken — {@code mob_kills.<kindKey>},
 * {@code playtime_active.<zoneKey>}.
 *
 * <p><b>Hier stehen keine Metriknamen.</b> Diese Klasse kennt nur die Mechanik: Familie, Trenner,
 * Dimension. Welche Metriken es gibt, weiß allein {@link MetricRegistry} — und diese Trennung ist
 * der Grund, aus dem {@code NoMetricLiteralsTest} überhaupt eine Chance hat: gäbe es die Bildung
 * an jeder Aufrufstelle, stünde dort zwangsläufig auch der Name.
 *
 * <p>Die Dimension stammt aus fremden Konfigurationen — Artenschlüssel aus {@code mobs.yml},
 * Zonenschlüssel aus {@code zones.yml}. Beide dürfen den Trenner nicht enthalten, sonst wäre die
 * Zerlegung nicht mehr eindeutig; {@link #compose} weist so eine Dimension deshalb zurück, statt
 * einen Schlüssel zu bilden, der sich später anders liest, als er gemeint war.
 */
public final class MetricKeys {

    /** Der Trenner zwischen Familie und Dimension. */
    static final char SEPARATOR = '.';

    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_-]+");

    private MetricKeys() {}

    /**
     * Der vollständige Schlüssel einer dimensionierten Metrik.
     *
     * @throws IllegalArgumentException wenn die Metrik keine Dimension trägt oder die Dimension
     *     leer ist, Großbuchstaben oder den Trenner enthält
     */
    public static String compose(Metric family, String dimension) {
        if (!family.dimensioned()) {
            throw new IllegalArgumentException(
                    "Metrik '" + family.key() + "' traegt keine Dimension");
        }
        if (dimension == null || !DIMENSION.matcher(dimension).matches()) {
            throw new IllegalArgumentException(
                    "Dimension '"
                            + dimension
                            + "' fuer '"
                            + family.key()
                            + "' ist unbrauchbar - erlaubt sind "
                            + DIMENSION.pattern());
        }
        return family.key() + SEPARATOR + dimension;
    }

    /** Die Familie eines gespeicherten Schlüssels; bei undimensionierten der Schlüssel selbst. */
    public static String familyOf(String storedKey) {
        int at = storedKey.indexOf(SEPARATOR);
        return at < 0 ? storedKey : storedKey.substring(0, at);
    }

    /** Die Dimension eines gespeicherten Schlüssels, sofern er eine trägt. */
    public static Optional<String> dimensionOf(String storedKey) {
        int at = storedKey.indexOf(SEPARATOR);
        return at < 0 ? Optional.empty() : Optional.of(storedKey.substring(at + 1));
    }

    /** Ob dieser gespeicherte Schlüssel zu dieser Familie gehört. */
    public static boolean belongsTo(String storedKey, Metric family) {
        return familyOf(storedKey).equals(family.key());
    }
}

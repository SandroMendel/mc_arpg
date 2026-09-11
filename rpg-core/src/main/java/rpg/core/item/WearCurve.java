package rpg.core.item;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Wie sich Verschleiss auf den Ausruestungsbeitrag auswirkt, und wodurch er entsteht (FR-047).
 *
 * <p><b>Ausruestung zerbricht nie.</b> B07 haelt sie unzerstoerbar
 * ({@code BoundItemFactory.makeIndestructible}), und diese Spec hebt das nicht auf - sie loest den
 * Einwand, der dort steht: <em>„a damaged item would quietly weaken a character in a way no
 * attribute reflects."</em> Hier ist die Schwaechung keine stille: sie geht durch die
 * Werteberechnung und ist ablesbar (FR-038, ADR-039).
 *
 * <p><b>Die Kurve.</b> Oberhalb der Schwelle voller Beitrag, darunter stetig fallend bis auf den
 * Restanteil bei Zustand null:
 *
 * <pre>
 *   zustand &gt;= schwelle  ->  1.0
 *   zustand &lt;  schwelle  ->  rest + (1 - rest) * (zustand / schwelle)
 * </pre>
 *
 * Mit den Vorgabewerten (Schwelle 50, Rest 0,20): Zustand 50 -&gt; 1,00 · 25 -&gt; 0,60 ·
 * 10 -&gt; 0,36 · 0 -&gt; 0,20. Das erfuellt die Ansage „bei 0/100 volle 80 % Verlust, bei 10/100
 * schon spuerbar".
 *
 * <p><b>Der Tod wiegt schwerer als der Alltag - und das ist eine Regel, keine Zahlenwahl.</b>
 * {@link #validate(String)} weist eine Konfiguration zurueck, in der {@code perDeath} nicht
 * wenigstens um {@code deathFactorMin} ueber den Schadensraten liegt (FR-044). Ohne diese Pruefung
 * koennte ein spaeteres Balancing die Todesstrafe aus ADR-017 stillschweigend aushebeln, und
 * niemand haette es gemerkt.
 *
 * <p><b>Gemessen wird vor der Abwehr, nicht danach</b> (FR-040a). Das steht nicht hier, sondern in
 * {@code WearRules} - aber es ist der Grund, aus dem {@code perDamageTaken} eine einzige Rate ist
 * und keine je Ruestungsguete: am durchgekommenen Schaden gemessen entstuende eine
 * Abwaertsspirale, in der verschlissene Ruestung sich selbst schneller aufbraucht.
 *
 * @param threshold Zustand, ab dem der Beitrag voll ist
 * @param floor Restanteil bei Zustand 0, in {@code [0, 1)}
 * @param perDamageTaken Zustandspunkte je erlittenem Schadenspunkt (Ruestung)
 * @param perDamageDealt Zustandspunkte je ausgeteiltem Schadenspunkt aus einem Autoattack (Waffe)
 * @param perDeath Zustandspunkte je Tod, auf beide Slots
 * @param deathFactorMin wie viel schwerer ein Tod mindestens wiegen muss als ein Schadenspunkt
 * @param warnAt Schwellen, bei deren Unterschreiten gewarnt wird
 * @param warnCooldown Ruhezeit je Schwelle, damit nicht bei jedem Treffer gewarnt wird
 */
public record WearCurve(
        double threshold,
        double floor,
        double perDamageTaken,
        double perDamageDealt,
        double perDeath,
        double deathFactorMin,
        List<Double> warnAt,
        Duration warnCooldown) {

    /** Der volle Zustand, mit dem jeder Charakter beginnt. */
    public static final double FULL = 100.0;

    public WearCurve {
        warnAt = List.copyOf(Objects.requireNonNull(warnAt, "warnAt"));
        Objects.requireNonNull(warnCooldown, "warnCooldown");
    }

    /**
     * Prueft die Konfiguration und wirft mit einer Meldung, die den Schluessel nennt (FR-012).
     *
     * @param where Datei und Abschnitt, fuer die Meldung
     */
    public void validate(String where) {
        if (!(threshold > 0.0) || threshold > FULL) {
            throw new IllegalArgumentException(
                    where + ".threshold must be in (0, 100], but was " + threshold);
        }
        if (floor < 0.0 || floor >= 1.0) {
            throw new IllegalArgumentException(
                    where + ".floor must be in [0, 1), but was " + floor);
        }
        requirePositive(perDamageTaken, where + ".per-damage-taken");
        requirePositive(perDamageDealt, where + ".per-damage-dealt");
        requirePositive(perDeath, where + ".per-death");
        if (!(deathFactorMin >= 1.0)) {
            throw new IllegalArgumentException(
                    where + ".death-factor-min must be at least 1, but was " + deathFactorMin);
        }

        // FR-044: die Ordnung ist die Anforderung. Ein Tod, der weniger kostet als ein paar
        // Treffer, waere keine Todesstrafe mehr - und der Verstoss waere von aussen unsichtbar,
        // weil alles weiter funktioniert.
        double required = Math.max(perDamageTaken, perDamageDealt) * deathFactorMin;
        if (perDeath < required) {
            throw new IllegalArgumentException(
                    where
                            + ".per-death is "
                            + perDeath
                            + ", but must be at least "
                            + required
                            + " (death-factor-min "
                            + deathFactorMin
                            + " times the larger damage rate). A death has to weigh more than an"
                            + " ordinary fight - that is the death penalty of ADR-017, and it must"
                            + " not be turned off by a balancing change nobody notices (FR-044)");
        }

        for (double warn : warnAt) {
            if (warn < 0.0 || warn > FULL) {
                throw new IllegalArgumentException(
                        where + ".warn-at must be in [0, 100], but held " + warn);
            }
        }
        if (warnCooldown.isNegative()) {
            throw new IllegalArgumentException(
                    where + ".warn-cooldown-ms must not be negative, but was " + warnCooldown);
        }
    }

    private static void requirePositive(double value, String what) {
        if (!(value > 0.0)) {
            throw new IllegalArgumentException(what + " must be positive, but was " + value);
        }
    }

    /**
     * Der Faktor auf den Stufenbeitrag bei diesem Zustand (FR-047, FR-048).
     *
     * <p>Stetig: zwei verschiedene Zustaende zwischen Schwelle und null ergeben nie denselben
     * Faktor. Ein Sprung waere ein Punkt, an dem ein einzelner Treffer spuerbar mehr kostet als der
     * davor, und niemand koennte erklaeren warum.
     */
    public double factorFor(double condition) {
        double clamped = Math.min(Math.max(condition, 0.0), FULL);
        if (clamped >= threshold) {
            return 1.0;
        }
        return floor + (1.0 - floor) * (clamped / threshold);
    }

    /** Der Zustand nach erlittenem Schaden - gemessen <b>vor</b> der Abwehr (FR-040a). */
    public double afterDamageTaken(double condition, double incomingDamage) {
        return reduce(condition, perDamageTaken * Math.max(incomingDamage, 0.0));
    }

    /** Der Zustand nach ausgeteiltem Autoattack-Schaden (FR-041). */
    public double afterDamageDealt(double condition, double dealtDamage) {
        return reduce(condition, perDamageDealt * Math.max(dealtDamage, 0.0));
    }

    /** Der Zustand nach einem Tod (FR-042). */
    public double afterDeath(double condition) {
        return reduce(condition, perDeath);
    }

    private static double reduce(double condition, double amount) {
        return Math.max(0.0, Math.min(condition, FULL) - amount);
    }

    /**
     * Welche Warnschwelle dieser Uebergang unterschritten hat, oder leer.
     *
     * <p>Genau eine je Uebergang: die hoechste ueberschrittene. Zwei Meldungen fuer einen Treffer
     * waeren zwei Meldungen zu viel.
     */
    public java.util.OptionalDouble crossedWarning(double before, double after) {
        double best = Double.NaN;
        for (double warn : warnAt) {
            if (before > warn && after <= warn && (Double.isNaN(best) || warn > best)) {
                best = warn;
            }
        }
        return Double.isNaN(best) ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(best);
    }

    /** Die Vorgabewerte aus {@code items.yml}, fuer Tests und als Ausgangspunkt. */
    public static WearCurve defaults() {
        return new WearCurve(
                50.0,
                0.20,
                0.01,
                0.01,
                10.0,
                100.0,
                new ArrayList<>(List.of(50.0, 25.0, 10.0)),
                Duration.ofSeconds(60));
    }
}

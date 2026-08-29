package rpg.core.statistics;

/**
 * Ein Eintrag des Metrikverzeichnisses ([data-model.md] §1.2).
 *
 * <p>Vier Eigenschaften, und alle vier sind Eigenschaften der <em>Metrik</em>, nicht des Ereignisses,
 * das sie auslöst: der Schlüssel, die {@link MetricKind Art}, die {@link MetricVisibility
 * Sichtbarkeit} und die Frage, ob der Schlüssel eine Dimension trägt.
 *
 * <p><b>Dimensioniert</b> heißt: der gespeicherte Schlüssel besteht aus dieser Familie und einem
 * Zusatz — {@code mob_kills.dune-warlord}, {@code playtime_active.greenfields}. Der Wert in
 * {@link #key()} ist dann die <em>Familie</em> allein; den vollständigen Schlüssel bildet
 * {@link MetricKeys}. Eine Gesamtzahl über die Familie ist eine Aggregation über deren Zeilen und
 * <b>kein eigener Schlüssel</b> (FR-013): sie zusätzlich zu schreiben hieße, dieselbe Zahl zweimal
 * zu führen, und zwei Wahrheiten driften.
 *
 * @param key Familien- oder vollständiger Schlüssel, je nach {@link #dimensioned()}
 * @param kind ob addiert, maximiert oder nur gelesen wird
 * @param visibility ob die Metrik öffentlich rankbar ist
 * @param dimensioned ob der gespeicherte Schlüssel einen Zusatz trägt
 */
public record Metric(String key, MetricKind kind, MetricVisibility visibility, boolean dimensioned) {

    public Metric {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Metrik ohne Schluessel");
        }
        if (kind == null || visibility == null) {
            throw new IllegalArgumentException("Metrik " + key + " ohne Art oder Sichtbarkeit");
        }
        if (key.indexOf(MetricKeys.SEPARATOR) >= 0) {
            throw new IllegalArgumentException(
                    "Metrikschluessel '"
                            + key
                            + "' enthaelt den Trenner '"
                            + MetricKeys.SEPARATOR
                            + "' - eine Dimension gehoert nicht in den Verzeichniseintrag, "
                            + "sondern wird mit MetricKeys.compose(...) angehaengt");
        }
    }

    /**
     * Weist eine Metrik zurück, deren Art nicht die erwartete ist.
     *
     * <p><b>Abweisen, nicht umdeuten.</b> {@code count} auf einer {@code MAX}-Metrik ist kein
     * Grenzfall, den man freundlich auslegen könnte — es ist ein Programmierfehler, und das
     * freundliche Auslegen wäre genau die stille Fehldeutung, gegen die {@link MetricKind} sein
     * Javadoc schreibt (contracts/stats-api.md §1).
     *
     * @throws IllegalArgumentException wenn die Art nicht passt
     */
    public Metric requireKind(MetricKind expected) {
        if (kind != expected) {
            throw new IllegalArgumentException(
                    "Metrik '" + key + "' ist " + kind + ", erwartet wurde " + expected);
        }
        return this;
    }
}

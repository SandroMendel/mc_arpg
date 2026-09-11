package rpg.core.statistics;

import java.util.UUID;

/**
 * Was B12 nach außen zum <b>Zählen</b> anbietet (contracts/stats-api.md §1).
 *
 * <h2>Zwei Zusicherungen, die im Tick gelten müssen</h2>
 *
 * <ul>
 *   <li><b>Im Tick sicher.</b> Beide Methoden kosten dort einen Zugriff auf eine Map — kein
 *       Datenbankzugriff, keine Berechnung. Was daraus wird, entscheidet der Fluss (Prinzip II).
 *   <li><b>Fehlertolerant.</b> Eine Ausnahme hier darf das auslösende Spielereignis nicht
 *       scheitern lassen (FR-004). <b>Ein nicht gezählter Kill ist hinnehmbar, ein verlorener
 *       Kill nicht</b> — die Statistik ist der Beobachter des Spiels, nicht sein Schiedsrichter.
 * </ul>
 *
 * <h2>Warum hier eine {@link Metric} steht und keine Zeichenkette</h2>
 *
 * <p>Der Verzeichniseintrag trägt die {@link MetricKind Art} und die {@link MetricVisibility
 * Sichtbarkeit}. Ein Schlüssel als Literal an der Aufrufstelle umginge beide, sähe dabei harmlos
 * aus und schriebe eine plausible Zahl an die falsche Stelle — {@code NoMetricLiteralsTest}
 * verhindert das mechanisch (FR-018).
 *
 * <p>Aus demselben Grund werden {@code count} auf einer Maximum-Metrik und {@code reportMax} auf
 * einer Summen-Metrik <b>abgewiesen</b> und nicht stillschweigend umgedeutet.
 */
public interface Statistics {

    /**
     * Erhöht einen Zähler.
     *
     * @param playerId das <b>Konto</b>, nicht der Charakter (FR-005)
     * @param metric ein undimensionierter Eintrag der Art {@link MetricKind#SUM}
     */
    void count(UUID playerId, Metric metric, long delta);

    /**
     * Erhöht einen Zähler einer dimensionierten Familie — {@code mob_kills.<kindKey>}.
     *
     * <p><b>Diese Überladung steht nicht im Vertrag</b>, und das war eine Lücke darin: drei der
     * fünf gespeicherten Metriken sind dimensioniert, und ohne sie müsste jeder Aufrufer den
     * vollständigen Schlüssel selbst bilden — also genau das Literal erzeugen, das FR-018
     * verbietet. Die Bildung liegt deshalb hier, in {@link MetricKeys#compose}.
     *
     * @param family ein dimensionierter Eintrag der Art {@link MetricKind#SUM}
     * @param dimension Artenschlüssel, Zonenschlüssel oder einer der festen Ersatzschlüssel
     */
    void count(UUID playerId, Metric family, String dimension, long delta);

    /**
     * Meldet einen Wert für eine Maximum-Metrik. Kein Lesen vor dem Schreiben (ADR-040).
     *
     * @param metric ein Eintrag der Art {@link MetricKind#MAX}
     */
    void reportMax(UUID playerId, Metric metric, long value);
}

package rpg.core.statistics;

/**
 * Wie der gespeicherte Wert einer Metrik entsteht.
 *
 * <h2>Warum die Art am Verzeichnis hängt und nicht an der Aufrufstelle</h2>
 *
 * <p>Der Unterschied zwischen {@link #SUM} und {@link #MAX} ist im Ergebnis <em>ein Operator</em>:
 * die eine Metrik schreibt {@code value = value + excluded.value}, die andere
 * {@code value = GREATEST(value, excluded.value)} (ADR-040). Beide schreiben auf dieselbe Zeile
 * derselben Tabelle, beide ohne vorher zu lesen — von außen sehen sie identisch aus.
 *
 * <p>Genau deshalb darf die Wahl nicht dort fallen, wo gezählt wird. Ein Zuhörer im Kampfpfad weiß,
 * <em>dass</em> etwas passiert ist; ob daraus eine Summe oder ein Höchstwert wird, ist eine
 * Eigenschaft der Metrik und ändert sich nie mit dem Ereignis. Läge die Entscheidung an der
 * Aufrufstelle, gäbe es sie so oft, wie es Aufrufstellen gibt — und die zweite, die sie anders
 * trifft, fällt niemandem auf: der höchste Schaden würde sich still aufsummieren und wäre ab dann
 * eine Schadenssumme mit falschem Namen. Kein Test schlägt an, weil beide Zahlen plausibel sind.
 *
 * <p>Also entscheidet der Verzeichniseintrag, und {@code count} auf einer {@code MAX}-Metrik wird
 * <b>abgewiesen</b> statt umgedeutet ({@link Metric#requireKind}, FR-018).
 */
public enum MetricKind {

    /** Tageswerte addieren sich. Kills, Tode, Spielzeit. */
    SUM,

    /** Der Tageswert ist der größte gemeldete. Höchster Einzeltreffer (ADR-040). */
    MAX,

    /**
     * Ein Zustand, der anderswo lebt und hier nur gelesen wird — Level, XP, Coins.
     *
     * <p><b>Wird nie in die Tagestabelle geschrieben</b> (FR-019, ADR-041). Die Summe von
     * Tageszuständen ist bedeutungslos: wer sieben Tage lang Level 12 ist, hat nicht Level 84.
     * Zustandswerte haben deshalb auch keine Tages-, Wochen- oder Saisonform (FR-023) — sie
     * erscheinen ausschließlich als aktueller Stand.
     */
    STATE
}

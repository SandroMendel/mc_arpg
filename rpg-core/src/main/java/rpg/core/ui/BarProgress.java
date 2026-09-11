package rpg.core.ui;

import java.time.Instant;
import java.util.Objects;

/**
 * Ein Balkenfüllstand als <b>Rechnung gegen die Uhr</b> — keine Aufgabe.
 *
 * <p>Constitution II.2 verbietet eine wiederkehrende Aufgabe je Spieler oder je Entität;
 * zeitbasierte Werte werden <b>zeitstempelbasiert lazy</b> ausgewertet. Eine Kanalisierung ist genau
 * so ein Wert: {@code RunningAbility} führt {@code startedAt} und {@code dueAt}, und daraus folgt
 * der Füllstand zu jedem Zeitpunkt, ohne dass irgendwo etwas tickt.
 *
 * <p><b>Der Javadoc sagt das, damit niemand später einen Ticker danebenstellt.</b> Ein Balken, der
 * sich „von selbst füllt", ist die naheliegende Erwartung — und bei 200 Spielern die teuerste.
 *
 * @param startedAt wann es losging
 * @param dueAt wann es fertig ist
 */
public record BarProgress(Instant startedAt, Instant dueAt) {

    public BarProgress {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(dueAt, "dueAt");
        if (dueAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "dueAt " + dueAt + " liegt vor startedAt " + startedAt);
        }
    }

    /**
     * Der Füllstand zum Zeitpunkt {@code now}, immer in {@code [0,1]}.
     *
     * <p><b>Begrenzt und nicht nur gerechnet.</b> Ein Balken über eins ist ein Paketfehler, einer
     * unter null eine Ausnahme in Papers Bossbar — beides Fälle, die nur bei einer Uhr auftreten,
     * die zurückspringt oder bei einem Aufruf, der zu spät kommt. Beides passiert.
     *
     * <p><b>{@code startedAt == dueAt} ergibt 1 und keine Division durch null.</b> Eine Fähigkeit
     * ohne Kanalisierungszeit ist sofort fertig; das ist der richtige Wert und nicht der
     * Sonderfall, den ein Aufrufer abfangen müsste.
     */
    public double fraction(Instant now) {
        Objects.requireNonNull(now, "now");
        long total = dueAt.toEpochMilli() - startedAt.toEpochMilli();
        if (total <= 0) {
            return 1.0;
        }
        long elapsed = now.toEpochMilli() - startedAt.toEpochMilli();
        if (elapsed <= 0) {
            return 0.0;
        }
        if (elapsed >= total) {
            return 1.0;
        }
        return (double) elapsed / (double) total;
    }

    /**
     * Der verbleibende Anteil — was ein Cooldown-Balken zeigt.
     *
     * <p>Ein eigener Aufruf statt {@code 1 - fraction(now)} an jeder Aufrufstelle: die Umkehrung ist
     * die Sorte Rechnung, die einmal vergessen wird und dann rückwärts läuft.
     */
    public double remaining(Instant now) {
        return 1.0 - fraction(now);
    }

    /** Ob {@code now} den Endzeitpunkt erreicht oder überschritten hat. */
    public boolean isDone(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(dueAt);
    }
}

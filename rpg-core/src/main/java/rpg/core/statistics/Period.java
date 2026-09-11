package rpg.core.statistics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;

/**
 * Die vier Zeiträume, über die ein Wert gelesen werden kann (FR-024).
 *
 * <h2>Alle vier entstehen aus derselben Tagesangabe</h2>
 *
 * <p>Keiner von ihnen bekommt eigenen Speicher (FR-025). Eine Woche ist kein zusätzlicher
 * Schreibvorgang, sondern eine Summe über sieben Tageszeilen; eine Saison eine Summe über ihren
 * Datumsbereich. Was gespeichert wird, ist ausschließlich der Tag — alles andere ist eine Frage an
 * ihn. Ein eigener Wochenzähler wäre eine zweite Wahrheit über dieselben Ereignisse und würde beim
 * ersten verpassten Schreibvorgang von der Tagessumme abweichen, ohne dass jemand es merkt.
 *
 * <h2>Alles in UTC, und zwar nicht aus Bequemlichkeit</h2>
 *
 * <p>Die gespeicherte Tagesangabe <em>ist</em> bereits UTC (data-model.md §1.1). Würden die
 * Grenzen hier einer anderen Zeitzone folgen, gehörte ein Teil der Zeilen eines Tages zum Vortag
 * und ein Teil zum Folgetag — und die Woche stimmte nie mit der Summe ihrer sieben Tage überein.
 * Der Fehler wäre klein, verschöbe sich mit der Sommerzeit und wäre praktisch nicht zu finden
 * (FR-026).
 *
 * <p>Die Woche ist die <b>ISO-Woche</b> und beginnt am <b>Montag</b>. Nicht am Sonntag: die
 * gespeicherte Angabe folgt ISO-8601, und zwei Wochenbegriffe nebeneinander wären genau der
 * Unterschied, den niemand bemerkt, bis eine Rangliste am Montagmorgen zurückspringt.
 */
public enum Period {

    /** Ein Kalendertag in UTC. */
    DAY,

    /** Die ISO-Woche, Montag bis Sonntag, in UTC (FR-026). */
    WEEK,

    /** Eine konfigurierte Saison; ihre Grenzen sind tagesgenau (FR-027). */
    SEASON,

    /**
     * Alles seit Beginn.
     *
     * <p><b>Der einzige Zeitraum, den ein Zustandswert kennt</b> (FR-023): Level, XP und Coins
     * haben keine Tages-, Wochen- oder Saisonform, weil ein Zustand keine Dauer hat, über die man
     * ihn summieren könnte.
     */
    ALL_TIME;

    /** Der erste Tag der ISO-Woche, in der dieser Tag liegt. */
    public static LocalDate startOfWeek(LocalDate day) {
        return day.with(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue());
    }

    /** Der letzte Tag der ISO-Woche, in der dieser Tag liegt. */
    public static LocalDate endOfWeek(LocalDate day) {
        return startOfWeek(day).plusDays(6);
    }

    /** Ob dieser Zeitraum für eine Metrik dieser Art überhaupt zulässig ist (FR-023). */
    public boolean fits(MetricKind kind) {
        return kind != MetricKind.STATE || this == ALL_TIME;
    }
}

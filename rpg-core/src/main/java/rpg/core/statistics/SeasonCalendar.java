package rpg.core.statistics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Die konfigurierten Saisons — <b>lückenlos und überschneidungsfrei</b> (FR-048, FR-049).
 *
 * <h2>Warum der Start daran scheitern darf</h2>
 *
 * <p>FR-027 sagt zu: ein Tag gehört zu <b>genau einer</b> Saison. Diese Zusage ist nicht in der
 * Abfrage durchsetzbar, sondern nur im Kalender selbst. Überschneiden sich zwei Saisons, zählt ein
 * Kill für beide — die Summe aller Saisons wäre größer als die Allzeitsumme, und niemand könnte
 * sagen, welche der beiden Ranglisten die richtige ist. Klafft eine Lücke, verschwinden die
 * Ereignisse dieser Tage aus jeder Saisonwertung, tauchen aber in der Allzeitwertung weiter auf.
 *
 * <p>Beides ergäbe <em>plausible</em> Zahlen. Genau deshalb bricht der Start ab, statt zu warnen:
 * eine Warnung im Protokoll wird gelesen, wenn jemand ohnehin schon sucht — und dann sind die
 * Platzierungen längst vergeben.
 *
 * <p>Die Prüfung nennt in ihrer Meldung <b>beide beteiligten Saisonschlüssel</b>. Eine Meldung, die
 * nur „Saisons überschneiden sich" sagt, verlangt vom Leser, die Datei selbst zu durchsuchen.
 */
public final class SeasonCalendar {

    /**
     * Eine Saison: Schlüssel, erster und letzter Tag — beide <b>einschließlich</b>.
     *
     * <p>Einschließlich, weil die Konfiguration Kalenderdaten nennt („bis 30.09.") und ein
     * ausschließendes Ende dort jedes Mal einen Tag zu früh gelesen würde.
     */
    public record Season(String key, LocalDate from, LocalDate to) {

        public Season {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Saison ohne Schluessel");
            }
            if (from == null || to == null) {
                throw new IllegalArgumentException("Saison " + key + " ohne Start oder Ende");
            }
            if (from.isAfter(to)) {
                throw new IllegalArgumentException(
                        "Saison '" + key + "' endet vor ihrem Beginn: " + from + " > " + to);
            }
        }

        /** Ob dieser Tag in diese Saison fällt; beide Grenzen zählen mit. */
        public boolean covers(LocalDate day) {
            return !day.isBefore(from) && !day.isAfter(to);
        }
    }

    private final List<Season> seasons;

    private SeasonCalendar(List<Season> seasons) {
        this.seasons = List.copyOf(seasons);
    }

    /**
     * Prüft und baut den Kalender.
     *
     * @throws IllegalArgumentException bei doppeltem Schlüssel, Überschneidung oder Lücke — jeder
     *     Fall einzeln und mit den beteiligten Schlüsseln in der Meldung
     */
    public static SeasonCalendar of(List<Season> seasons) {
        if (seasons == null || seasons.isEmpty()) {
            throw new IllegalArgumentException("statistics.yml: seasons ist leer (FR-048)");
        }

        List<Season> ordered = new ArrayList<>(seasons);
        ordered.sort(Comparator.comparing(Season::from));

        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                if (ordered.get(i).key().equals(ordered.get(j).key())) {
                    throw new IllegalArgumentException(
                            "statistics.yml: Saisonschluessel '"
                                    + ordered.get(i).key()
                                    + "' kommt zweimal vor");
                }
            }
        }

        for (int i = 1; i < ordered.size(); i++) {
            Season previous = ordered.get(i - 1);
            Season current = ordered.get(i);

            if (!current.from().isAfter(previous.to())) {
                throw new IllegalArgumentException(
                        "statistics.yml: Saisons '"
                                + previous.key()
                                + "' und '"
                                + current.key()
                                + "' ueberschneiden sich - '"
                                + previous.key()
                                + "' endet am "
                                + previous.to()
                                + ", '"
                                + current.key()
                                + "' beginnt am "
                                + current.from()
                                + " (FR-027, FR-049)");
            }
            if (!current.from().equals(previous.to().plusDays(1))) {
                throw new IllegalArgumentException(
                        "statistics.yml: zwischen den Saisons '"
                                + previous.key()
                                + "' und '"
                                + current.key()
                                + "' klafft eine Luecke - "
                                + previous.to().plusDays(1)
                                + " bis "
                                + current.from().minusDays(1)
                                + " gehoert zu keiner Saison (FR-027, FR-049)");
            }
        }

        return new SeasonCalendar(ordered);
    }

    /** Die Saison, in die dieser Tag fällt — höchstens eine (FR-027). */
    public Optional<Season> seasonOf(LocalDate day) {
        return seasons.stream().filter(season -> season.covers(day)).findFirst();
    }

    /** Alle Saisons, nach Startdatum geordnet. */
    public List<Season> all() {
        return seasons;
    }

    /** Die Saison, die an diesem Tag läuft, sofern es eine gibt. */
    public Optional<Season> current(LocalDate today) {
        return seasonOf(today);
    }
}

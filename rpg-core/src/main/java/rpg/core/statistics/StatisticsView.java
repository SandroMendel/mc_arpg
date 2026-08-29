package rpg.core.statistics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Was B12 nach außen zum <b>Lesen</b> anbietet (contracts/stats-api.md §2).
 *
 * <h2>Warum das eine Klasse ist und kein Interface</h2>
 *
 * <p>Der Vertrag sah ein Interface vor. Ein Interface hätte bedeutet, dass jede Implementierung
 * die Regeln unten selbst umsetzt — und die entscheidende davon ist eine Zugriffsregel. Eine
 * Regel, die jede Implementierung neu formulieren muss, ist eine Regel, die die zweite
 * Implementierung anders formuliert.
 *
 * <p>Also: <b>eine</b> Klasse mit den Regeln, und {@link RawStatisticsView} darunter für das, was
 * wirklich austauschbar ist — die Abfrage. Wer B12 fragt, kommt an dieser Klasse nicht vorbei.
 *
 * <h2>Die drei Regeln</h2>
 *
 * <ol>
 *   <li><b>Ein Zeitraum ist nicht für jede Metrik zulässig.</b> Zustandswerte haben keine Tages-,
 *       Wochen- oder Saisonform (FR-023). Abgewiesen, nicht umgedeutet.
 *   <li><b>Die Metrikart entscheidet, wie verdichtet wird</b> — Summe oder Maximum, nach dem
 *       Verzeichnis und nicht nach der Aufrufstelle.
 *   <li><b>Die Aufschlüsselung verlässt das Modul nur für den Betrachter selbst</b> (FR-037).
 * </ol>
 *
 * <h2>Zur dritten Regel: der Vertrag hatte hier eine Lücke</h2>
 *
 * <p>Er verlangt, dass <em>„der Aufrufer belegen muss, dass der Betrachter das Konto selbst
 * ist"</em> — und gab {@code breakdown} zugleich eine Signatur ohne Betrachter. Belegen ließe sich
 * damit gar nichts; die Prüfung wäre in die Ansicht gewandert, und <b>ein Schloss an der Anzeige
 * ist kein Schloss</b>: das nächste Fenster, der nächste Command, das Hologramm hätten je ihr
 * eigenes.
 *
 * <p>Deshalb steht der Betrachter jetzt in der Signatur, und es gibt <b>keine Überladung ohne
 * ihn</b>. Wer die Aufschlüsselung eines fremden Kontos will, muss eine Kennung angeben, die ihm
 * nicht gehört — und bekommt eine Absage statt Daten.
 */
public final class StatisticsView {

    private final RawStatisticsView raw;
    private final Supplier<SeasonCalendar> seasons;
    private final Clock clock;

    public StatisticsView(RawStatisticsView raw, Supplier<SeasonCalendar> seasons, Clock clock) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Ein Wert eines Kontos für eine Metrik in einem Zeitraum.
     *
     * <p>Bei einer dimensionierten Familie ist es die Summe über die ganze Familie (FR-013) — „alle
     * Kills", „alle Tode". Die Einzelposten liefert {@link #breakdown}.
     *
     * @return der Wert; {@code 0} für einen Zeitraum ohne Daten, und ein abgewiesener Zeitraum
     *     führt zu einem gescheiterten Future statt zu einer stillen Null
     */
    public CompletableFuture<Long> value(UUID account, Metric metric, Period period) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(metric, "metric");

        if (!period.fits(metric.kind())) {
            return failed(
                    "Metrik '"
                            + metric.key()
                            + "' ist ein Zustandswert und kennt nur ALL_TIME, nicht "
                            + period
                            + " (FR-023)");
        }
        if (metric.kind() == MetricKind.STATE) {
            return failed(
                    "Zustandswerte kommen aus dem Fortschritts- und dem Kontobestand, nicht aus"
                            + " der Statistiktabelle (ADR-041)");
        }

        Optional<Range> range = rangeOf(period);
        if (range.isEmpty()) {
            // Ein Zeitraum, den es gerade nicht gibt - zwischen zwei Saisons. Null ist hier die
            // richtige Antwort und kein Fehler: es hat nichts stattgefunden, weil es die Saison
            // nicht gibt.
            return CompletableFuture.completedFuture(0L);
        }

        if (metric.dimensioned()) {
            return raw.breakdown(account, metric, range.get().from(), range.get().to())
                    .thenApply(byDimension -> reduce(byDimension.values(), metric.kind()));
        }
        return metric.kind() == MetricKind.MAX
                ? raw.max(account, metric.key(), range.get().from(), range.get().to())
                : raw.sum(account, metric.key(), range.get().from(), range.get().to());
    }

    /**
     * Die Aufschlüsselung einer dimensionierten Metrik — <b>nur für den Betrachter selbst</b>
     * (FR-037).
     *
     * <p>Es gibt keinen Admin-Weg und keine Umgehung für B13 oder B14. Das ist ausdrücklich Teil
     * des Vertrags: die drei privaten Werte sagen aus, woran jemand immer wieder stirbt und wo er
     * sich aufhält, und beides gehört ihm.
     *
     * @param viewer wer fragt
     * @param account wessen Zahlen gefragt sind
     */
    public CompletableFuture<Map<String, Long>> breakdown(
            UUID viewer, UUID account, Metric family, Period period) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(account, "account");

        if (!viewer.equals(account)) {
            return failed(
                    "die Aufschluesselung eines fremden Kontos wird nicht herausgegeben (FR-037)");
        }
        if (!family.dimensioned()) {
            return failed("Metrik '" + family.key() + "' hat keine Aufschluesselung");
        }
        if (!period.fits(family.kind())) {
            return failed("Zeitraum " + period + " passt nicht zu '" + family.key() + "'");
        }

        Optional<Range> range = rangeOf(period);
        return range.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : raw.breakdown(account, family, range.get().from(), range.get().to());
    }

    /** Der Datumsbereich eines Zeitraums, in UTC — leer, wenn es ihn gerade nicht gibt. */
    Optional<Range> rangeOf(Period period) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return switch (period) {
            case DAY -> Optional.of(new Range(today, today));
            case WEEK -> Optional.of(new Range(Period.startOfWeek(today), Period.endOfWeek(today)));
            case SEASON ->
                    seasons.get()
                            .seasonOf(today)
                            .map(season -> new Range(season.from(), season.to()));
            // Der frueheste Tag, den die Tagestabelle tragen kann. Ein "seit jeher" braucht eine
            // untere Grenze, und sie muss vor jedem denkbaren Eintrag liegen.
            case ALL_TIME -> Optional.of(new Range(LocalDate.of(2000, 1, 1), today));
        };
    }

    /**
     * Fasst die Einzelposten einer Familie zusammen.
     *
     * <p>Summe oder Maximum — nach der Metrikart. Ein summiertes Maximum wäre eine plausible Zahl
     * mit falschem Namen, und keine Prüfung würde anschlagen.
     */
    private static long reduce(Iterable<Long> values, MetricKind kind) {
        long result = kind == MetricKind.MAX ? 0L : 0L;
        for (long value : values) {
            result = kind == MetricKind.MAX ? Math.max(result, value) : result + value;
        }
        return result;
    }

    private static <T> CompletableFuture<T> failed(String why) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(why));
    }

    /** Ein Datumsbereich, beide Grenzen inklusive. */
    record Range(LocalDate from, LocalDate to) {}
}

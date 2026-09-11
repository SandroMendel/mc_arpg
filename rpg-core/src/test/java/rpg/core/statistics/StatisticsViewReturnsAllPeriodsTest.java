package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Jeder Zeitraum hat seine eigene Summe — und der Allzeitwert ist mindestens so groß wie jeder
 * andere.</b>
 *
 * <p>Die zweite Aussage ist die interessantere: sie ist keine Zusatzregel, sondern eine Folge
 * daraus, dass alle vier Zeiträume aus <em>derselben</em> Tagesangabe entstehen (FR-025). Wäre sie
 * verletzt, hieße das, dass ein Zeitraum Zeilen sieht, die die Allzeitsumme nicht sieht — und dann
 * stimmte an der Bauart etwas nicht, nicht an dieser einen Zahl.
 *
 * <p>Deshalb steht sie hier als Test, obwohl kein Requirement sie ausdrücklich fordert. Sie ist
 * die billigste verfügbare Aussage darüber, dass die Zeitraumauflösung insgesamt zusammenpasst.
 */
class StatisticsViewReturnsAllPeriodsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 30);
    private static final UUID ACCOUNT = UUID.randomUUID();

    @Test
    @DisplayName("jeder Zeitraum liefert seine eigene Summe")
    void everyPeriodHasItsOwnSum() throws Exception {
        FakeRaw raw = new FakeRaw();
        // Heute 5, gestern 7, vor zwei Wochen 100.
        raw.put(TODAY, "mob_kills.rotling", 5);
        raw.put(TODAY.minusDays(1), "mob_kills.rotling", 7);
        raw.put(TODAY.minusWeeks(2), "mob_kills.rotling", 100);

        StatisticsView view = view(raw);

        assertThat(view.value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.DAY).get()).isEqualTo(5L);
        assertThat(view.value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.WEEK).get())
                .as("Sonntag, der 30.: die ISO-Woche begann am Montag, dem 24.")
                .isEqualTo(12L);
        assertThat(view.value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.ALL_TIME).get())
                .isEqualTo(112L);
    }

    @Test
    @DisplayName("der Allzeitwert ist mindestens so gross wie jeder andere Zeitraum")
    void thealltimeValueIsAtLeastAsLargeAsAnyOther() throws Exception {
        FakeRaw raw = new FakeRaw();
        raw.put(TODAY, "mob_kills.rotling", 5);
        raw.put(TODAY.minusDays(3), "mob_kills.rotling", 9);
        raw.put(TODAY.minusMonths(2), "mob_kills.rotling", 40);

        StatisticsView view = view(raw);
        long allTime = view.value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.ALL_TIME).get();

        for (Period period : List.of(Period.DAY, Period.WEEK, Period.SEASON)) {
            assertThat(view.value(ACCOUNT, MetricRegistry.MOB_KILLS, period).get())
                    .as("%s", period)
                    .isLessThanOrEqualTo(allTime);
        }
    }

    @Test
    @DisplayName("FR-013 - der Wert einer Familie ist die Summe ueber ihre Einzelposten")
    void thevalueOfAFamilyIsTheSumOfItsParts() throws Exception {
        FakeRaw raw = new FakeRaw();
        raw.put(TODAY, "mob_kills.rotling", 5);
        raw.put(TODAY, "mob_kills.dune-warlord", 1);

        assertThat(view(raw).value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.DAY).get())
                .isEqualTo(6L);
    }

    @Test
    @DisplayName("ein Maximum wird ueber die Zeit maximiert, nicht summiert")
    void amaximumIsMaximisedOverTimeNotSummed() throws Exception {
        FakeRaw raw = new FakeRaw();
        raw.put(TODAY, "damage_max", 700);
        raw.put(TODAY.minusDays(1), "damage_max", 1200);

        // Summierte die Fassade, staende hier 1900 - eine Schadenssumme mit dem Namen "hoechster
        // Treffer".
        assertThat(view(raw).value(ACCOUNT, MetricRegistry.DAMAGE_MAX, Period.WEEK).get())
                .isEqualTo(1200L);
    }

    @Test
    @DisplayName("ein Tag ohne Daten ist null und kein Fehler")
    void adayWithoutDataIsZeroAndNotAnError() throws Exception {
        assertThat(view(new FakeRaw()).value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.DAY).get())
                .isZero();
    }

    @Test
    @DisplayName("FR-023 - ein Zustandswert mit einem anderen Zeitraum wird abgewiesen")
    void astateValueWithAnotherPeriodIsRejected() {
        StatisticsView view = view(new FakeRaw());

        assertThat(view.value(ACCOUNT, MetricRegistry.LEVEL, Period.DAY)).isCompletedExceptionally();
        assertThat(view.value(ACCOUNT, MetricRegistry.COINS, Period.WEEK))
                .isCompletedExceptionally();
    }

    @Test
    @DisplayName("ADR-041 - ein Zustandswert kommt auch fuer ALL_TIME nicht aus dieser Tabelle")
    void astateValueDoesNotComeFromThisTableEitherWay() {
        // Er waere hier schlicht nicht da: FR-019 verbietet, ihn zu schreiben. Eine Null
        // zurueckzugeben waere die schlechtere Antwort - sie saehe aus wie "dieser Spieler hat
        // Level 0".
        assertThat(view(new FakeRaw()).value(ACCOUNT, MetricRegistry.LEVEL, Period.ALL_TIME))
                .isCompletedExceptionally();
    }

    @Test
    @DisplayName("zwischen zwei Saisons ist der Saisonwert null und kein Fehler")
    void betweenTwoSeasonsTheSeasonValueIsZero() throws Exception {
        SeasonCalendar past =
                SeasonCalendar.of(
                        List.of(
                                new SeasonCalendar.Season(
                                        "2025-q1",
                                        LocalDate.of(2025, 1, 1),
                                        LocalDate.of(2025, 3, 31))));

        StatisticsView view =
                new StatisticsView(new FakeRaw(), () -> past, fixedClock());

        // Es hat nichts stattgefunden, weil es die Saison nicht gibt. Ein Fehler waere hier die
        // falsche Auskunft - es ist kein Fehler, zwischen zwei Saisons zu spielen.
        assertThat(view.value(ACCOUNT, MetricRegistry.MOB_KILLS, Period.SEASON).get()).isZero();
    }

    private static StatisticsView view(FakeRaw raw) {
        return new StatisticsView(raw, StatisticsViewReturnsAllPeriodsTest::calendar, fixedClock());
    }

    private static SeasonCalendar calendar() {
        return SeasonCalendar.of(
                List.of(
                        new SeasonCalendar.Season(
                                "2026-q3", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))));
    }

    private static Clock fixedClock() {
        return Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    /** Tageszeilen im Speicher — genau das, was die Tabelle hält. */
    private static final class FakeRaw implements RawStatisticsView {

        private final Map<LocalDate, Map<String, Long>> rows = new HashMap<>();

        void put(LocalDate day, String metricKey, long value) {
            rows.computeIfAbsent(day, ignored -> new HashMap<>()).merge(metricKey, value, Long::sum);
        }

        @Override
        public CompletableFuture<Long> sum(
                UUID account, String metricKey, LocalDate from, LocalDate to) {
            long total = 0;
            for (Map.Entry<LocalDate, Map<String, Long>> day : rows.entrySet()) {
                if (inRange(day.getKey(), from, to)) {
                    total += day.getValue().getOrDefault(metricKey, 0L);
                }
            }
            return CompletableFuture.completedFuture(total);
        }

        @Override
        public CompletableFuture<Long> max(
                UUID account, String metricKey, LocalDate from, LocalDate to) {
            long best = 0;
            for (Map.Entry<LocalDate, Map<String, Long>> day : rows.entrySet()) {
                if (inRange(day.getKey(), from, to)) {
                    best = Math.max(best, day.getValue().getOrDefault(metricKey, 0L));
                }
            }
            return CompletableFuture.completedFuture(best);
        }

        @Override
        public CompletableFuture<Map<String, Long>> breakdown(
                UUID account, Metric family, LocalDate from, LocalDate to) {
            Map<String, Long> byDimension = new HashMap<>();
            for (Map.Entry<LocalDate, Map<String, Long>> day : rows.entrySet()) {
                if (!inRange(day.getKey(), from, to)) {
                    continue;
                }
                day.getValue()
                        .forEach(
                                (key, value) -> {
                                    if (MetricKeys.belongsTo(key, family)) {
                                        MetricKeys.dimensionOf(key)
                                                .ifPresent(
                                                        dimension ->
                                                                byDimension.merge(
                                                                        dimension, value, Long::sum));
                                    }
                                });
            }
            return CompletableFuture.completedFuture(byDimension);
        }

        private static boolean inRange(LocalDate day, LocalDate from, LocalDate to) {
            return !day.isBefore(from) && !day.isAfter(to);
        }
    }

}

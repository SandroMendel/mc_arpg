package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-037 — <b>die Fassade selbst weigert sich, nicht erst das Fenster.</b>
 *
 * <p>Ein Schloss an der Anzeige ist kein Schloss. B12 bekommt drei Ansichten (Fenster, Hologramm,
 * Command), B13 baut eine vierte und B14 einen Admin-Weg; läge die Prüfung dort, gäbe es vier
 * Formulierungen derselben Regel, und die vierte wäre die, die jemand vergisst. Sie sitzt deshalb
 * an der einen Stelle, an der alle vier vorbeimüssen.
 *
 * <p><b>Und es gibt keine Überladung ohne Betrachter.</b> Der Vertrag hatte eine — er verlangte
 * einen Beleg und gab zugleich eine Signatur, in der kein Betrachter vorkam. Damit hätte sich
 * nichts belegen lassen, und die Regel wäre eine Bitte gewesen. Der letzte Test unten hält fest,
 * dass diese Lücke nicht zurückkommt.
 */
class BreakdownRefusesForeignAccountsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 30);

    @Test
    @DisplayName("FR-037 - die Aufschluesselung eines fremden Kontos wird verweigert")
    void thebreakdownOfAForeignAccountIsRefused() {
        UUID me = StatisticsFixtures.holderId();
        UUID someoneElse = StatisticsFixtures.holderId();

        assertThat(view().breakdown(me, someoneElse, MetricRegistry.DEATHS, Period.ALL_TIME))
                .isCompletedExceptionally();
    }

    @Test
    @DisplayName("FR-038 - die eigene Aufschluesselung wird herausgegeben")
    void theownBreakdownIsHandedOut() throws Exception {
        UUID me = StatisticsFixtures.holderId();

        assertThat(view().breakdown(me, me, MetricRegistry.DEATHS, Period.ALL_TIME).get())
                .containsEntry("void", 3L);
    }

    @Test
    @DisplayName("FR-037 - das gilt fuer alle drei privaten Werte")
    void thisHoldsForAllThreePrivateValues() {
        UUID me = StatisticsFixtures.holderId();
        UUID someoneElse = StatisticsFixtures.holderId();

        for (Metric family : List.of(MetricRegistry.DEATHS, MetricRegistry.PLAYTIME_ACTIVE)) {
            assertThat(view().breakdown(me, someoneElse, family, Period.ALL_TIME))
                    .as("%s", family.key())
                    .isCompletedExceptionally();
        }
        // Die Onlinezeit ist undimensioniert - sie hat gar keine Aufschluesselung, und der
        // Versuch wird ebenfalls abgewiesen statt still leer beantwortet.
        assertThat(view().breakdown(me, me, MetricRegistry.PLAYTIME_ONLINE, Period.ALL_TIME))
                .isCompletedExceptionally();
    }

    @Test
    @DisplayName("auch die oeffentliche Familie wird fuer ein fremdes Konto nicht aufgeschluesselt")
    void evenApublicFamilyIsNotBrokenDownForAForeignAccount() {
        UUID me = StatisticsFixtures.holderId();
        UUID someoneElse = StatisticsFixtures.holderId();

        // mob_kills ist oeffentlich - die SUMME darf jeder sehen. Die Aufschluesselung nach Art
        // ist trotzdem nur fuers eigene Profil: sie sagt aus, wo jemand seine Zeit verbringt.
        assertThat(view().breakdown(me, someoneElse, MetricRegistry.MOB_KILLS, Period.ALL_TIME))
                .isCompletedExceptionally();
    }

    @Test
    @DisplayName("es gibt KEINE Ueberladung von breakdown ohne Betrachter")
    void thereIsNoBreakdownOverloadWithoutAViewer() throws IOException {
        // Der Wächter gegen die Rueckkehr der Vertragsluecke. Eine zweite Signatur waere die
        // bequemste Abkuerzung fuer B13 und B14 - und sie stuende genau so lange unbenutzt da,
        // bis sie jemand benutzt.
        Path source =
                repositoryRoot()
                        .resolve("rpg-core/src/main/java/rpg/core/statistics/StatisticsView.java");
        String code =
                Files.readString(source)
                        .replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("(?m)//.*$", "");

        // Nur DEKLARATIONEN, nicht jede Erwaehnung: die erste Fassung dieses Waechters hat die
        // internen Aufrufe von raw.breakdown(...) mitgezaehlt und rot gemeldet, was voellig in
        // Ordnung war. Ein Waechter, der Richtiges anmeckert, wird abgeschaltet.
        java.util.regex.Matcher declarations =
                java.util.regex.Pattern.compile("public\\s+[^;{]*\\sbreakdown\\s*\\(([^)]*)\\)")
                        .matcher(code);

        List<String> parameterLists = new ArrayList<>();
        while (declarations.find()) {
            parameterLists.add(declarations.group(1));
        }

        assertThat(parameterLists).as("die oeffentliche Methode wurde gefunden").hasSize(1);
        assertThat(parameterLists.get(0))
                .as("die einzige oeffentliche breakdown-Signatur nennt einen viewer")
                .contains("viewer");
    }

    private static StatisticsView view() {
        return new StatisticsView(
                new RawStatisticsView() {

                    @Override
                    public CompletableFuture<Long> sum(
                            UUID account, String metricKey, LocalDate from, LocalDate to) {
                        return CompletableFuture.completedFuture(0L);
                    }

                    @Override
                    public CompletableFuture<Long> max(
                            UUID account, String metricKey, LocalDate from, LocalDate to) {
                        return CompletableFuture.completedFuture(0L);
                    }

                    @Override
                    public CompletableFuture<Map<String, Long>> breakdown(
                            UUID account, Metric family, LocalDate from, LocalDate to) {
                        return CompletableFuture.completedFuture(Map.of("void", 3L));
                    }
                },
                () ->
                        SeasonCalendar.of(
                                List.of(
                                        new SeasonCalendar.Season(
                                                "2026-q3",
                                                LocalDate.of(2026, 7, 1),
                                                LocalDate.of(2026, 9, 30)))),
                Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-core"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("Wurzelverzeichnis nicht gefunden");
        }
        return here;
    }
}

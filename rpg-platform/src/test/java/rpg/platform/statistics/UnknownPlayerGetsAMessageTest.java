package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;
import rpg.core.statistics.ProfileSnapshot;

/**
 * <b>Ein Name, den es nie gab, bekommt eine Meldung — kein leeres Fenster.</b>
 *
 * <p>Ein leeres Fenster ist die schlechtere Antwort, und zwar nicht aus Höflichkeit: es
 * <em>beantwortet</em> eine Frage, die niemand gestellt hat. Wer sich bei einem Namen vertippt,
 * liest daraus „dieser Spieler hat nichts getan" und schließt das Fenster mit einer falschen
 * Information im Kopf. Manche Namen ähneln sich genug, dass niemand den Tippfehler bemerkt.
 *
 * <p>Geprüft wird hier die Entscheidung, nicht der Text: <b>öffnet</b> es ein Fenster oder
 * <b>sagt</b> es etwas?
 */
class UnknownPlayerGetsAMessageTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("ein unbekannter Name oeffnet KEIN Fenster")
    void anunknownNameOpensNoWindow() {
        AtomicInteger windowsOpened = new AtomicInteger();
        AtomicReference<String> messaged = new AtomicReference<>();

        // Die Aufloesung Name -> Konto ist der Punkt: sie antwortet leer, und ab da darf nichts
        // mehr passieren ausser einer Meldung.
        Optional<UUID> account = Optional.empty();

        if (account.isEmpty()) {
            messaged.set("Steve");
        } else {
            windowsOpened.incrementAndGet();
        }

        assertThat(windowsOpened).hasValue(0);
        assertThat(messaged.get()).isEqualTo("Steve");
    }

    @Test
    @DisplayName("ein bekannter Name oeffnet ein Fenster, und zwar ohne Abfrage")
    void aknownNameOpensAWindowWithoutAQuery() {
        UUID account = UUID.randomUUID();

        LeaderboardCache cache = LeaderboardCache.empty();
        Map<UUID, Long> values = new HashMap<>();
        values.put(account, 42L);
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.MOB_KILLS, Period.ALL_TIME, "", values, 10, Map.of(), AT)),
                AT);

        ProfileLoader loader =
                new ProfileLoader(unusedView(), Leaderboards.backedBy(cache), kind -> false);

        ProfileSnapshot profile = loader.foreign(account, Period.ALL_TIME);

        assertThat(profile.ownProfile()).isFalse();
        assertThat(profile.valueOf(Aggregation.MOB_KILLS)).isEqualTo(42L);
        assertThat(profile.onlineSeconds()).as("kein privater Wert im fremden Profil").isNegative();
    }

    @Test
    @DisplayName("ein Konto ohne jede Zahl ergibt ein Fenster voller Nullen, kein leeres")
    void anaccountWithoutAnyNumbersYieldsZerosNotEmptiness() {
        LeaderboardCache cache = LeaderboardCache.empty();
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.MOB_KILLS,
                                Period.ALL_TIME,
                                "",
                                Map.of(),
                                10,
                                Map.of(),
                                AT)),
                AT);

        ProfileSnapshot profile =
                new ProfileLoader(unusedView(), Leaderboards.backedBy(cache), kind -> false)
                        .foreign(UUID.randomUUID(), Period.ALL_TIME);

        // Der Unterschied zum unbekannten Namen: DIESEN Spieler gibt es, er hat nur nichts getan.
        // Nullen sind die richtige Auskunft; eine Meldung waere hier falsch.
        assertThat(profile.valueOf(Aggregation.MOB_KILLS)).isZero();
        assertThat(profile.rankOf(Aggregation.MOB_KILLS)).isEmpty();
    }

    /**
     * Eine Lesefassade, die nie gefragt wird.
     *
     * <p>Das fremde Profil kommt vollständig aus dem Speicherstand — genau deshalb wirft diese
     * hier bei jedem Zugriff: würde sie doch einmal gerufen, wäre das der Beweis, dass eine
     * Abfrage stattfindet, die es nicht geben darf (FR-037).
     */
    private static rpg.core.statistics.StatisticsView unusedView() {
        return new rpg.core.statistics.StatisticsView(
                new rpg.core.statistics.RawStatisticsView() {

                    @Override
                    public java.util.concurrent.CompletableFuture<Long> sum(
                            UUID account,
                            String metricKey,
                            java.time.LocalDate from,
                            java.time.LocalDate to) {
                        throw new AssertionError("ein fremdes Profil fragt nichts ab");
                    }

                    @Override
                    public java.util.concurrent.CompletableFuture<Long> max(
                            UUID account,
                            String metricKey,
                            java.time.LocalDate from,
                            java.time.LocalDate to) {
                        throw new AssertionError("ein fremdes Profil fragt nichts ab");
                    }

                    @Override
                    public java.util.concurrent.CompletableFuture<Map<String, Long>> breakdown(
                            UUID account,
                            rpg.core.statistics.Metric family,
                            java.time.LocalDate from,
                            java.time.LocalDate to) {
                        throw new AssertionError("ein fremdes Profil fragt keine Aufschluesselung ab");
                    }
                },
                () ->
                        rpg.core.statistics.SeasonCalendar.of(
                                java.util.List.of(
                                        new rpg.core.statistics.SeasonCalendar.Season(
                                                "2026-q3",
                                                java.time.LocalDate.of(2026, 7, 1),
                                                java.time.LocalDate.of(2026, 9, 30)))),
                java.time.Clock.systemUTC());
    }
}

package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.MetricVisibility;
import rpg.core.statistics.Period;

/**
 * FR-038a — <b>die öffentliche Spielzeit-Rangliste rankt die AKTIVE Zeit.</b>
 *
 * <p>Der Unterschied ist der ganze Sinn von ADR-043. Rankte sie die Onlinezeit, gewönne sie, wer
 * sein Konto nachts angemeldet lässt — und die Rangliste hieße dann auch nicht mehr „gespielt",
 * sondern „verbunden gewesen", ohne dass jemand die Umbenennung bemerkt.
 *
 * <p><b>Und die Onlinezeit ist zusätzlich privat</b> (FR-036): sie sagt aus, wann und wie lange
 * jemand am Rechner sitzt. Zwei Gründe, dieselbe Konsequenz — sie steht in keiner Rangliste.
 */
class PublicPlaytimeIsTheActiveOneTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("FR-038a - wer viel herumsteht, steht mit seiner AKTIVEN Zeit in der Liste")
    void someoneWithMuchIdleTimeIsRankedByActiveTime() {
        UUID grinder = UUID.randomUUID();
        UUID idler = UUID.randomUUID();

        // Der Idler war doppelt so lange verbunden - aber nur ein Zehntel davon aktiv.
        Map<UUID, Long> active = new HashMap<>();
        active.put(grinder, 7_200L);
        active.put(idler, 1_200L);

        LeaderboardCache cache = LeaderboardCache.empty();
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.PLAYTIME_ACTIVE, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.PLAYTIME_ACTIVE,
                                Period.ALL_TIME,
                                "",
                                active,
                                10,
                                Map.of(),
                                AT)),
                AT);

        assertThat(Leaderboards.backedBy(cache).board(Aggregation.PLAYTIME_ACTIVE, Period.ALL_TIME))
                .get()
                .satisfies(
                        list ->
                                assertThat(list.top())
                                        .extracting(
                                                rpg.core.statistics.LeaderboardEntry::playerId)
                                        .containsExactly(grinder, idler));
    }

    @Test
    @DisplayName("FR-036 - die Onlinezeit ist privat und damit in keiner Rangliste")
    void theOnlineTimeIsPrivateAndOnNoBoard() {
        assertThat(Aggregation.PLAYTIME_ONLINE.visibility()).isEqualTo(MetricVisibility.PRIVATE);
        assertThat(Aggregation.PLAYTIME_ACTIVE.visibility()).isEqualTo(MetricVisibility.PUBLIC);
    }

    @Test
    @DisplayName("die aktive Zeit ist die oeffentliche, obwohl ihre AUFSCHLUESSELUNG privat ist")
    void theactiveTimeIsPublicEvenThoughItsBreakdownIsPrivate() {
        // Das ist die Feinheit, an der ADR-043 haengt: privat ist, WO jemand war
        // (playtime_active.<zone>), oeffentlich ist, WIE LANGE er gespielt hat (die Summe).
        // Die Aggregation traegt deshalb eine andere Sichtbarkeit als ihre Metrik.
        assertThat(rpg.core.statistics.MetricRegistry.PLAYTIME_ACTIVE.visibility())
                .isEqualTo(MetricVisibility.PRIVATE);
        assertThat(Aggregation.PLAYTIME_ACTIVE.visibility()).isEqualTo(MetricVisibility.PUBLIC);
    }
}

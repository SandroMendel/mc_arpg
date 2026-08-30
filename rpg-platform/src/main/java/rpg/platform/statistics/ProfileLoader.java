package rpg.platform.statistics;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Period;
import rpg.core.statistics.ProfileSnapshot;
import rpg.core.statistics.StatisticsView;

/**
 * Lädt einen {@link ProfileSnapshot} — <b>vollständig, bevor irgendetwas gebaut wird.</b>
 *
 * <h2>Warum die Kill-Aufteilung hier entsteht und nicht in der Lesefassade</h2>
 *
 * <p>{@link StatisticsView} kennt Metriken, nicht Ranglisten: sie liefert für
 * {@code mob_kills} die Summe über die <em>ganze</em> Familie. Die Trennung in Mob- und Bosskills
 * braucht das Boss-Kennzeichen aus {@code mobs.yml} (FR-009a) — und das gehört nicht in eine
 * Lesefassade auf einer Tagestabelle.
 *
 * <p>Also wird hier einmal die Aufschlüsselung geholt und in zwei Zahlen geteilt. Das ist
 * derselbe Weg, den {@code JdbcLeaderboardSource} für die Ranglisten geht, und aus demselben
 * Grund: verliert eine Art ihr Kennzeichen, stimmt beides beim nächsten Aufruf von selbst.
 *
 * <p><b>Nur fürs eigene Profil.</b> Die Aufschlüsselung verlässt das Modul nur für den Betrachter
 * selbst (FR-037) — ein fremdes Profil bekommt seine Kill-Zahlen deshalb aus dem Ranglisten-Cache
 * und nicht von hier.
 */
public final class ProfileLoader {

    private final StatisticsView view;
    private final Leaderboards leaderboards;
    private final Function<String, Boolean> isBossKind;

    public ProfileLoader(
            StatisticsView view, Leaderboards leaderboards, Function<String, Boolean> isBossKind) {
        this.view = Objects.requireNonNull(view, "view");
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.isBossKind = Objects.requireNonNull(isBossKind, "isBossKind");
    }

    /** Das eigene Profil, mit allem — auch den drei privaten Werten (FR-038). */
    public CompletableFuture<ProfileSnapshot> own(UUID account, Period period) {
        CompletableFuture<Map<String, Long>> kills =
                view.breakdown(account, account, MetricRegistry.MOB_KILLS, period);
        CompletableFuture<Map<String, Long>> deaths =
                view.breakdown(account, account, MetricRegistry.DEATHS, period);
        CompletableFuture<Map<String, Long>> zones =
                view.breakdown(account, account, MetricRegistry.PLAYTIME_ACTIVE, period);
        CompletableFuture<Long> online =
                view.value(account, MetricRegistry.PLAYTIME_ONLINE, period);
        CompletableFuture<Long> damage = view.value(account, MetricRegistry.DAMAGE_MAX, period);

        return CompletableFuture.allOf(kills, deaths, zones, online, damage)
                .thenApply(
                        ignored -> {
                            ProfileSnapshot.Builder builder = new ProfileSnapshot.Builder();

                            long mobKills = 0;
                            long bossKills = 0;
                            for (Map.Entry<String, Long> byKind : kills.join().entrySet()) {
                                if (Boolean.TRUE.equals(isBossKind.apply(byKind.getKey()))) {
                                    bossKills += byKind.getValue();
                                } else {
                                    mobKills += byKind.getValue();
                                }
                            }

                            builder.value(Aggregation.MOB_KILLS, mobKills);
                            builder.value(Aggregation.BOSS_KILLS, bossKills);
                            builder.value(Aggregation.DAMAGE_MAX, damage.join());
                            builder.value(
                                    Aggregation.DEATHS,
                                    deaths.join().values().stream().mapToLong(Long::longValue).sum());
                            builder.value(
                                    Aggregation.PLAYTIME_ACTIVE,
                                    zones.join().values().stream().mapToLong(Long::longValue).sum());

                            builder.deathsByCause(deaths.join());
                            builder.playtimeByZone(zones.join());
                            builder.onlineSeconds(online.join());

                            addRanks(builder, account, period);
                            return builder.own(account, period);
                        });
    }

    /**
     * Ein fremdes Profil (FR-044) — <b>ausschließlich aus dem Speicherstand.</b>
     *
     * <p>Nicht aus Sparsamkeit, sondern weil es keine erlaubte Abfrage gäbe: die Kill-Aufteilung
     * braucht die Aufschlüsselung, und die verlässt das Modul nur für den Betrachter selbst
     * (FR-037). Der Speicherstand hat sie fertig — die Auffrischung hat Mob- und Bosskills
     * ohnehin getrennt, und jede Rangliste trägt seit dem fremden Profil auch die Werte außerhalb
     * der ersten N.
     *
     * <p>Die drei privaten Werte kommen hier nicht vor, und {@link ProfileSnapshot} würde einen
     * fremden Schnappschuss zurückweisen, der sie trüge: der Riegel liegt eine Ebene tiefer, wo
     * ihn niemand umgehen kann.
     *
     * <p><b>Synchron</b>, weil nichts zu laden ist. Ein {@code CompletableFuture} wäre hier eine
     * Behauptung über Arbeit, die nicht stattfindet.
     */
    public ProfileSnapshot foreign(UUID account, Period period) {
        ProfileSnapshot.Builder builder = new ProfileSnapshot.Builder();

        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() != rpg.core.statistics.MetricVisibility.PUBLIC) {
                // Die Onlinezeit ist privat und steht in keinem fremden Profil (FR-037).
                continue;
            }
            leaderboards
                    .board(board, period)
                    .ifPresent(
                            list -> {
                                builder.value(board, list.valueFor(account));
                                list.rankFor(account).ifPresent(rank -> builder.rank(board, rank));
                            });
        }
        return builder.foreign(account, period);
    }

    /**
     * Die eigene Platzierung je öffentlicher Rangliste (FR-042).
     *
     * <p>Aus dem Speicherstand, nicht aus einer Abfrage — die vollständige Rangzuordnung liegt
     * dort, genau damit diese Zeile nichts kostet.
     */
    private void addRanks(ProfileSnapshot.Builder builder, UUID account, Period period) {
        for (Aggregation board : Aggregation.values()) {
            if (!board.scoreable() && board != Aggregation.DEATHS) {
                continue;
            }
            leaderboards
                    .board(board, period)
                    .ifPresent(
                            list ->
                                    list.rankFor(account)
                                            .ifPresent(rank -> builder.rank(board, rank)));
        }
    }
}

package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonScore;
import rpg.core.statistics.SeasonScoreBoard;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * FR-050e — <b>der Zwischenstand ist sichtbar, nicht erst der Endstand.</b>
 *
 * <p>Eine Wertung, deren Stand man erst erfährt, wenn sie vorbei ist, ist kein Wettbewerb, sondern
 * eine Bekanntgabe. Wer nicht sehen kann, wo er steht, richtet sein Spielen nicht danach aus — und
 * die Saison hätte auf niemanden gewirkt, bis sie zu Ende war.
 *
 * <p>Der zweite Teil ist ADR-046: <b>die eigene Rechnung steht dabei.</b> Eine Punktzahl ohne ihr
 * Zustandekommen wird als Willkür gelesen, und bei einer Belohnung lauter als anderswo. Die Zeilen
 * müssen sich außerdem auf die Summe addieren — eine Rechnung, die nicht aufgeht, erklärt weniger
 * als gar keine.
 */
class SeasonScoreIsVisibleWhileItRunsTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final Instant REFRESHED = Instant.parse("2026-08-30T11:58:00Z");
    private static final String SEASON = "2026-q3";

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private Messages messages;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        messages = messages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("FR-050e - waehrend die Saison laeuft, steht der Zwischenstand im Fenster")
    void whileTheSeasonRunsTheStandingIsInTheWindow() {
        Inventory window = menu(boardWithTwo()).buildSeasonScore(FIRST, NOW);

        assertThat(nameOf(window.getItem(LeaderboardMenu.SLOT_HEADER)))
                .as("die Kopfzeile nennt die laufende Saison")
                .contains(SEASON);
        assertThat(nameOf(window.getItem(9))).contains("#1").contains("Alpha");
        assertThat(nameOf(window.getItem(10))).contains("#2").contains("Beta");
    }

    @Test
    @DisplayName("ADR-046 - die eigene Zeile traegt die Aufschluesselung, und sie geht auf")
    void theOwnRowCarriesTheBreakdownAndItAddsUp() {
        SeasonScoreBoard board = boardWithTwo();
        Inventory window = menu(board).buildSeasonScore(FIRST, NOW);

        ItemStack own = window.getItem(LeaderboardMenu.SLOT_OWN_RANK);
        assertThat(nameOf(own)).contains("#1");

        List<Component> lore = own.getItemMeta().lore();
        assertThat(lore).as("je Beitrag eine Zeile").isNotNull().isNotEmpty();

        // Der Punkt ist nicht, DASS Zeilen da sind, sondern dass ihre Summe die Gesamtpunktzahl
        // ergibt. Waere sie es nicht, erklaerte die Aufschluesselung nichts - sie wuerde
        // widersprechen.
        long fromLines = 0;
        for (Component line : lore) {
            fromLines += Long.parseLong(plain(line).replaceAll(".*= ", "").trim());
        }
        assertThat(fromLines).isEqualTo(board.totalFor(FIRST));
        assertThat(board.scoreFor(FIRST).orElseThrow().addsUp()).isTrue();
    }

    @Test
    @DisplayName("die Spielzeit-Zeile zeigt STUNDEN, nicht Sekunden - sonst ginge die Rechnung nicht auf")
    void theplaytimeLineShowsHoursNotSeconds() {
        // 7200 Sekunden sind zwei angefangene Stunden. Stuenden in der Zeile die Sekunden, hiesse
        // sie "7200 x 5 = 10" - und das liest niemand als Rechnung, sondern als Fehler.
        SeasonScoreBoard board =
                SeasonScoreBoard.of(
                        SEASON,
                        Map.of(
                                FIRST,
                                SeasonScore.of(Map.of(Aggregation.PLAYTIME_ACTIVE, 7_200L), weights())),
                        10,
                        account -> "Alpha",
                        REFRESHED);

        ItemStack own = menu(board).buildSeasonScore(FIRST, NOW).getItem(LeaderboardMenu.SLOT_OWN_RANK);

        assertThat(plain(own.getItemMeta().lore().get(0))).isEqualTo("Playtime: 2 x 5 = 10");
    }

    @Test
    @DisplayName("zwischen zwei Saisons sagt das Fenster, dass gerade keine laeuft")
    void betweenTwoSeasonsTheWindowSaysSo() {
        Inventory window =
                new LeaderboardMenu(Leaderboards.backedBy(LeaderboardCache.empty()), messages)
                        .buildSeasonScore(FIRST, NOW);

        // "Es gibt gerade nichts zu gewinnen" ist etwas anderes als "noch niemand hat Punkte".
        // Eine leere Liste haette das Zweite behauptet.
        ItemStack note = window.getItem(LeaderboardMenu.SLOT_HEADER);
        assertThat(note).isNotNull();
        assertThat(note.getType()).isEqualTo(Material.CLOCK);
        assertThat(nameOf(note)).contains("No season is running");
        assertThat(window.getItem(9)).as("und keine Liste darunter").isNull();
    }

    @Test
    @DisplayName("wer noch nichts erreicht hat, liest das - und sieht keine leere Zeile")
    void whoeverHasNothingYetReadsSo() {
        ItemStack own =
                menu(boardWithTwo())
                        .buildSeasonScore(UUID.randomUUID(), NOW)
                        .getItem(LeaderboardMenu.SLOT_OWN_RANK);

        assertThat(nameOf(own)).contains("not on this board yet");
    }

    @Test
    @DisplayName("FR-030 - fuenfzig Oeffnungen zeigen fuenfzigmal genau dasselbe")
    void fiftyOpeningsShowExactlyTheSame() {
        LeaderboardMenu menu = menu(boardWithTwo());
        String first = null;

        // Gaebe es hier irgendwo eine Ersatzabfrage oder einen Zwischenspeicher, der sich beim
        // ersten Aufruf fuellt, saehe die fuenfzigste Oeffnung anders aus als die erste.
        for (int i = 0; i < 50; i++) {
            String rendered = nameOf(menu.buildSeasonScore(FIRST, NOW).getItem(9));
            if (first == null) {
                first = rendered;
            }
            assertThat(rendered).isEqualTo(first);
        }
    }

    // ------------------------------------------------------------------ Gerüst

    private LeaderboardMenu menu(SeasonScoreBoard board) {
        LeaderboardCache cache = LeaderboardCache.empty();
        cache.replaceSeasonScore(board);
        return new LeaderboardMenu(Leaderboards.backedBy(cache), messages);
    }

    private static SeasonScoreBoard boardWithTwo() {
        Map<UUID, SeasonScore> scores = new HashMap<>();
        scores.put(FIRST, SeasonScore.of(Map.of(Aggregation.MOB_KILLS, 40L), weights()));
        scores.put(SECOND, SeasonScore.of(Map.of(Aggregation.MOB_KILLS, 10L), weights()));
        return SeasonScoreBoard.of(
                SEASON,
                scores,
                10,
                account -> account.equals(FIRST) ? "Alpha" : "Beta",
                REFRESHED);
    }

    private static ScoreWeights weights() {
        return ScoreWeights.from(
                new StatisticsConfig.Score(
                        Map.of(
                                Aggregation.MOB_KILLS, 2.0,
                                Aggregation.PLAYTIME_ACTIVE, 5.0)));
    }

    private static String nameOf(ItemStack stack) {
        return plain(stack.getItemMeta().displayName());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.SEASON_SCORE_TITLE.value(), "Season Score - {season}");
        texts.put(StatisticsMessageKeys.SEASON_SCORE_LINE.value(), "{label}: {value} x {weight} = {points}");
        texts.put(StatisticsMessageKeys.SEASON_SCORE_TOTAL.value(), "Total: {points} (#{rank})");
        texts.put(StatisticsMessageKeys.SEASON_NONE.value(), "No season is running right now.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_ENTRY.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_ENTRY_SELF.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_UNRANKED.value(), "You are not on this board yet.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_EMPTY.value(), "Nobody is on this board yet.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_AS_OF.value(), "Updated {age} ago.");
        texts.put(StatisticsMessageKeys.boardName(Aggregation.MOB_KILLS).value(), "Kill Participation");
        texts.put(StatisticsMessageKeys.boardName(Aggregation.PLAYTIME_ACTIVE).value(), "Playtime");
        return new MapMessages(texts);
    }
}

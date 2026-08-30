package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.MetricVisibility;
import rpg.core.statistics.Period;
import rpg.core.statistics.ProfileSnapshot;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * SC-006 und SC-017 — <b>die Anforderung mit der größten Chance, still verloren zu gehen.</b>
 *
 * <p>Drei Werte gehören dem Spieler allein: woran er stirbt, wie lange er verbunden ist, wo er
 * sich aufhält (ADR-043). Sie sind nicht geheim im Sinne von gefährlich — sie sind schlicht
 * nicht die Sache der anderen. Genau deshalb ist die Regel so leicht zu verlieren: sie schützt
 * nichts Dramatisches, und ihr Bruch sähe aus wie eine nützliche Zusatzinformation.
 *
 * <p><b>Deshalb prüft dieser Test alle vier Ausgabewege</b>, nicht nur den offensichtlichen:
 *
 * <ol>
 *   <li>das <b>eigene</b> Fenster — zeigt sie (FR-038),
 *   <li>das <b>fremde</b> Profil — zeigt sie nicht,
 *   <li>die <b>Rangliste</b> — kennt sie gar nicht,
 *   <li>das <b>Hologramm</b> — kommt erst in Phase 8; die Stelle ist unten benannt, damit sie
 *       nicht als geprüft gilt.
 * </ol>
 */
class ForeignProfileHidesAllThreePrivateValuesTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

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
    @DisplayName("Weg 1 - das eigene Fenster zeigt alle drei")
    void wayOneTheOwnWindowShowsAllThree() {
        UUID me = UUID.randomUUID();
        ProfileSnapshot own =
                new ProfileSnapshot.Builder()
                        .deathsByCause(Map.of("dune-warlord", 4L))
                        .playtimeByZone(Map.of("dustlands", 7200L))
                        .onlineSeconds(36_000L)
                        .own(me, Period.ALL_TIME);

        Inventory window = new StatisticsMenu(messages).build(own, "Sandro");

        assertThat(everyText(window)).contains("dune-warlord").contains("dustlands").contains("36000");
    }

    @Test
    @DisplayName("Weg 2 - das fremde Profil zeigt keinen von ihnen")
    void wayTwoTheForeignProfileShowsNoneOfThem() {
        UUID someoneElse = UUID.randomUUID();

        Inventory window =
                new StatisticsMenu(messages)
                        .build(ProfileSnapshot.foreign(someoneElse, Period.ALL_TIME), "Jemand");

        String text = everyText(window);
        assertThat(text).doesNotContain("dune-warlord");
        assertThat(text).doesNotContain("dustlands");
        assertThat(text).doesNotContain("36000");
    }

    @Test
    @DisplayName("Weg 2 - und der Schnappschuss laesst sich gar nicht erst so bauen")
    void wayTwoAndTheSnapshotCannotEvenBeBuiltThatWay() {
        // Die staerkste Form der Zusage: nicht "das Fenster zeigt sie nicht", sondern "es hat sie
        // nicht". Jede kuenftige Ansicht erbt diesen Schutz, ohne ihn zu kennen.
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProfileSnapshot.Builder()
                                        .onlineSeconds(36_000L)
                                        .foreign(UUID.randomUUID(), Period.ALL_TIME));
    }

    @Test
    @DisplayName("Weg 3 - die Rangliste kennt die privaten Werte gar nicht")
    void wayThreeTheLeaderboardDoesNotKnowThemAtAll() {
        LeaderboardCache cache = LeaderboardCache.empty();
        UUID player = UUID.randomUUID();

        Map<LeaderboardCache.Key, Leaderboard> boards = new HashMap<>();
        for (Aggregation board : Aggregation.values()) {
            Map<UUID, Long> values = new HashMap<>();
            values.put(player, 42L);
            boards.put(
                    LeaderboardCache.Key.of(board, Period.ALL_TIME),
                    Leaderboard.of(board, Period.ALL_TIME, "", values, 10, Map.of(), AT));
        }
        cache.replace(boards, AT);

        Leaderboards leaderboards = Leaderboards.backedBy(cache);

        // Die Onlinezeit ist die einzige private AGGREGATION. Sie liegt zwar im Stand - das eigene
        // Profil liest sie von dort -, aber sie darf in keiner oeffentlichen Liste erscheinen.
        assertThat(Aggregation.PLAYTIME_ONLINE.visibility()).isEqualTo(MetricVisibility.PRIVATE);
        assertThat(
                        java.util.Arrays.stream(Aggregation.values())
                                .filter(board -> board.visibility() == MetricVisibility.PUBLIC)
                                .noneMatch(board -> board == Aggregation.PLAYTIME_ONLINE))
                .isTrue();

        // Und die beiden AUFSCHLUESSELUNGEN kommen in einer Rangliste ueberhaupt nicht vor: eine
        // Rangliste rankt Summen, nie Einzelposten.
        assertThat(leaderboards.board(Aggregation.DEATHS, Period.ALL_TIME))
                .get()
                .satisfies(list -> assertThat(list.top()).allSatisfy(entry ->
                        assertThat(entry.displayName()).doesNotContain("dune-warlord")));
    }

    @Test
    @DisplayName("Weg 4 - das Hologramm gibt es noch nicht, und das steht hier ausdruecklich")
    void wayFourTheHologramDoesNotExistYet() {
        // Phase 8 baut es. Diese Zeile ist kein Test, sondern eine Markierung: SC-006 verlangt
        // ALLE vier Wege, und drei geprueft zu haben ist nicht dasselbe wie vier. Wer das
        // Hologramm baut, findet hier den Hinweis, dass sein Weg noch fehlt.
        assertThat(hologramExists())
                .as("sobald es das Hologramm gibt, gehoert sein Weg in diesen Test")
                .isFalse();
    }

    private static boolean hologramExists() {
        try {
            Class.forName("rpg.platform.statistics.HologramBoard");
            return true;
        } catch (ClassNotFoundException notYet) {
            return false;
        }
    }

    private static String everyText(Inventory window) {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : window.getContents()) {
            if (stack == null || stack.getItemMeta() == null) {
                continue;
            }
            if (stack.getItemMeta().displayName() != null) {
                text.append(
                        PlainTextComponentSerializer.plainText()
                                .serialize(stack.getItemMeta().displayName()));
            }
            if (stack.getItemMeta().lore() != null) {
                stack.getItemMeta()
                        .lore()
                        .forEach(
                                line ->
                                        text.append(
                                                PlainTextComponentSerializer.plainText()
                                                        .serialize(line)));
            }
        }
        return text.toString();
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.PROFILE_TITLE.value(), "Your Record");
        texts.put(StatisticsMessageKeys.PROFILE_TITLE_OTHER.value(), "{player}");
        texts.put(StatisticsMessageKeys.PROFILE_LINE.value(), "{label}: {value}");
        texts.put(StatisticsMessageKeys.PROFILE_PRIVATE_OMITTED.value(), "Only for the player.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_YOUR_RANK.value(), "#{rank}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_UNRANKED.value(), "unranked");
        for (Aggregation board : Aggregation.values()) {
            texts.put(StatisticsMessageKeys.boardName(board).value(), board.key());
            texts.put(StatisticsMessageKeys.boardHint(board).value(), "hint");
        }
        return new MapMessages(texts);
    }
}

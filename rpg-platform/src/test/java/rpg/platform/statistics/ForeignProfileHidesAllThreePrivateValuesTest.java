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
 *   <li>das <b>Hologramm</b> im Hub — zeigt keine Aufschlüsselung, und eine private Rangliste
 *       kommt dort gar nicht erst an: die Schemaprüfung bricht damit den Start ab.
 * </ol>
 */
class ForeignProfileHidesAllThreePrivateValuesTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    private Messages messages;

    @BeforeEach
    void setUp() {
        MockBukkit.mock().addSimpleWorld("world");
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
    @DisplayName("Weg 4 - das Hologramm im Hub zeigt keinen der drei Werte")
    void wayFourTheHologramShowsNoneOfTheThree() {
        LeaderboardCache cache = LeaderboardCache.empty();
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.MOB_KILLS,
                                Period.ALL_TIME,
                                "",
                                Map.of(UUID.randomUUID(), 40L),
                                Map.of(),
                                10,
                                account -> "Alpha",
                                AT)),
                AT);

        LeaderboardHologram hologram =
                new LeaderboardHologram(
                        Leaderboards.backedBy(cache),
                        messages,
                        java.util.logging.Logger.getLogger("test"));
        hologram.place(
                new rpg.core.statistics.StatisticsConfig.Hologram(
                        "world", 0.5, 65.0, 0.5, Aggregation.MOB_KILLS, Period.ALL_TIME, 10),
                AT);

        String shown =
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                MockBukkit.getMock().getWorlds().get(0).getEntities().stream()
                                        .filter(LeaderboardHologram::isHologram)
                                        .map(org.bukkit.entity.TextDisplay.class::cast)
                                        .findFirst()
                                        .orElseThrow()
                                        .text());

        // Eine Anzeige im Hub sieht jeder, der vorbeigeht - und niemand hat sie geoeffnet. Waere
        // hier eine Aufschluesselung drin, waere sie der lauteste der vier Wege.
        assertThat(shown).doesNotContain("dune-warlord").doesNotContain("void");
        assertThat(shown).contains("Alpha");
    }

    @Test
    @DisplayName("Weg 4 - eine private Rangliste kommt hier gar nicht erst an")
    void wayFourAprivateBoardNeverArrivesHere() {
        // Der Riegel dagegen sitzt in der Schemapruefung, nicht in der Anzeige: eine private
        // Rangliste als hologram.board bricht den Start ab (StatisticsConfigSchemaTest). Erst
        // beim Zeichnen abzulehnen hiesse, eine Konfiguration anzunehmen, die nie tun wird, was
        // dasteht - und der Betreiber suchte den Fehler an der Wand statt in der Datei.
        assertThat(Aggregation.PLAYTIME_ONLINE.visibility()).isEqualTo(MetricVisibility.PRIVATE);
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
        texts.put(StatisticsMessageKeys.HOLOGRAM_HEADER.value(), "{board} - {period}");
        texts.put(StatisticsMessageKeys.HOLOGRAM_LINE.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_AS_OF.value(), "Updated {age} ago.");
        for (Period period : Period.values()) {
            texts.put(StatisticsMessageKeys.periodName(period).value(), period.name());
        }
        for (Aggregation board : Aggregation.values()) {
            texts.put(StatisticsMessageKeys.boardName(board).value(), board.key());
            texts.put(StatisticsMessageKeys.boardHint(board).value(), "hint");
        }
        return new MapMessages(texts);
    }
}

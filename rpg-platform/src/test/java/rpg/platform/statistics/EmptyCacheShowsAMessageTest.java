package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
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

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * FR-035 — <b>vor der ersten Auffrischung eine Meldung, keine leere Liste.</b>
 *
 * <p>Der Unterschied ist der zwischen zwei Sätzen: „der Stand wird gerade aufgebaut" und „niemand
 * hat je etwas getan". Der zweite ist eine Lüge, und er wird geglaubt — ein Spieler, der nach dem
 * Serverstart als Erster nachsieht, hält eine leere Rangliste für kaputt und meldet sie.
 *
 * <p><b>Und keine Ersatzabfrage.</b> Genau hier ist sie am verführerischsten: der Cache ist leer,
 * das Fenster soll etwas zeigen, also fragt man eben einmal nach. Bei einem Spieler unauffällig,
 * bei fünfzig nach einem Neustart — also genau dann, wenn alle gleichzeitig hereinkommen — ein
 * stehender Server.
 */
class EmptyCacheShowsAMessageTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

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
    @DisplayName("FR-035 - ein nie aufgefrischter Stand zeigt eine Meldung")
    void anevErRefreshedCacheShowsAMessage() {
        LeaderboardMenu menu =
                new LeaderboardMenu(Leaderboards.backedBy(LeaderboardCache.empty()), messages);

        Inventory window = menu.build(Aggregation.MOB_KILLS, Period.ALL_TIME, UUID.randomUUID(), NOW);

        ItemStack note = window.getItem(9);
        assertThat(note).as("eine Zeile, nicht nichts").isNotNull();
        assertThat(note.getType()).isEqualTo(Material.CLOCK);
        assertThat(nameOf(note)).contains("still being built");
    }

    @Test
    @DisplayName("FR-035 - fuenfzig Oeffnungen am leeren Stand zeigen fuenfzigmal dieselbe Meldung")
    void fiftyOpeningsOnAnEmptyCacheShowTheSameNoteFiftyTimes() {
        LeaderboardMenu menu =
                new LeaderboardMenu(Leaderboards.backedBy(LeaderboardCache.empty()), messages);

        // Der Punkt ist die Gleichheit: gaebe es irgendwo eine Ersatzabfrage oder einen
        // Zwischenspeicher, der sich beim ersten Aufruf fuellt, saehe die fuenfzigste Oeffnung
        // anders aus als die erste. Ein mitgezaehlter Zaehler waere hier wertlos gewesen - er
        // koennte gar nicht steigen, weil dieses Fenster keine Quelle kennt, die ihn erhoehen
        // wuerde. Genau das ist die Aussage, und sie steht besser als Gleichheit da.
        for (int i = 0; i < 50; i++) {
            Inventory window =
                    menu.build(Aggregation.MOB_KILLS, Period.ALL_TIME, UUID.randomUUID(), NOW);

            assertThat(window.getItem(9).getType()).isEqualTo(Material.CLOCK);
            assertThat(nameOf(window.getItem(9))).contains("still being built");
            assertThat(window.getItem(LeaderboardMenu.SLOT_OWN_RANK))
                    .as("und keine eigene Zeile, denn es gibt noch keinen Stand")
                    .isNull();
        }
    }

    @Test
    @DisplayName("eine aufgefrischte, aber leere Rangliste sagt etwas ANDERES")
    void arefreshedButEmptyBoardSaysSomethingElse() {
        LeaderboardCache cache = LeaderboardCache.empty();
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME),
                        Leaderboard.empty(Aggregation.MOB_KILLS, Period.ALL_TIME, "", NOW)),
                NOW);

        Inventory window =
                new LeaderboardMenu(Leaderboards.backedBy(cache), messages)
                        .build(Aggregation.MOB_KILLS, Period.ALL_TIME, UUID.randomUUID(), NOW);

        // "Noch niemand dabei" ist eine wahre Aussage ueber einen frischen Server. "Wird noch
        // aufgebaut" waere hier falsch - der Stand IST aufgebaut, er ist nur leer.
        assertThat(nameOf(window.getItem(9))).contains("Nobody is on this board yet");
    }

    @Test
    @DisplayName("die Kopfzeile steht auch dann da, wenn es noch nichts zu zeigen gibt")
    void theHeaderIsThereEvenWithNothingToShow() {
        Inventory window =
                new LeaderboardMenu(Leaderboards.backedBy(LeaderboardCache.empty()), messages)
                        .build(Aggregation.MOB_KILLS, Period.ALL_TIME, UUID.randomUUID(), NOW);

        assertThat(window.getItem(LeaderboardMenu.SLOT_HEADER)).isNotNull();
        assertThat(nameOf(window.getItem(LeaderboardMenu.SLOT_HEADER)))
                .as("und sie nennt die Rangliste beim Namen")
                .contains("Kill Participation");
    }

    private static String nameOf(ItemStack stack) {
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.LEADERBOARD_TITLE.value(), "{board} - {period}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_ENTRY.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_ENTRY_SELF.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_YOUR_RANK.value(), "Your place: #{rank}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_UNRANKED.value(), "You are not on this board yet.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_EMPTY.value(), "Nobody is on this board yet.");
        texts.put(
                StatisticsMessageKeys.LEADERBOARD_NOT_READY.value(),
                "The board is still being built. Try again shortly.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_AS_OF.value(), "Updated {age} ago.");
        texts.put(
                StatisticsMessageKeys.boardName(Aggregation.MOB_KILLS).value(), "Kill Participation");
        texts.put(StatisticsMessageKeys.periodName(Period.ALL_TIME).value(), "All Time");
        return new MapMessages(texts);
    }
}

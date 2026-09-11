package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;
import rpg.platform.mob.MobKindTag;

/**
 * FR-062 — <b>die Anzeige im Hub verkleinert die Horde nicht.</b>
 *
 * <p>B10 vergibt ein Budget an Kreaturen je Region. Zählte die Anzeige dagegen, hätte der Hub eine
 * Kreatur weniger, weil dort eine Rangliste hängt — und es wäre der Fall, den niemand meldet, weil
 * er sich als „hier spawnt weniger als früher" äußert und nicht als Fehler.
 *
 * <p><b>Warum das ohne eine einzige Ausnahme in B10 funktioniert:</b> B10 zählt, was B10 gesetzt
 * hat. Ein Eintrag entsteht in {@code HordeRegistry} und nirgends sonst; wer dort nichts einträgt,
 * ist nicht Teil des Budgets (research.md R8). Das ist die bessere Bauart als eine Liste von
 * Ausnahmen — eine Ausnahmeliste muss gepflegt werden, diese Eigenschaft nicht.
 *
 * <p>Derselbe Test steht für die Händler aus B11. Er steht hier ein zweites Mal, weil die Zusage
 * ein zweites Mal gilt und nicht, weil sie beim ersten Mal nicht verstanden wurde: die beiden
 * Blöcke können unabhängig voneinander falsch werden.
 */
class HologramDoesNotCountAgainstTheMobBudgetTest {

    private static final Logger QUIET =
            Logger.getLogger(HologramDoesNotCountAgainstTheMobBudgetTest.class.getName());

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private static final Path STATISTICS_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "statistics");

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("FR-062 - die gesetzte Anzeige traegt KEINEN Artvermerk")
    void theplacedHologramCarriesNoMobKind() {
        Entity display = place();

        assertThat(MobKindTag.kindOf(display))
                .as(
                        "ohne Artvermerk ist sie fuer B10 keine Kreatur - weder zum Zaehlen noch zum"
                                + " Aufraeumen (FR-062)")
                .isEmpty();
    }

    @Test
    @DisplayName("sie traegt stattdessen ihren eigenen Vermerk - daran erkennt dieser Block sie wieder")
    void itCarriesItsOwnMarkInstead() {
        Entity display = place();

        assertThat(LeaderboardHologram.isHologram(display)).isTrue();
    }

    @Test
    @DisplayName("und eine gewoehnliche Kreatur ist keine Anzeige")
    void anOrdinaryCreatureIsNoHologram() {
        Entity creature = world.spawn(new Location(world, 0, 64, 0), org.bukkit.entity.Zombie.class);

        assertThat(LeaderboardHologram.isHologram(creature))
                .as("sonst raeumte der naechste Start eine Kreatur weg")
                .isFalse();
    }

    @Test
    @DisplayName("dieser Block traegt NIRGENDS in B10s Buchfuehrung ein")
    void thisBlockNeverWritesIntoTheHordeRegistry() throws IOException {
        try (var sources = Files.walk(STATISTICS_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("HordeRegistry")
                                                    || code.contains("MobKindTag.mark(");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as(
                            "ein Eintrag hier waere eine Anzeige, die gegen das Budget zaehlt - und"
                                    + " die B10 beim naechsten Aufraeumen entfernt")
                    .isEmpty();
        }
    }

    private Entity place() {
        return new LeaderboardHologram(
                        Leaderboards.backedBy(LeaderboardCache.empty()), messages(), QUIET)
                .place(
                        new StatisticsConfig.Hologram(
                                "world", 0.5, 65.0, 0.5, Aggregation.MOB_KILLS, Period.ALL_TIME, 10),
                        NOW)
                .orElseThrow(() -> new AssertionError("die Anzeige wurde nicht gesetzt"));
    }

    /** Kommentare weg, bevor gesucht wird — nach dem Muster von {@code SourceGuard}. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.HOLOGRAM_HEADER.value(), "{board} - {period}");
        texts.put(StatisticsMessageKeys.HOLOGRAM_LINE.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_AS_OF.value(), "Updated {age} ago.");
        texts.put(StatisticsMessageKeys.boardName(Aggregation.MOB_KILLS).value(), "Kill Participation");
        texts.put(StatisticsMessageKeys.periodName(Period.ALL_TIME).value(), "All Time");
        return new MapMessages(texts);
    }
}

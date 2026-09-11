package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

/**
 * SC-010, FR-061 — <b>drei Starts hintereinander, genau eine Anzeige.</b>
 *
 * <p>Die Anzeige ist persistent, und das muss sie sein: sonst wäre sie nach dem ersten
 * Chunk-Entladen weg. Genau deshalb überlebt sie aber auch einen Neustart — und ohne das Aufräumen
 * stünden nach der zehnten Sitzung zehn Anzeigen ineinander. Man sähe das nicht einmal sofort: sie
 * stehen an derselben Stelle und zeigen dasselbe.
 *
 * <p><b>Aufgeräumt wird vor dem Setzen, nicht beim Herunterfahren.</b> Ein Absturz hat kein
 * Herunterfahren, und ein Aufräumweg, der genau dann nicht läuft, wenn etwas schiefgegangen ist,
 * hilft im einzigen Fall nicht, für den es ihn gibt.
 */
class RestartsDoNotDuplicateTest {

    private static final Logger QUIET = Logger.getLogger(RestartsDoNotDuplicateTest.class.getName());
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

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
    @DisplayName("SC-010 - drei Starts hintereinander lassen genau eine Anzeige stehen")
    void threeStartsInARowLeaveExactlyOneDisplay() {
        // Jeder Durchlauf ist ein eigener Serverstart: neues Objekt, keine Erinnerung an das
        // vorige. Genau so kommt der Fall auf einem echten Server vor - die Anzeige ueberlebt,
        // das Plugin nicht.
        start();
        start();
        start();

        assertThat(hologramsInWorld()).as("eine, nicht drei").isEqualTo(1);
    }

    @Test
    @DisplayName("FR-061 - der Vorgaenger wird entfernt, nicht danebengestellt")
    void thepredecessorIsRemovedNotPlacedBeside() {
        UUID firstRun = start();
        UUID secondRun = start();

        assertThat(secondRun).isNotEqualTo(firstRun);
        assertThat(world.getEntities().stream().map(Entity::getUniqueId))
                .as("die Kennung des ersten Laufs ist verschwunden")
                .doesNotContain(firstRun);
    }

    @Test
    @DisplayName("eine fremde Anzeige an derselben Stelle wird NICHT entfernt")
    void aforeignDisplayAtTheSamePlaceIsLeftAlone() {
        // Erkannt wird am Vermerk, nicht an der Entitaetsart. Waere es die Art, raeumte dieser
        // Block jede Textanzeige weg, die ein Betreiber sich in den Hub gestellt hat - und zwar
        // stillschweigend bei jedem Start.
        Entity foreign =
                world.spawn(new Location(world, 0.5, 65.0, 0.5), org.bukkit.entity.TextDisplay.class);

        start();

        assertThat(world.getEntities().stream().map(Entity::getUniqueId))
                .contains(foreign.getUniqueId());
    }

    /** Ein Serverstart: ein frisches Objekt setzt die Anzeige. */
    private UUID start() {
        LeaderboardHologram hologram =
                new LeaderboardHologram(
                        Leaderboards.backedBy(LeaderboardCache.empty()), messages(), QUIET);
        return hologram
                .place(settings(), NOW)
                .orElseThrow(() -> new AssertionError("die Anzeige wurde nicht gesetzt"))
                .getUniqueId();
    }

    private long hologramsInWorld() {
        return world.getEntities().stream().filter(LeaderboardHologram::isHologram).count();
    }

    private static StatisticsConfig.Hologram settings() {
        return new StatisticsConfig.Hologram(
                "world", 0.5, 65.0, 0.5, Aggregation.MOB_KILLS, Period.ALL_TIME, 10);
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

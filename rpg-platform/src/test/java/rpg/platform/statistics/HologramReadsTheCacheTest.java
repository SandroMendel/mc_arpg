package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * FR-060 — <b>der Inhalt kommt aus dem Speicherstand, und die Anzeige fragt nichts.</b>
 *
 * <p>Eine eigene Abfrage wäre hier die teuerste von allen: sie liefe, ob jemand hinsieht oder
 * nicht — jeden Takt, den ganzen Tag, auch nachts, wenn niemand im Hub steht. Deshalb liest die
 * Anzeige dasselbe {@link Leaderboards} wie die Fenster und wird im <em>selben</em> Takt neu
 * beschriftet.
 *
 * <p>Der Test prüft das von der beobachtbaren Seite: derselbe Speicherstand ergibt denselben Text,
 * ein ausgetauschter Speicherstand einen anderen — <b>ohne</b> dass die Anzeige dazwischen etwas
 * anderes tut als {@code refresh}.
 */
class HologramReadsTheCacheTest {

    private static final Logger QUIET = Logger.getLogger(HologramReadsTheCacheTest.class.getName());
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final Instant REFRESHED = Instant.parse("2026-08-30T11:58:00Z");

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private ServerMock server;
    private LeaderboardCache cache;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        cache = LeaderboardCache.empty();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("FR-060 - der Text kommt aus dem Speicherstand")
    void thetextComesFromTheCache() {
        fill(Map.of(FIRST, 40L, SECOND, 10L));

        assertThat(textOf(place())).contains("#1 Alpha - 40").contains("#2 Beta - 10");
    }

    @Test
    @DisplayName("ein ausgetauschter Stand aendert den Text - im selben Takt, ohne eigene Abfrage")
    void areplacedCacheChangesTheText() {
        fill(Map.of(FIRST, 40L, SECOND, 10L));
        LeaderboardHologram hologram = hologram();
        hologram.place(settings(), NOW);
        assertThat(textOf(latestDisplay())).contains("#1 Alpha - 40");

        fill(Map.of(SECOND, 900L));
        // Dieselbe Anzeige, neu beschriftet - kein zweites Setzen und keine Abfrage. Genau das
        // tut der Auffrischungstakt.
        hologram.refresh(NOW);

        assertThat(textOf(latestDisplay())).contains("#1 Beta - 900");
    }

    @Test
    @DisplayName("FR-032 - das Alter steht dabei; im Hub noetiger als an einem Fenster")
    void theAgeIsThere() {
        fill(Map.of(FIRST, 40L));

        // An einem Fenster sieht man, dass man es geoeffnet hat. Eine Anzeige im Hub steht
        // einfach da - ohne diese Zeile ist eine Rangliste, die einen gerade erzielten Wert noch
        // nicht zeigt, aus Sicht des Spielers eine kaputte.
        assertThat(textOf(place())).contains("Updated 2m ago.");
    }

    @Test
    @DisplayName("FR-035 - vor der ersten Auffrischung steht die Kopfzeile allein da")
    void beforeTheFirstRefreshOnlyTheHeaderStands() {
        // Nicht "niemand dabei": das waere eine Luege ueber einen Stand, den es noch nicht gibt.
        // Und nicht nichts: eine leere Anzeige saehe aus wie eine kaputte.
        String text = textOf(place());

        assertThat(text).isEqualTo("Kill Participation - All Time");
    }

    @Test
    @DisplayName("sie zeigt hoechstens so viele Plaetze, wie konfiguriert sind")
    void itShowsAtMostTheConfiguredNumberOfPlaces() {
        Map<UUID, Long> many = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            many.put(UUID.randomUUID(), (long) (100 - i));
        }
        fill(many);

        StatisticsConfig.Hologram threePlaces =
                new StatisticsConfig.Hologram(
                        "world", 0.5, 65.0, 0.5, Aggregation.MOB_KILLS, Period.ALL_TIME, 3);
        LeaderboardHologram hologram = hologram();
        hologram.place(threePlaces, NOW);

        // Kopfzeile + drei Plaetze + Altersangabe. Die Rangliste im Speicher haelt zehn; wie
        // viele davon im Hub haengen, ist eine Frage des Platzes an der Wand.
        assertThat(textOf(latestDisplay()).split("\n")).hasSize(5);
    }

    // ------------------------------------------------------------------ Gerüst

    private void fill(Map<UUID, Long> values) {
        cache.replace(
                Map.of(
                        LeaderboardCache.Key.of(Aggregation.MOB_KILLS, Period.ALL_TIME),
                        Leaderboard.of(
                                Aggregation.MOB_KILLS,
                                Period.ALL_TIME,
                                "",
                                values,
                                Map.of(),
                                10,
                                HologramReadsTheCacheTest::nameOf,
                                REFRESHED)),
                REFRESHED);
    }

    private LeaderboardHologram hologram() {
        return new LeaderboardHologram(Leaderboards.backedBy(cache), messages(), QUIET);
    }

    private TextDisplay place() {
        hologram().place(settings(), NOW);
        return latestDisplay();
    }

    private TextDisplay latestDisplay() {
        return server.getWorlds().get(0).getEntities().stream()
                .filter(LeaderboardHologram::isHologram)
                .map(TextDisplay.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Anzeige gesetzt"));
    }

    private static String textOf(TextDisplay display) {
        return PlainTextComponentSerializer.plainText().serialize(display.text());
    }

    private static String nameOf(UUID account) {
        if (account.equals(FIRST)) {
            return "Alpha";
        }
        return account.equals(SECOND) ? "Beta" : "Somebody";
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

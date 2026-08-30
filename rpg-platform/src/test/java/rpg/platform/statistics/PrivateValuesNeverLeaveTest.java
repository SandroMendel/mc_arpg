package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import net.kyori.adventure.text.Component;
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
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonScore;
import rpg.core.statistics.SeasonScoreBoard;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * SC-006, SC-017 — <b>die Sichtbarkeitsprüfung noch einmal, über den fertigen Block.</b>
 *
 * <p>Es gibt schon Tests je Ausgabeweg. Dieser hier ist trotzdem nicht doppelt: er geht
 * <em>alle</em> Wege in <em>einem</em> Durchlauf ab — jedes Fenster in jeder Rangliste in jedem
 * Zeitraum, das fremde Profil, die Saisonwertung und das Hologramm im Hub. Ein Einzeltest sagt „dieser Weg ist dicht"; dieser sagt „es gibt keinen anderen".
 *
 * <p><b>Warum das die Anforderung mit der größten Chance ist, still verloren zu gehen:</b> die
 * drei privaten Werte schützen nichts Dramatisches. Woran jemand stirbt, wie lange er verbunden
 * ist, wo er sich aufhält — ihr Bruch sähe aus wie eine nützliche Zusatzinformation, und niemand
 * meldet eine Zusatzinformation als Fehler.
 *
 * <p>Deshalb sind die privaten Werte hier mit <b>Markierungen</b> gefüllt, die sonst nirgends
 * vorkommen. Taucht eine davon irgendwo auf, ist der Weg benannt, über den sie herauskam.
 */
class PrivateValuesNeverLeaveTest {

    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    /** Die drei Markierungen — sie stehen für die drei privaten Werte (ADR-043). */
    private static final String CAUSE = "cause-marker-xyzzy";

    private static final String ZONE = "zone-marker-xyzzy";

    /** Die Onlinezeit hat keinen Namen, nur eine Zahl — also ist die Zahl die Markierung. */
    private static final long ONLINE_SECONDS = 918_273L;

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final Logger QUIET = Logger.getLogger(PrivateValuesNeverLeaveTest.class.getName());

    private ServerMock server;
    private Messages messages;
    private LeaderboardCache cache;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        messages = messages();
        cache = filledCache();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("SC-006 - keine der drei Markierungen erreicht IRGENDEINEN Ausgabeweg")
    void noneOfTheThreeMarkersReachesAnyOutputPath() {
        String everything = everythingAViewerCanSee();

        assertThat(everything).as("die Todesursache").doesNotContain(CAUSE);
        assertThat(everything).as("die Zone").doesNotContain(ZONE);
        assertThat(everything).as("die Onlinezeit").doesNotContain(String.valueOf(ONLINE_SECONDS));
    }

    @Test
    @DisplayName("und das eigene Profil zeigt alle drei - sonst waere der Test oben wertlos")
    void andtheOwnProfileShowsAllThree() {
        // Ohne diesen Gegenbeweis pruefte der Test oben nur, dass die Werte nirgends stehen -
        // was auch dann zutraefe, wenn sie gar nicht erst erhoben wuerden.
        String own = everyText(new StatisticsMenu(messages).build(ownProfile(), "Owner"));

        assertThat(own).contains(CAUSE).contains(ZONE).contains(String.valueOf(ONLINE_SECONDS));
    }

    @Test
    @DisplayName("FR-036 - keine private Rangliste erscheint irgendwo namentlich")
    void noPrivateBoardAppearsByNameAnywhere() {
        List<String> hidden =
                java.util.Arrays.stream(Aggregation.values())
                        .filter(board -> board.visibility() != MetricVisibility.PUBLIC)
                        .map(Aggregation::key)
                        .toList();

        // Nicht einmal der NAME einer privaten Rangliste gehoert in eine Ansicht: er verriete
        // schon, dass es sie gibt, und die naechste Frage waere, warum man sie nicht sehen darf.
        // Die Vervollstaendigung von /top filtert sie zusaetzlich weg; das liegt in rpg-plugin
        // und wird dort geprueft - hier steht die Ansichtsseite.
        assertThat(hidden).isNotEmpty();

        // Das Fenster WEIST eine private Rangliste ab, statt sie leer zu zeigen. Ein leeres
        // Fenster haette den Programmierfehler zugedeckt - und in seiner Kopfzeile trotzdem den
        // Namen getragen, den es zu verbergen gilt. Genau das war hier zuerst der Fall.
        LeaderboardMenu menu = new LeaderboardMenu(Leaderboards.backedBy(cache), messages);
        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() == MetricVisibility.PUBLIC) {
                continue;
            }
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(() -> menu.build(board, Period.ALL_TIME, VIEWER, AT))
                    .withMessageContaining("FR-036");
        }

        for (String privateBoard : hidden) {
            assertThat(everythingAViewerCanSee())
                    .as("die private Rangliste " + privateBoard)
                    .doesNotContain(privateBoard);
        }
    }

    @Test
    @DisplayName("FR-037 - ein fremdes Profil kann die drei Werte gar nicht erst TRAGEN")
    void aforeignProfileCannotEvenCarryTheThreeValues() {
        // Der Riegel sitzt im Bauer, nicht im Fenster. Ein Filter beim Anzeigen waere ein Riegel
        // je Ansicht - und die naechste Ansicht faengt wieder ohne an. Ein Wert, der nie in den
        // Schnappschuss kommt, kann dagegen von keinem Fenster gelesen werden, auch nicht von
        // einem, das es noch gar nicht gibt.
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> withPrivateValues().foreign(OWNER, Period.ALL_TIME))
                .withMessageContaining("FR-037");

        // Und derselbe Bauer macht daraus anstandslos ein EIGENES Profil.
        assertThat(withPrivateValues().own(OWNER, Period.ALL_TIME)).isNotNull();
    }

    @Test
    @DisplayName("und keine private Rangliste liegt ueberhaupt im Speicherstand")
    void andnoPrivateBoardIsEvenInTheCache() {
        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() == MetricVisibility.PUBLIC) {
                continue;
            }
            for (Period period : Period.values()) {
                assertThat(Leaderboards.backedBy(cache).board(board, period))
                        .as(board.key() + " / " + period + " darf es gar nicht geben")
                        .isEmpty();
            }
        }
    }

    // ------------------------------------------------------------------ Gerüst

    /**
     * Jeder Text, den ein <b>anderer</b> Spieler in diesem Block zu sehen bekommt.
     *
     * <p>Alles in einem String: dann ist eine einzige {@code doesNotContain}-Zusicherung die
     * vollständige Aussage, und ein neuer Ausgabeweg muss hier eingetragen werden, statt
     * unbemerkt danebenzuliegen.
     */
    private String everythingAViewerCanSee() {
        StringBuilder seen = new StringBuilder();
        LeaderboardMenu menu = new LeaderboardMenu(Leaderboards.backedBy(cache), messages);

        // Weg 1: jede oeffentliche Rangliste in jedem Zeitraum. Die privaten fehlen hier nicht
        // aus Bequemlichkeit - das Fenster WEIST sie ab, und der Test dazu steht oben.
        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() != MetricVisibility.PUBLIC) {
                continue;
            }
            for (Period period : Period.values()) {
                seen.append(everyText(menu.build(board, period, VIEWER, AT)));
            }
        }

        // Weg 2: die Saisonwertung samt Aufschluesselung.
        seen.append(everyText(menu.buildSeasonScore(VIEWER, AT)));

        // Weg 3: das FREMDE Profil.
        seen.append(everyText(new StatisticsMenu(messages).build(foreignProfile(), "Owner")));

        // Weg 4: das Hologramm im Hub.
        seen.append(hologramText());

        return seen.toString();
    }

    private String hologramText() {
        LeaderboardHologram hologram =
                new LeaderboardHologram(Leaderboards.backedBy(cache), messages, QUIET);
        hologram.place(
                new StatisticsConfig.Hologram(
                        "world", 0.5, 65.0, 0.5, Aggregation.MOB_KILLS, Period.ALL_TIME, 10),
                AT);

        return server.getWorlds().get(0).getEntities().stream()
                .filter(LeaderboardHologram::isHologram)
                .map(TextDisplay.class::cast)
                .findFirst()
                .map(display -> plain(display.text()))
                .orElseThrow(() -> new AssertionError("keine Anzeige gesetzt"));
    }

    /** Ein Speicherstand, in dem jede <b>öffentliche</b> Rangliste steht — und sonst keine. */
    private LeaderboardCache filledCache() {
        LeaderboardCache filled = LeaderboardCache.empty();
        Map<LeaderboardCache.Key, Leaderboard> boards = new HashMap<>();

        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() != MetricVisibility.PUBLIC) {
                // Genau das tut LeaderboardFill auch: eine private Rangliste wird gar nicht erst
                // gebildet. Ein Speicherstand, der sie enthielte, waere ein Fenster von ihr
                // entfernt.
                continue;
            }
            for (Period period : Period.values()) {
                if (!period.fits(board.source().kind())) {
                    continue;
                }
                boards.put(
                        LeaderboardCache.Key.of(board, period),
                        Leaderboard.of(
                                board,
                                period,
                                "",
                                Map.of(OWNER, 42L),
                                Map.of(),
                                10,
                                account -> "Owner",
                                AT));
            }
        }
        filled.replace(boards, AT);
        filled.replaceSeasonScore(
                SeasonScoreBoard.of(
                        "2026-q3",
                        Map.of(
                                OWNER,
                                SeasonScore.of(
                                        Map.of(
                                                Aggregation.MOB_KILLS, 42L,
                                                // Die Spielzeit traegt zur Wertung bei - ihre
                                                // ZONENAUFTEILUNG aber nie.
                                                Aggregation.PLAYTIME_ACTIVE, 7_200L),
                                        weights())),
                        10,
                        account -> "Owner",
                        AT));
        return filled;
    }

    private static ScoreWeights weights() {
        return ScoreWeights.from(
                new StatisticsConfig.Score(
                        Map.of(Aggregation.MOB_KILLS, 2.0, Aggregation.PLAYTIME_ACTIVE, 1.0)));
    }

    private static ProfileSnapshot ownProfile() {
        return withPrivateValues().own(OWNER, Period.ALL_TIME);
    }

    /**
     * Ein fremdes Profil <b>ohne</b> die privaten Werte — anders geht es gar nicht.
     *
     * <p>Der Bauer weist es ab, wenn sie gesetzt sind. Das ist die stärkere Bauart als ein Filter
     * beim Anzeigen: ein Wert, der nie in den Schnappschuss kommt, kann von keinem Fenster
     * versehentlich gelesen werden — auch nicht von einem, das es noch gar nicht gibt.
     */
    private static ProfileSnapshot foreignProfile() {
        return publicValues().foreign(OWNER, Period.ALL_TIME);
    }

    private static ProfileSnapshot.Builder publicValues() {
        return new ProfileSnapshot.Builder()
                .value(Aggregation.MOB_KILLS, 42L)
                .value(Aggregation.DEATHS, 7L)
                .value(Aggregation.PLAYTIME_ACTIVE, 7_200L);
    }

    private static ProfileSnapshot.Builder withPrivateValues() {
        return publicValues()
                .deathsByCause(Map.of(CAUSE, 7L))
                .playtimeByZone(Map.of(ZONE, 7_200L))
                .onlineSeconds(ONLINE_SECONDS);
    }

    private static String everyText(Inventory window) {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : window.getContents()) {
            if (stack == null || stack.getItemMeta() == null) {
                continue;
            }
            if (stack.getItemMeta().displayName() != null) {
                text.append(plain(stack.getItemMeta().displayName())).append('\n');
            }
            List<Component> lore = stack.getItemMeta().lore();
            if (lore != null) {
                for (Component line : lore) {
                    text.append(plain(line)).append('\n');
                }
            }
        }
        return text.toString();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.PROFILE_TITLE.value(), "Your Record");
        texts.put(StatisticsMessageKeys.PROFILE_TITLE_OTHER.value(), "{player}");
        texts.put(StatisticsMessageKeys.PROFILE_LINE.value(), "{label}: {value}");
        texts.put(StatisticsMessageKeys.PROFILE_PRIVATE_OMITTED.value(), "Only for the player.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_TITLE.value(), "{board} - {period}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_ENTRY.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_ENTRY_SELF.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_YOUR_RANK.value(), "#{rank} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_UNRANKED.value(), "unranked");
        texts.put(StatisticsMessageKeys.LEADERBOARD_EMPTY.value(), "nobody yet");
        texts.put(StatisticsMessageKeys.LEADERBOARD_NOT_READY.value(), "still being built");
        texts.put(StatisticsMessageKeys.LEADERBOARD_AS_OF.value(), "Updated {age} ago.");
        texts.put(StatisticsMessageKeys.SEASON_SCORE_TITLE.value(), "Season Score - {season}");
        texts.put(
                StatisticsMessageKeys.SEASON_SCORE_LINE.value(),
                "{label}: {value} x {weight} = {points}");
        texts.put(StatisticsMessageKeys.SEASON_SCORE_TOTAL.value(), "Total: {points} (#{rank})");
        texts.put(StatisticsMessageKeys.SEASON_NONE.value(), "no season");
        texts.put(StatisticsMessageKeys.HOLOGRAM_HEADER.value(), "{board} - {period}");
        texts.put(StatisticsMessageKeys.HOLOGRAM_LINE.value(), "#{rank} {player} - {value}");
        for (Aggregation board : Aggregation.values()) {
            texts.put(StatisticsMessageKeys.boardName(board).value(), board.key());
            texts.put(StatisticsMessageKeys.boardHint(board).value(), "hint");
        }
        for (Period period : Period.values()) {
            texts.put(StatisticsMessageKeys.periodName(period).value(), period.name());
        }
        return new MapMessages(texts);
    }

}

package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.progression.ProgressView;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;
import rpg.core.ui.UiMessageKeys;

/**
 * Die drei Flächen gegen echte Paper-Typen (T040–T046).
 *
 * <p>Alles davor belegt, dass das <em>Richtige</em> gesendet wird; hier geht es darum, dass es
 * überhaupt auf einer Vanilla-Fläche landet — und dass es <b>eine</b> bleibt.
 */
class PaperSurfacesTest {

    private static final Logger QUIET = Logger.getLogger("paper-surfaces-test");

    private ServerMock server;
    private PlayerMock player;
    private PaperBossBar bossBar;
    private PaperSidebar sidebar;
    private PaperHudRenderer renderer;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer();
        Messages messages = messages();
        bossBar = new PaperBossBar(server, messages);
        sidebar = new PaperSidebar(server, messages);
        renderer =
                new PaperHudRenderer(
                        server, new DirectScheduler(), messages, bossBar, sidebar, QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- T040/T041: die Naht --------------------------------------------------

    @Test
    @DisplayName("T040: show auf ACTION_BAR trifft die Actionbar des Spielers")
    void showOnTheActionBarReachesTheActionBar() {
        renderer.show(
                player.getUniqueId(),
                HudSurface.ACTION_BAR,
                UiMessageKeys.SIDEBAR_LEVEL,
                Map.of("level", "7"));

        assertThat(plain(player.nextActionBar())).contains("7");
    }

    @Test
    @DisplayName("T041: ein Spieler, der gerade gegangen ist, ist kein Fehler")
    void adepartedPlayerIsNotAnError() {
        // Zusage 1 des Vertrags: diese Methoden liegen in Pfaden, die jedes Ereignis beruehren.
        UUID gone = UUID.randomUUID();

        assertThatCode(
                        () ->
                                renderer.show(
                                        gone,
                                        HudSurface.ACTION_BAR,
                                        UiMessageKeys.SIDEBAR_LEVEL,
                                        Map.of("level", "1")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("T041: ein Fehler beim Senden kostet keinen Tick")
    void afailureWhileSendingCostsNoTick() {
        // Ein Schluessel, dessen Platzhalter fehlen, ist der billigste Weg zu einem Fehler im
        // Rendern - und er darf den Aufrufer nicht mitreissen (Constitution VI).
        assertThatCode(
                        () ->
                                renderer.show(
                                        player.getUniqueId(),
                                        HudSurface.ACTION_BAR,
                                        UiMessageKeys.SHEET_TITLE,
                                        Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("showLines auf einer anderen Flaeche als der Sidebar tut nichts und sagt es")
    void showLinesElsewhereIsRefused() {
        // Still das Falsche zu tun waere schlechter als es zu sagen.
        assertThatCode(
                        () ->
                                renderer.showLines(
                                        player.getUniqueId(),
                                        HudSurface.ACTION_BAR,
                                        UiMessageKeys.SIDEBAR_TITLE,
                                        List.of()))
                .doesNotThrowAnyException();
        assertThat(player.nextActionBar()).isNull();
    }

    // --- T043/T044/T044a/T044b: die Bossbar -----------------------------------

    @Test
    @DisplayName("T043: je Spieler entsteht EINE Bossbar und sie wird wiederverwendet")
    void oneBossBarPerPlayerReused() {
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.ZONE_NAME,
                UiMessageKeys.BOSSBAR_ZONE_NOTICE,
                Map.of("zone", "Ashen Reach"),
                1.0);
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.BOSS_FIGHT,
                UiMessageKeys.BOSSBAR_BOSS_FIGHT,
                Map.of("name", "Warden"),
                0.5);
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.CHANNELLING,
                UiMessageKeys.BOSSBAR_CHANNELLING,
                Map.of("ability", "Rise"),
                0.25);

        // Drei Anlaesse, eine Leiste. Gestapelt waeren es drei uebereinander, von denen zwei
        // veraltet sind (FR-004b).
        assertThat(bossBar.tracked()).isEqualTo(1);
    }

    @Test
    @DisplayName("T043: clearBar entfernt die Leiste, bar(..., 0.0) laesst sie stehen")
    void clearBarRemovesItButAnEmptyBarStays() {
        // Zusage 3 des Vertrags: zwei verschiedene Dinge brauchen zwei Methoden, sonst muss der
        // Aufrufer eine Konvention kennen.
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.BOSS_FIGHT,
                UiMessageKeys.BOSSBAR_BOSS_FIGHT,
                Map.of("name", "Warden"),
                0.0);

        assertThat(bossBar.tracked()).as("ein leerer Balken steht noch da").isEqualTo(1);

        bossBar.clear(player.getUniqueId());

        assertThat(bossBar.tracked()).as("eine geraeumte Leiste ist weg").isZero();
    }

    @Test
    @DisplayName("T044a: beim Abmelden wird die Leiste weggeraeumt - samt Eintrag")
    void quittingClearsTheBarAndItsEntry() {
        // FR-004c. Beides, nicht nur das erste: eine entfernte Leiste, deren Eintrag stehen bleibt,
        // ist ein Leck, das erst nach Stunden auffaellt.
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.ZONE_NAME,
                UiMessageKeys.BOSSBAR_ZONE_NOTICE,
                Map.of("zone", "Ashen Reach"),
                1.0);

        bossBar.forget(player.getUniqueId());

        assertThat(bossBar.tracked()).isZero();
    }

    @Test
    @DisplayName("T044a: beim Wiederanmelden steht keine alte Leiste")
    void nostaleBarAfterRejoining() {
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.BOSS_FIGHT,
                UiMessageKeys.BOSSBAR_BOSS_FIGHT,
                Map.of("name", "Warden"),
                0.7);
        bossBar.forget(player.getUniqueId());

        // Nach dem Wiederanmelden: nichts steht, bis wieder etwas anliegt.
        assertThat(bossBar.tracked()).isZero();
    }

    @Test
    @DisplayName("ein Fuellstand ausserhalb von [0,1] wird begrenzt statt zu werfen")
    void anOutOfRangeFractionIsClamped() {
        // Ein Balken ueber eins ist eine Ausnahme in Adventure - also ein Fehler in einem Pfad, der
        // jedes Ereignis beruehrt.
        assertThatCode(
                        () ->
                                bossBar.show(
                                        player.getUniqueId(),
                                        BossBarOccasion.BOSS_FIGHT,
                                        UiMessageKeys.BOSSBAR_BOSS_FIGHT,
                                        Map.of("name", "Warden"),
                                        1.5))
                .doesNotThrowAnyException();
    }

    // --- T045/T046: die Sidebar -----------------------------------------------

    @Test
    @DisplayName("T045: die vier Zeilen stehen in der Reihenfolge aus SidebarLines")
    void thefourLinesAreInOrder() {
        List<SidebarLines.Line> lines =
                SidebarLines.of(new ProgressView(7, 40, 100, false), 250, Optional.of("Ashen Reach"));

        sidebar.show(player.getUniqueId(), UiMessageKeys.SIDEBAR_TITLE, lines);

        assertThat(sidebar.tracked()).isEqualTo(1);
    }

    @Test
    @DisplayName("T045: die Sidebar ueberlebt einen zweiten Aufruf ohne zu stapeln")
    void asecondCallDoesNotStack() {
        List<SidebarLines.Line> lines =
                SidebarLines.of(new ProgressView(7, 40, 100, false), 250, Optional.empty());

        sidebar.show(player.getUniqueId(), UiMessageKeys.SIDEBAR_TITLE, lines);
        sidebar.show(player.getUniqueId(), UiMessageKeys.SIDEBAR_TITLE, lines);

        assertThat(sidebar.tracked()).isEqualTo(1);
    }

    @Test
    @DisplayName("T046: clear nimmt die Sidebar weg")
    void clearRemovesTheSidebar() {
        sidebar.show(
                player.getUniqueId(),
                UiMessageKeys.SIDEBAR_TITLE,
                SidebarLines.of(new ProgressView(1, 0, 10, false), 0, Optional.empty()));

        sidebar.clear(player.getUniqueId());

        assertThat(sidebar.tracked()).isZero();
    }

    @Test
    @DisplayName("T046: beim Abmelden bleibt kein Board zurueck")
    void quittingLeavesNoBoard() {
        sidebar.show(
                player.getUniqueId(),
                UiMessageKeys.SIDEBAR_TITLE,
                SidebarLines.of(new ProgressView(1, 0, 10, false), 0, Optional.empty()));

        sidebar.forget(player.getUniqueId());

        assertThat(sidebar.tracked()).isZero();
    }

    @Test
    @DisplayName("forget auf dem Renderer raeumt beide Flaechen")
    void forgetClearsBothSurfaces() {
        bossBar.show(
                player.getUniqueId(),
                BossBarOccasion.ZONE_NAME,
                UiMessageKeys.BOSSBAR_ZONE_NOTICE,
                Map.of("zone", "Ashen Reach"),
                1.0);
        sidebar.show(
                player.getUniqueId(),
                UiMessageKeys.SIDEBAR_TITLE,
                SidebarLines.of(new ProgressView(1, 0, 10, false), 0, Optional.empty()));

        renderer.forget(player.getUniqueId());

        assertThat(bossBar.tracked()).isZero();
        assertThat(sidebar.tracked()).isZero();
    }

    // --- Aufbau ---------------------------------------------------------------

    private static String plain(net.kyori.adventure.text.Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Messages messages() {
        return MapMessages.fromNested(
                Map.of(
                        "ui",
                        Map.of(
                                "sidebar",
                                        Map.of(
                                                "title", "&6Vuntex",
                                                "level", "&7Level: &f{level}",
                                                "xp", "&7XP: &f{xp}/{xpNext}",
                                                "xp-max", "&7XP: &f{xp} (max)",
                                                "coins", "&7Coins: &e{coins}",
                                                "zone", "&7Zone: &f{zone}",
                                                "zone-wilderness", "&8Wilderness"),
                                "bossbar",
                                        Map.of(
                                                "zone-notice", "&f{zone}",
                                                "boss-fight", "&c{name}",
                                                "channelling", "&b{ability}"),
                                "sheet", Map.of("title", "&6{character}"))));
    }

    /** Führt alles sofort aus — der Sprung in den Tick ist hier nicht das Prüfobjekt. */
    private static final class DirectScheduler implements Scheduler {

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            task.run();
            return handle();
        }

        private static TaskHandle handle() {
            return new TaskHandle() {

                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}

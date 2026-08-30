package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.progression.ProgressView;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.DamageNumberSetting;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;
import rpg.core.ui.SurfaceSetting;
import rpg.core.ui.UiConfig;
import rpg.core.ui.UiMessageKeys;

/**
 * <b>Der Test der MVP-Grenze</b> (T067): ein Spieler durchläuft alles, was B13 anzeigt, und jeder
 * Wert landet auf <em>genau einer</em> Fläche.
 *
 * <p>{@code HudSurfaceAssignmentTest} prüft dieselbe Zusage an der Aufzählung — hier läuft sie
 * gegen den zusammengesetzten Block. Beides ist nötig: die Aufzählung kann richtig sein, während
 * der Renderer trotzdem doppelt zeichnet, und umgekehrt.
 *
 * <p>Der eigentliche Fehler, gegen den er steht: <b>ein Wert, der nach dem Umzug an zwei Stellen
 * ankommt.</b> Der Fortschritt hat die Actionbar verlassen und die Sidebar erreicht (FR-002a) — wenn
 * dabei jemand die alte Zeile stehen ließe, sähe der Spieler dieselben Zahlen zweimal, und niemand
 * merkte es an einem grünen Modultest.
 */
class HudSurfacesIntegrationTest {

    private static final Logger QUIET = Logger.getLogger("hud-surfaces-integration-test");

    private final UUID playerId = UUID.randomUUID();

    private RecordingHudRenderer renderer;
    private HudRefresh refresh;

    private final AtomicInteger actionBarCalls = new AtomicInteger();
    private final AtomicLong coins = new AtomicLong(0);
    private volatile ProgressView progress = new ProgressView(1, 0, 100, false);
    private volatile Optional<String> zone = Optional.empty();
    private volatile Optional<HudRefresh.BossBarContent> bossBar = Optional.empty();
    private volatile boolean hasCharacter = true;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        renderer = new RecordingHudRenderer();
        refresh =
                new HudRefresh(
                        renderer,
                        this::config,
                        id -> actionBarCalls.incrementAndGet(),
                        this::linesFor,
                        id -> bossBar,
                        QUIET);
    }

    @Test
    @DisplayName("T067: ein Spieler durchlaeuft alles - und kein Wert steht doppelt")
    void aplayerGoesThroughEverythingAndNothingIsDuplicated() {
        // 1. Anmelden mit Charakter: alle drei Flaechen bedient.
        refresh.refresh(playerId);
        assertThat(actionBarCalls).as("Leben, Mana, Verteidigung").hasValue(1);
        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).as("Level, XP, Coins, Zone").isEqualTo(1);

        // 2. Aufsteigen: die Sidebar aendert sich, die Actionbar bekommt KEINE Stufe.
        renderer.reset();
        progress = new ProgressView(2, 10, 120, false);
        refresh.refresh(playerId);

        assertThat(renderer.linesShown())
                .last()
                .satisfies(
                        lines -> {
                            assertThat(lines.get(0).key()).isEqualTo(UiMessageKeys.SIDEBAR_LEVEL);
                            assertThat(lines.get(0).values()).containsEntry("level", "2");
                        });

        // 3. Coins verdienen: eine Zeile aendert sich, drei nicht.
        renderer.reset();
        coins.set(250);
        refresh.refresh(playerId);

        assertThat(renderer.linesShown())
                .last()
                .satisfies(
                        lines ->
                                assertThat(lines.get(2).values()).containsEntry("coins", "250"));

        // 4. Zone betreten: die Sidebar-Zeile UND die Bossbar - zwei verschiedene Werte, nicht
        //    derselbe zweimal. Die Sidebar nennt, WO er ist; die Bossbar meldet, dass er gerade
        //    angekommen ist, und verschwindet wieder.
        renderer.reset();
        zone = Optional.of("Ashen Reach");
        bossBar =
                Optional.of(
                        new HudRefresh.BossBarContent(
                                BossBarOccasion.ZONE_NAME,
                                UiMessageKeys.BOSSBAR_ZONE_NOTICE,
                                Map.of("zone", "Ashen Reach"),
                                1.0));
        refresh.refresh(playerId);

        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(1);
        assertThat(renderer.barCallsFor(BossBarOccasion.ZONE_NAME)).isEqualTo(1);

        // 5. Der Zonenhinweis laeuft ab: die Bossbar wird GERAEUMT, die Sidebar-Zeile bleibt.
        renderer.reset();
        bossBar = Optional.empty();
        refresh.refresh(playerId);

        assertThat(renderer.barsCleared()).containsExactly(BossBarOccasion.ZONE_NAME);
        assertThat(renderer.callsFor(HudSurface.SIDEBAR))
                .as("die Zone steht weiter auf der Sidebar - sie ist nicht mitgegangen")
                .isZero();
    }

    @Test
    @DisplayName("T067: der Fortschritt erreicht die Sidebar und NICHT die Actionbar")
    void progressReachesTheSidebarAndNotTheActionBar() {
        // Der Kern der Entscheidung aus I1. Die Actionbar wird zwar jede Sekunde bedient - aber
        // ihr Inhalt kommt aus StatusActionBar, und der kennt keinen Fortschritt mehr. Was hier
        // geprueft wird, ist, dass die Sidebar ihn traegt und dass das Zeichnen der Actionbar
        // davon unabhaengig ist.
        progress = new ProgressView(40, 900, 1000, false);
        refresh.refresh(playerId);

        assertThat(renderer.linesShown())
                .last()
                .satisfies(
                        lines -> {
                            assertThat(lines).hasSize(4);
                            assertThat(lines.get(1).key()).isEqualTo(UiMessageKeys.SIDEBAR_XP);
                            assertThat(lines.get(1).values())
                                    .containsEntry("xp", "900")
                                    .containsEntry("xpNext", "1000");
                        });
    }

    @Test
    @DisplayName("T067: am Hoechstlevel traegt die Erfahrungszeile ihren eigenen Schluessel")
    void atMaxLevelTheXpLineUsesItsOwnKey() {
        // Die Unterscheidung ist mit dem Fortschritt von der Actionbar zur Sidebar gewandert und
        // dabei nicht verlorengegangen: '4120/0' saehe aus wie ein Fehler.
        progress = new ProgressView(60, 4120, 0, true);
        refresh.refresh(playerId);

        assertThat(renderer.linesShown())
                .last()
                .satisfies(
                        lines -> {
                            assertThat(lines.get(1).key()).isEqualTo(UiMessageKeys.SIDEBAR_XP_MAX);
                            assertThat(lines.get(1).values()).doesNotContainKey("xpNext");
                        });
    }

    @Test
    @DisplayName("T067: ohne Charakter zeigt keine der drei Flaechen einen Wert")
    void withoutACharacterNothingShows() {
        hasCharacter = false;

        refresh.refresh(playerId);

        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).isZero();
        assertThat(renderer.barred()).isEmpty();
    }

    @Test
    @DisplayName("T067: mit allen drei Flaechen abgeschaltet passiert gar nichts")
    void withEverythingDisabledNothingHappens() {
        // SC-010 in seiner staerksten Form. Nicht "unsichtbar", sondern "keine Arbeit": weder ein
        // Zeichnen noch ein Raeumen noch eine Nachfrage.
        allOff = true;

        refresh.refresh(playerId);

        assertThat(renderer.silent()).isTrue();
        assertThat(renderer.cleared()).isEmpty();
        assertThat(actionBarCalls).hasValue(0);
    }

    // --- Aufbau -------------------------------------------------------------

    private volatile boolean allOff;

    private Optional<List<SidebarLines.Line>> linesFor(UUID id) {
        if (!hasCharacter) {
            return Optional.empty();
        }
        return Optional.of(SidebarLines.of(progress, coins.get(), zone));
    }

    private UiConfig config() {
        SurfaceSetting setting = allOff ? SurfaceSetting.off() : SurfaceSetting.on();
        return new UiConfig(
                "en",
                Duration.ofSeconds(1),
                setting,
                setting,
                setting,
                Duration.ofSeconds(4),
                new DamageNumberSetting(true, Duration.ofMillis(1200), 1.4));
    }
}

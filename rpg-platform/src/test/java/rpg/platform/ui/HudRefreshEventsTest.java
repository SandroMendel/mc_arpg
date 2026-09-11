package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.progression.LevelUpEvent;
import rpg.core.progression.ProgressChangedEvent;
import rpg.core.progression.ProgressView;
import rpg.core.ui.DamageNumberSetting;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;
import rpg.core.ui.SurfaceSetting;
import rpg.core.ui.UiConfig;
import rpg.core.zone.ZoneChangedEvent;

/**
 * Die Ereignispfade der Sidebar (T056a) — und die eine Zeile, die keinen hat (T056c).
 *
 * <h2>Warum der Takt hier stillsteht</h2>
 *
 * <p>FR-009 verlangt „bei jeder Wertänderung <b>unmittelbar</b>". Ein Test, der den Takt mitlaufen
 * ließe, wäre auch dann grün, wenn es <em>keinen einzigen</em> Ereignispfad gäbe — er misst dann den
 * Takt statt der Ereignisse. Der Takt wird deshalb gar nicht erst gestartet: was hier gezeichnet
 * wird, ist ausschließlich von einem Ereignis ausgelöst.
 */
class HudRefreshEventsTest {

    private static final Logger QUIET = Logger.getLogger("hud-refresh-events-test");

    private final UUID playerId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();

    private RecordingHudRenderer renderer;
    private HudRefresh refresh;
    private EventBus eventBus;
    private final AtomicLong coins = new AtomicLong(100);
    private volatile ProgressView progress = new ProgressView(12, 340, 1000, false);
    private volatile Optional<String> zone = Optional.empty();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        renderer = new RecordingHudRenderer();
        refresh =
                new HudRefresh(
                        renderer,
                        this::config,
                        id -> {},
                        this::linesFor,
                        id -> Optional.empty(),
                        QUIET);
        eventBus = new DefaultEventBus(QUIET);

        // Dieselbe Verdrahtung wie in RpgPlugin.wireUi - und dieselben drei Ereignisse. Die
        // Coin-Buchung fehlt hier genauso wie dort, und das ist der Punkt von T056c.
        eventBus.subscribe(ProgressChangedEvent.class, event -> refresh.refresh(event.playerId()));
        eventBus.subscribe(LevelUpEvent.class, event -> refresh.refresh(event.playerId()));
        eventBus.subscribe(
                ZoneChangedEvent.class,
                event -> {
                    if (event.characterId().equals(characterId)) {
                        refresh.refresh(playerId);
                    }
                });
    }

    @Test
    @DisplayName("T056a: ein Erfahrungsgewinn zeichnet die Sidebar sofort neu")
    void anExperienceGainRedrawsTheSidebarImmediately() {
        // Der Nachfolger des Tests, der bis B13 in StatusActionBarTest stand. Dort zeichnete das
        // Ereignis die Actionbar; seit FR-002a stehen Level und Erfahrung auf der Sidebar, und der
        // Aufstieg muss DORT ankommen.
        refresh.refresh(playerId);
        renderer.reset();

        progress = new ProgressView(12, 500, 1000, false);
        eventBus.publish(new ProgressChangedEvent(characterId, playerId, 160L, 12, 500L, 1000L));

        assertThat(renderer.callsFor(HudSurface.SIDEBAR))
                .as("ohne laufenden Takt - allein durch das Ereignis")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("T056a: ein Stufenaufstieg zeichnet die Sidebar sofort neu")
    void alevelUpRedrawsTheSidebarImmediately() {
        refresh.refresh(playerId);
        renderer.reset();

        progress = new ProgressView(13, 0, 1200, false);
        eventBus.publish(new LevelUpEvent(characterId, playerId, 12, 13, true));

        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(1);
        assertThat(renderer.linesShown())
                .last()
                .satisfies(
                        lines ->
                                assertThat(lines.get(0).values()).containsEntry("level", "13"));
    }

    @Test
    @DisplayName("T056a: ein Zonenwechsel zeichnet die Sidebar sofort neu")
    void azoneChangeRedrawsTheSidebarImmediately() {
        refresh.refresh(playerId);
        renderer.reset();

        zone = Optional.of("Ashen Reach");
        eventBus.publish(
                new ZoneChangedEvent(characterId, Optional.empty(), Optional.of("Ashen Reach")));

        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(1);
    }

    @Test
    @DisplayName("T056c: eine Coin-Buchung zeichnet NICHT sofort - sie hat kein Ereignis")
    void acoinBookingDoesNotRedrawImmediately() {
        // FR-009a, und die Zusage, die dieser Block ausdruecklich NICHT gibt.
        //
        // rpg.core.currency fuehrt keinen Ereignistyp - nur CoinLedger, LedgerEntry und
        // BookingResult. Es gibt also nichts, worauf man sich anmelden koennte. Der Test haelt die
        // Grenze fest, statt sie als Fehler zu hinterlassen, den spaeter jemand fuer einen Bug
        // haelt.
        //
        // Nachgeruestet wird nichts: ein Ereignis in B08b waere der Eingriff in einen fremden
        // Block, den FR-024, FR-070 und FR-071 an drei anderen Stellen ablehnen. Wenn die Sekunde
        // stoert, gehoert es nach B08b - mit eigenem Namen und eigener Aufgabe.
        refresh.refresh(playerId);
        renderer.reset();

        coins.set(999);

        assertThat(renderer.callsFor(HudSurface.SIDEBAR))
                .as("kein Ereignis, also kein Zeichnen")
                .isZero();
    }

    @Test
    @DisplayName("T056c: beim naechsten Takt steht der neue Coin-Stand dann da")
    void thenextPassPicksTheCoinsUp() {
        // Die andere Haelfte: die Zeile ist nicht kaputt, sie ist nur eine Sekunde langsamer.
        refresh.refresh(playerId);
        renderer.reset();

        coins.set(999);
        refresh.refresh(playerId);

        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).isEqualTo(1);
        assertThat(renderer.linesShown())
                .last()
                .satisfies(
                        lines ->
                                assertThat(lines.get(2).values()).containsEntry("coins", "999"));
    }

    @Test
    @DisplayName("ein Ereignis ohne Wertaenderung zeichnet nicht - FR-013 gilt auch hier")
    void aneventWithoutAChangeDrawsNothing() {
        // Der Ereignispfad benutzt DENSELBEN Eingang wie der Takt, also auch dieselbe
        // Aenderungserkennung. Ein zweiter Weg haette eine zweite - und die waere die, die niemand
        // pflegt.
        refresh.refresh(playerId);
        renderer.reset();

        eventBus.publish(new ProgressChangedEvent(characterId, playerId, 0L, 12, 340L, 1000L));

        assertThat(renderer.callsFor(HudSurface.SIDEBAR)).isZero();
    }

    // --- Aufbau -------------------------------------------------------------

    private Optional<List<SidebarLines.Line>> linesFor(UUID id) {
        return id.equals(playerId)
                ? Optional.of(SidebarLines.of(progress, coins.get(), zone))
                : Optional.empty();
    }

    private UiConfig config() {
        return new UiConfig(
                "en",
                Duration.ofSeconds(1),
                SurfaceSetting.on(),
                SurfaceSetting.on(),
                SurfaceSetting.on(),
                Duration.ofSeconds(4),
                new DamageNumberSetting(true, Duration.ofMillis(1200), 1.4));
    }
}

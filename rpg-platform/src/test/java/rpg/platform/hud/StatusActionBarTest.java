package rpg.platform.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.stats.ChangeCause;
import rpg.core.stats.ResourceChangedEvent;
import rpg.core.stats.ResourceKind;

/**
 * Die eigene Gesundheit, Mana und Verteidigung auf der Actionbar.
 *
 * <p>Zwei Auslöser: jede Änderung einer Ressource und jede Neuberechnung - und dazu eine stetige
 * Auffrischung. Die ist nicht vermeidbar: Minecraft blendet eine Actionbar nach etwa zwei Sekunden
 * aus, eine dauerhafte Anzeige heißt also erneut senden.
 */
class StatusActionBarTest {

    private static final Logger QUIET = Logger.getLogger("status-action-bar-test");

    private ServerMock server;
    private HudFixture.Statuses statuses;
    private HudFixture.RecordingScheduler scheduler;
    private StatusActionBar bar;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        statuses = new HudFixture.Statuses();
        scheduler = new HudFixture.RecordingScheduler();
        bar = new StatusActionBar(server, statuses, scheduler, HudFixture.messages(), QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Zeile nennt Leben, Maximum, Prozent, MANA und Verteidigung")
    void theLineCarriesEveryNumber() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player)).isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148");
    }

    @Test
    @DisplayName("ohne Mana faellt der Manateil weg - 0/0 saehe aus wie ein Fehler")
    void withoutManaThatPartIsOmitted() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .as("eine Zahl, die nichts sagt, nimmt dreien den Platz weg, die etwas sagen")
                .isEqualTo("620/2000 HP (31%) DEF 148");
    }

    @Test
    @DisplayName("wer einen Zaehler hat, liest ihn mit - beim Berserker die Wut")
    void aHolderWithAMeterReadsItToo() {
        PlayerMock player = server.addPlayer();
        statuses.giveWithMeter(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0, 47.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player)).isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148 RAGE 47");
    }

    @Test
    @DisplayName("wer keinen hat, bekommt auch keine Null - Magier und Rogue lesen drei Zahlen")
    void withoutAMeterThatPartIsOmitted() {
        // Kein Platzhalter, der leer bleibt, und keine Null, die nichts bedeutet: dieselbe
        // Entscheidung wie beim Mana eines Mobs. WELCHE Klasse einen Zaehler hat, steht dabei
        // nirgends im Code - sie hat einen, wenn eine ihrer Faehigkeiten einen METER-Effekt traegt.
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148")
                .doesNotContain("RAGE");
    }

    @Test
    @DisplayName("gerundet, nicht mit Nachkommastellen - die Bruchteile sind Rauschen")
    void valuesAreRounded() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 1234.7, 2000.0, 74.6, 300.0, 147.6);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .contains("1235/2000")
                .contains("75/300")
                .contains("DEF 148");
    }

    @Test
    @DisplayName("jede Gesundheitsänderung zeichnet neu, ohne dass jemand die Leiste ruft")
    void aHealthChangeRedrawsIt() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 500.0, 1000.0, 20.0);
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(healthChange(player.getUniqueId()));

        assertThat(actionBarOf(player)).contains("500/1000").contains("(50%)");
    }

    @Test
    @DisplayName("eine Manaänderung zeichnet die Zeile neu - Mana steht jetzt darauf")
    void aManaChangeRedrawsToo() {
        // UMGEKEHRT statt gelöscht. Vorher stand hier, dass eine Manaänderung NICHT neu zeichnet:
        // die Zeile nannte Mana nicht, also wäre das ein Paket ohne sichtbaren Unterschied gewesen.
        // Seit Mana auf der Zeile steht, ist das Gegenteil richtig - ohne diese Neuzeichnung hinkten
        // die Kosten eines Zaubers bis zu einer Sekunde nach, lang genug um wie ein verschluckter
        // Klick auszusehen.
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 500.0, 1000.0, 40.0, 200.0, 20.0);
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(
                new ResourceChangedEvent(
                        player.getUniqueId(),
                        UUID.randomUUID(),
                        ResourceKind.MANA,
                        10.0,
                        5.0,
                        50.0,
                        ChangeCause.DELTA));

        assertThat(scheduler.entityTasks).as("eingeplant und gezeichnet").isEqualTo(1);
    }

    @Test
    @DisplayName("ein Mob löst dieselben Ereignisse aus, bekommt aber keine Actionbar")
    void aMobGetsNoActionBar() {
        UUID mobId = UUID.randomUUID();
        statuses.give(mobId, 80.0, 80.0, 10.0);

        bar.show(mobId);

        assertThat(scheduler.entityTasks).as("die Aufgabe lief, fand aber keinen Spieler").isEqualTo(1);
    }

    @Test
    @DisplayName("ohne Werte passiert nichts - es wird nicht einmal eingeplant")
    void withoutAStatusNothingIsScheduled() {
        bar.show(UUID.randomUUID());

        assertThat(scheduler.entityTasks).isZero();
    }

    @Test
    @DisplayName("die Auffrischung zeichnet für jeden Spielenden, nicht nur im Kampf")
    void theRefreshDrawsForEveryone() {
        // Eine Actionbar blendet nach etwa zwei Sekunden aus. Eine dauerhafte Anzeige heißt deshalb:
        // erneut senden - für jeden, nicht nur für den, der gerade kämpft.
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        statuses.give(first.getUniqueId(), 100.0, 200.0, 5.0);
        statuses.give(second.getUniqueId(), 60.0, 200.0, 5.0);

        bar.startRefresh(() -> List.of(first.getUniqueId(), second.getUniqueId()));
        scheduler.runDelayedOnce();

        assertThat(actionBarOf(first)).contains("100/200");
        assertThat(actionBarOf(second)).contains("60/200");
    }

    @Test
    @DisplayName("die Auffrischung plant sich selbst neu, sonst läuft sie genau einmal")
    void theRefreshReschedulesItself() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 100.0, 200.0, 5.0);
        bar.startRefresh(() -> List.of(player.getUniqueId()));

        scheduler.runDelayedOnce();
        player.nextActionBar();
        scheduler.runDelayedOnce();

        assertThat(actionBarOf(player)).as("der zweite Durchgang zeichnet wieder").isNotNull();
    }

    // --- fixtures ---

    private static ResourceChangedEvent healthChange(UUID playerId) {
        return new ResourceChangedEvent(
                playerId,
                UUID.randomUUID(),
                ResourceKind.HEALTH,
                600.0,
                500.0,
                1000.0,
                ChangeCause.DELTA);
    }

    private static String actionBarOf(PlayerMock player) {
        var component = player.nextActionBar();
        return component == null
                ? null
                : PlainTextComponentSerializer.plainText()
                        .serialize(component)
                        .replaceAll("\\s+", " ")
                        .trim();
    }
}

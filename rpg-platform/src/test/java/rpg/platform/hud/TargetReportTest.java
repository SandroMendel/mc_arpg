package rpg.platform.hud;

import static org.assertj.core.api.Assertions.assertThat;

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
import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageType;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;

/**
 * Die Chatzeile über das, was der Spieler getroffen hat.
 *
 * <p>Sie hängt an {@code DamageDealtEvent}, das B05 über ein halbe-Sekunden-Fenster bündelt. Das ist
 * hier wichtiger, als es aussieht: ohne diese Bündelung würde eine schnelle Waffe mehrere Zeilen pro
 * Sekunde in den Chat schreiben, und die Anzeige wäre der Grund, sie abzuschalten.
 */
class TargetReportTest {

    private static final Logger QUIET = Logger.getLogger("target-report-test");

    private ServerMock server;
    private HudFixture.Statuses statuses;
    private HudFixture.RecordingScheduler scheduler;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        statuses = new HudFixture.Statuses();
        scheduler = new HudFixture.RecordingScheduler();
        eventBus = new DefaultEventBus(QUIET);
        new TargetReport(server, statuses, scheduler, HudFixture.messages(), QUIET)
                .subscribeTo(eventBus);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Zeile nennt Restleben, Prozent, Verteidigung und den Schaden des Fensters")
    void theLineCarriesTheTargetsNumbers() {
        PlayerMock attacker = server.addPlayer();
        UUID mobId = UUID.randomUUID();
        statuses.give(mobId, 45.0, 80.0, 10.0);

        eventBus.publish(dealt(attacker.getUniqueId(), mobId, 35.0, 3, false));

        assertThat(messageTo(attacker)).contains("45/80").contains("(56%)").contains("DEF 10")
                .contains("-35");
    }

    @Test
    @DisplayName("ein tödlicher Treffer meldet den Tod statt 0/0")
    void alethalHitReportsTheKill() {
        PlayerMock attacker = server.addPlayer();
        UUID mobId = UUID.randomUUID();
        statuses.give(mobId, 0.0, 80.0, 10.0);

        eventBus.publish(dealt(attacker.getUniqueId(), mobId, 60.0, 2, true));

        assertThat(messageTo(attacker)).contains("slain").contains("-60").doesNotContain("0/80");
    }

    @Test
    @DisplayName("Umgebungsschaden hat keinen Angreifer und wird niemandem gemeldet")
    void environmentDamageIsNotReported() {
        PlayerMock player = server.addPlayer();

        eventBus.publish(dealt(null, player.getUniqueId(), 12.0, 1, false));

        assertThat(scheduler.entityTasks).as("nicht einmal eingeplant").isZero();
    }

    @Test
    @DisplayName("wer sich selbst verletzt, bekommt keine Zielmeldung über sich")
    void selfDamageIsNotReported() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 50.0, 100.0, 5.0);

        eventBus.publish(dealt(player.getUniqueId(), player.getUniqueId(), 5.0, 1, false));

        assertThat(scheduler.entityTasks).isZero();
    }

    @Test
    @DisplayName("ein Ziel ohne Werte ergibt keine Zahlen statt erfundener Nullen")
    void aTargetOutsideTheSystemReportsNothing() {
        PlayerMock attacker = server.addPlayer();

        eventBus.publish(dealt(attacker.getUniqueId(), UUID.randomUUID(), 4.0, 1, false));

        assertThat(messageTo(attacker)).as("leere Zeile, keine Nullen").isEmpty();
    }

    // --- fixtures ---

    private static DamageDealtEvent dealt(
            UUID attackerId, UUID targetId, double total, int hits, boolean lethal) {
        return new DamageDealtEvent(attackerId, targetId, DamageType.PHYSICAL, total, hits, lethal);
    }

    private static String messageTo(PlayerMock player) {
        var component = player.nextComponentMessage();
        return component == null
                ? null
                : PlainTextComponentSerializer.plainText()
                        .serialize(component)
                        .replaceAll("\\s+", " ")
                        .trim();
    }
}

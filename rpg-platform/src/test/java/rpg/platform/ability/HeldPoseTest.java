package rpg.platform.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import rpg.core.ability.Ability;
import rpg.core.ability.EffectSpec;
import rpg.core.ability.EffectType;
import rpg.core.ability.TargetMode;
import rpg.core.ability.TargetSpec;
import rpg.core.classes.AbilityKind;
import rpg.core.combat.DamageType;
import rpg.core.message.MessageKey;
import rpg.platform.scheduler.ImmediateScheduler;

/**
 * Die Blockhaltung, die ohne gedrueckte Maustaste stehen bleibt.
 *
 * <p>Der Block lief schon vorher acht Sekunden - {@code sustained: true} stimmte, und die Milderung
 * wirkte. Was fehlte, war die Haltung: Vanilla haelt einen Schild nur, solange die rechte Maustaste
 * gedrueckt ist, und der Spieler laesst sie nach etwa hundert Millisekunden los. Uebrig blieb ein
 * Blinzeln.
 *
 * <p>MockBukkit kennt {@code startUsingItem} nicht und wirft dafuer. Das ist hier kein Hindernis und
 * auch keine Luecke: der Aufruf ist im Anzeiger gefangen und geloggt (Prinzip VI), und was diese
 * Tests pruefen, ist die Entscheidung davor - <em>wessen</em> Loslassen zurueckgenommen wird und
 * wessen nicht. Ob Paper die Pose dann setzt, beweist erst der echte Server.
 */
class HeldPoseTest {

    private static final Logger QUIET = Logger.getLogger("held-pose-test");

    private ServerMock server;
    private AbilityFeedback feedback;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        feedback = new AbilityFeedback(server, QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("wer den Block haelt, ist vermerkt - und nach dem Ende nicht mehr")
    void theRaisedGuardIsRemembered() {
        PlayerMock player = server.addPlayer();

        feedback.holdPose(player, shield());
        assertThat(feedback.shouldStayRaised(player.getUniqueId())).isTrue();

        feedback.releasePose(player, shield());
        assertThat(feedback.shouldStayRaised(player.getUniqueId()))
                .as("sonst hoebe das Ende die Haltung wieder, und sie endete nie")
                .isFalse();
    }

    @Test
    @DisplayName("eine Faehigkeit ohne Schild traegt keine Haltung - der Magieschild etwa")
    void anAbilityWithoutAShieldHoldsNothing() {
        // Die Unterscheidung steht in abilities.yml und nicht im Code: was eine Pose kann,
        // entscheidet das Material. Ein Amethystsplitter haelt niemand hoch.
        PlayerMock player = server.addPlayer();

        feedback.holdPose(player, amethyst());

        assertThat(feedback.shouldStayRaised(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("das Loslassen der Maustaste nimmt die Haltung nicht herunter")
    void releasingTheMouseDoesNotLowerTheGuard() {
        PlayerMock player = server.addPlayer();
        feedback.holdPose(player, shield());
        HeldPoseListener listener = new HeldPoseListener(feedback, new ImmediateScheduler());

        listener.onStopUsingItem(stopUsing(player));

        assertThat(feedback.shouldStayRaised(player.getUniqueId()))
                .as("genau das war der Wunsch: blocken, ohne die Taste gedrueckt zu halten")
                .isTrue();
    }

    @Test
    @DisplayName("wer keinen Block haelt, wird nicht festgehalten - ein gespannter Bogen etwa")
    void anyoneElseIsLeftAlone() {
        // Ein pauschales Festhalten waere ein Spieler, der seinen Bogen nicht mehr loesen kann.
        PlayerMock player = server.addPlayer();
        RecordingScheduler scheduler = new RecordingScheduler();
        HeldPoseListener listener = new HeldPoseListener(feedback, scheduler);

        listener.onStopUsingItem(stopUsing(player));

        assertThat(scheduler.scheduled)
                .as("nicht einmal eingeplant - wer nichts haelt, kostet keinen Gedanken")
                .isZero();
    }

    @Test
    @DisplayName("laeuft die Faehigkeit im Zwischentakt ab, bleibt die Haltung unten")
    void anAbilityThatEndsInBetweenIsNotRaisedAgain() {
        // Zwischen dem Loslassen und dem naechsten Takt koennen die acht Sekunden voll sein. Das
        // Heben faende dann keine Faehigkeit mehr, die es wieder herunternimmt.
        PlayerMock player = server.addPlayer();
        feedback.holdPose(player, shield());
        RecordingScheduler scheduler = new RecordingScheduler();
        new HeldPoseListener(feedback, scheduler).onStopUsingItem(stopUsing(player));

        feedback.releasePose(player, shield());
        scheduler.runPending();

        assertThat(feedback.shouldStayRaised(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("wer mitten im Block geht, hinterlaesst keinen Vermerk")
    void leavingMidGuardLeavesNothingBehind() {
        PlayerMock player = server.addPlayer();
        feedback.holdPose(player, shield());

        feedback.forget(player.getUniqueId());

        assertThat(feedback.shouldStayRaised(player.getUniqueId()))
                .as("sonst laege die Id bis zum Neustart des Servers herum")
                .isFalse();
    }

    // --- fixtures ---

    private static PlayerStopUsingItemEvent stopUsing(PlayerMock player) {
        return new PlayerStopUsingItemEvent(player, new ItemStack(Material.SHIELD), 2);
    }

    private static Ability shield() {
        return ability("warrior.shield", "SHIELD");
    }

    private static Ability amethyst() {
        return ability("mage.magic-shield", "AMETHYST_SHARD");
    }

    private static Ability ability(String id, String item) {
        return new Ability(
                id,
                AbilityKind.ACTIVE,
                MessageKey.of("ability." + id + ".name"),
                null,
                20.0,
                Duration.ofMillis(800L),
                Duration.ZERO,
                true,
                true,
                Duration.ofSeconds(8L),
                1,
                null,
                false,
                false,
                false,
                false,
                java.util.Set.of(),
                1.0,
                new TargetSpec(TargetMode.SELF, 0.0, null, 1, null, null, null),
                List.of(
                        new EffectSpec(
                                EffectType.SHIELD,
                                60.0,
                                20.0,
                                Duration.ofSeconds(8L),
                                null,
                                1,
                                null,
                                null,
                                DamageType.PHYSICAL,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                null)),
                5,
                Map.of(),
                List.of(item));
    }

    /** Haelt die verzoegerte Aufgabe fest, bis der Test sie laufen laesst. */
    private static final class RecordingScheduler implements rpg.core.scheduler.Scheduler {

        int scheduled;
        private final List<Runnable> pending = new java.util.ArrayList<>();

        void runPending() {
            List<Runnable> due = List.copyOf(pending);
            pending.clear();
            due.forEach(Runnable::run);
        }

        @Override
        public rpg.core.scheduler.TaskHandle runSyncAtLocation(
                rpg.core.scheduler.WorldPosition position, Runnable task) {
            throw new UnsupportedOperationException("no pose uses this");
        }

        @Override
        public rpg.core.scheduler.TaskHandle runSyncOnEntity(
                rpg.core.scheduler.EntityRef entity, Runnable task) {
            throw new UnsupportedOperationException("no pose uses this");
        }

        @Override
        public rpg.core.scheduler.TaskHandle runSyncOnEntityDelayed(
                rpg.core.scheduler.EntityRef entity, Duration delay, Runnable task) {
            scheduled++;
            pending.add(task);
            return handle();
        }

        @Override
        public rpg.core.scheduler.TaskHandle runAsync(Runnable task) {
            throw new UnsupportedOperationException("no pose uses this");
        }

        @Override
        public rpg.core.scheduler.TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("no pose uses this");
        }

        private static rpg.core.scheduler.TaskHandle handle() {
            return new rpg.core.scheduler.TaskHandle() {
                @Override
                public void cancel() {
                    // nothing to cancel
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}

package rpg.platform.ability;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * T060 - US2.6: der Doppelsprung des Mage, ohne eigenen Keybind (research.md R7).
 *
 * <p>Zwei Zusagen stehen hier: der zweite Sprung trägt, und ein <b>dritter vor Bodenkontakt</b> nicht.
 * Der zweite Teil ist keine geprüfte Regel, sondern eine Eigenschaft des Zustands - {@code allowFlight}
 * wird beim Auslösen genommen und erst beim Landen zurückgegeben.
 */
class DoubleJumpListenerTest {

    private ServerMock server;
    private PlayerMock player;
    private DoubleJumpListener listener;

    private boolean unlocked = true;
    private boolean slowFall = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("DoubleJumpProbe");
        server.addSimpleWorld("world");
        player = server.addPlayer();
        listener =
                new DoubleJumpListener(
                        who -> unlocked, who -> slowFall, () -> 0.8, () -> 60);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("US2.6: der zweite Sprung trägt und der Fall wird verlangsamt")
    void theSecondJumpCarriesAndSlowsTheFall() {
        player.setAllowFlight(true);

        listener.onToggleFlight(new PlayerToggleFlightEvent(player, true));

        assertThat(player.getVelocity().getY()).isEqualTo(0.8);
        assertThat(player.hasPotionEffect(PotionEffectType.SLOW_FALLING)).isTrue();
    }

    @Test
    @DisplayName("der Spieler fliegt dabei NICHT - genau dafür wird das Ereignis abgebrochen")
    void thePlayerDoesNotActuallyFly() {
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        listener.onToggleFlight(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(player.isFlying()).isFalse();
    }

    @Test
    @DisplayName("US2.6: ein dritter Sprung vor Bodenkontakt wird nicht ausgeführt")
    void noThirdJumpBeforeLanding() {
        player.setAllowFlight(true);
        listener.onToggleFlight(new PlayerToggleFlightEvent(player, true));

        assertThat(player.getAllowFlight())
                .as("ohne die Erlaubnis sendet der Client gar kein zweites Ereignis mehr")
                .isFalse();
    }

    @Test
    @DisplayName("die Einstellung 'nur Sprung' lässt den Slow Fall weg")
    void theJumpOnlySettingOmitsSlowFall() {
        slowFall = false;
        player.setAllowFlight(true);

        listener.onToggleFlight(new PlayerToggleFlightEvent(player, true));

        assertThat(player.getVelocity().getY()).isEqualTo(0.8);
        assertThat(player.hasPotionEffect(PotionEffectType.SLOW_FALLING))
                .as("ein normaler Sprung, nur höher")
                .isFalse();
    }

    @Test
    @DisplayName("ohne die Fähigkeit passiert nichts")
    void withoutTheAbilityNothingHappens() {
        unlocked = false;
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        listener.onToggleFlight(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(player.hasPotionEffect(PotionEffectType.SLOW_FALLING)).isFalse();
    }

    @Test
    @DisplayName("das Beenden des Fliegens löst nichts aus - nur der Beginn zählt")
    void togglingFlightOffDoesNothing() {
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, false);
        listener.onToggleFlight(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(player.hasPotionEffect(PotionEffectType.SLOW_FALLING)).isFalse();
    }
}

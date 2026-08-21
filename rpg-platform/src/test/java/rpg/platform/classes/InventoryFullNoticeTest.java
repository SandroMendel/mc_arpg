package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.ClassMessageKeys;
import rpg.core.classes.ClassNotice;
import rpg.core.message.MessageKey;

/** T125 - die Warnung bei vollem Inventar (US4.6, FR-030). */
class InventoryFullNoticeTest {

    private ServerMock server;
    private RecordingNotice notice;
    private TestClock clock;
    private InventoryFullNoticeListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        notice = new RecordingNotice();
        clock = new TestClock();
        listener = new InventoryFullNoticeListener(notice, clock);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("mit Platz im Inventar passiert nichts - das ist der Normalfall")
    void roomLeftMeansNoWarning() {
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown).isEmpty();
    }

    @Test
    @DisplayName("bei vollem Inventar erscheint die Warnung (US4.6)")
    void fullInventoryWarns() {
        fillInventory();

        listener.onAttemptPickup(pickup());

        assertThat(notice.shown).containsExactly(ClassMessageKeys.INVENTORY_FULL);
    }

    @Test
    @DisplayName("nichts wird verworfen und nichts aufgeräumt - der Spieler schafft Platz")
    void nothingIsDiscardedOrTidied() {
        fillInventory();
        PlayerAttemptPickupItemEvent event = pickup();

        listener.onAttemptPickup(event);

        assertThat(event.isCancelled())
                .as("das Aufsammeln wird nicht abgebrochen - das Item soll liegen bleiben, nicht weg")
                .isFalse();
        assertThat(player.getInventory().firstEmpty())
                .as("kein automatisches Aufräumen (ADR-018)")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("ein Haufen Beute erzeugt EINE Warnung, nicht eine Wand davon (Prinzip II)")
    void aPileOfLootWarnsOnce() {
        fillInventory();

        for (int i = 0; i < 200; i++) {
            listener.onAttemptPickup(pickup());
        }

        assertThat(notice.shown)
                .as("das Ereignis feuert mehrmals je Sekunde, solange der Spieler darauf steht")
                .hasSize(1);
    }

    @Test
    @DisplayName("nach Ablauf der Sperrzeit warnt es wieder - es bleibt eine Warnung")
    void afterTheCooldownItWarnsAgain() {
        fillInventory();
        listener.onAttemptPickup(pickup());

        clock.advance(InventoryFullNoticeListener.COOLDOWN.plusSeconds(1));
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown).hasSize(2);
    }

    @Test
    @DisplayName("kurz vor Ablauf der Sperrzeit warnt es noch nicht")
    void justBeforeTheCooldownItStaysQuiet() {
        fillInventory();
        listener.onAttemptPickup(pickup());

        clock.advance(InventoryFullNoticeListener.COOLDOWN.minusSeconds(1));
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown).hasSize(1);
    }

    @Test
    @DisplayName("die Sperrzeit gilt je Spieler, nicht global")
    void theCooldownIsPerPlayer() {
        PlayerMock second = server.addPlayer();
        fillInventory(player);
        fillInventory(second);

        listener.onAttemptPickup(pickup(player));
        listener.onAttemptPickup(pickup(second));

        assertThat(notice.shown).hasSize(2);
    }

    @Test
    @DisplayName("ein Spieler, der geht, wird vergessen - die Karte wächst nicht mit jeder Sitzung")
    void aDepartedPlayerIsForgotten() {
        fillInventory();
        listener.onAttemptPickup(pickup());
        assertThat(listener.trackedPlayers()).isEqualTo(1);

        listener.forget(player.getUniqueId());

        assertThat(listener.trackedPlayers()).isZero();
    }

    // --- helpers ----------------------------------------------------------------------------

    private void fillInventory() {
        fillInventory(player);
    }

    private void fillInventory(PlayerMock target) {
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) {
            target.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
    }

    private PlayerAttemptPickupItemEvent pickup() {
        return pickup(player);
    }

    private PlayerAttemptPickupItemEvent pickup(PlayerMock target) {
        Item item =
                target.getWorld()
                        .dropItem(target.getLocation(), new ItemStack(Material.DIAMOND));
        return new PlayerAttemptPickupItemEvent(target, item, 0);
    }

    /** Records which keys were shown, so the test never has to render text. */
    private static final class RecordingNotice implements ClassNotice {
        final List<MessageKey> shown = new ArrayList<>();

        @Override
        public void show(UUID playerId, MessageKey key) {
            shown.add(key);
        }
    }

    /** A clock the test moves by hand - no waiting, and the cooldown stays exact. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-08-21T18:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }
    }
}

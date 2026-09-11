package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Der Betreiber-Zugang: ein Weg in die Welt, der keine Klasse waehlt.
 *
 * <p><b>Der Zustand gab es schon und war nur unerreichbar.</b> Eine Sitzung ohne aktiven Charakter
 * ist das, worin jeder zwischen Beitritt und Wahl steckt (ADR-020) - der einzige Grund, warum niemand
 * darin bleiben konnte, ist {@code NoCharacterGuardListener}, der ihn festhaelt. Den Halt zu loesen
 * ist deshalb der ganze Mechanismus: kein neuer Zustand, kein Stat-Holder, keine Charakterzeile.
 *
 * <p>Der Klick selbst wird nicht nachgestellt - MockBukkit kann {@code InventoryClickEvent} nicht
 * bauen, und JUnit meldete das als <em>uebersprungen</em> statt als Fehler. Geprueft wird, was der
 * Klick aufruft.
 */
class AdminEntryTest {

    private static final Logger QUIET = Logger.getLogger("admin-entry-test");

    private ServerMock server;
    private NoCharacterGuardListener guard;
    private ClassSelectionMenu menu;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        guard = new NoCharacterGuardListener(QUIET);
        menu = new ClassSelectionMenu(PlatformClassFixture.registry(), PlatformClassFixture.messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ohne Berechtigung sieht das Fenster genau so aus wie vorher")
    void withoutThePermissionNothingChanges() {
        Inventory withoutEntry = menu.build(List.of(), false);

        assertThat(withoutEntry.getItem(ClassSelectionMenu.ADMIN_SLOT))
                .as("ein Spieler ohne das Recht darf den Zugang nicht einmal sehen")
                .isNull();
    }

    @Test
    @DisplayName("mit Berechtigung steht der Zugang abseits der drei Klassen")
    void withThePermissionTheEntryAppears() {
        Inventory withEntry = menu.build(List.of(), true);

        assertThat(withEntry.getItem(ClassSelectionMenu.ADMIN_SLOT)).isNotNull();
        assertThat(withEntry.getItem(ClassSelectionMenu.ADMIN_SLOT).getType())
                .isEqualTo(Material.ENDER_EYE);
        for (int offer : ClassSelectionMenu.OFFER_SLOTS) {
            assertThat(ClassSelectionMenu.isAdminEntry(offer))
                    .as("Platz " + offer + " gehoert einer Klasse und darf nie als Zugang gelten")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("der Zugang setzt auf Creative und loest den Halt")
    void theentryReleasesAndSwitchesToCreative() {
        ClassSelectionListener listener = listenerFor(true);
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        guard.hold(player);

        listener.enterWithoutClass(player);

        assertThat(guard.isHeld(player.getUniqueId()))
                .as("ohne das waere der Betreiber bewegungsunfaehig - der Halt IST die Sperre")
                .isFalse();
        assertThat(player.getGameMode()).isEqualTo(GameMode.CREATIVE);
        assertThat(listener.isWithoutClass(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("wer so hereinkam, wird beim Verlassen vergessen")
    void leavingClearsTheMark() {
        ClassSelectionListener listener = listenerFor(true);
        PlayerMock player = server.addPlayer();
        guard.hold(player);
        listener.enterWithoutClass(player);

        listener.forget(player.getUniqueId());

        assertThat(listener.isWithoutClass(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("wer nicht so hereinkam, gilt auch nicht als klassenlos")
    void anordinaryPlayerIsNotMarked() {
        ClassSelectionListener listener = listenerFor(false);
        PlayerMock player = server.addPlayer();

        assertThat(listener.isWithoutClass(player.getUniqueId())).isFalse();
    }

    private ClassSelectionListener listenerFor(boolean permitted) {
        return new ClassSelectionListener(
                new rpg.core.classes.ClassSelection(
                        new StubRepository(), new rpg.core.event.DefaultEventBus(QUIET), QUIET),
                menu,
                new StubSessions(),
                guard,
                (player, character) -> true,
                PlatformClassFixture.emptySlots(),
                new SelectionTimeout(
                        server, new InlineScheduler(), PlatformClassFixture.messages()),
                new InlineScheduler(),
                player -> permitted,
                PlatformClassFixture.messages(),
                QUIET);
    }

    /** Fuehrt sofort aus, ausser Verzoegertem - eine Uhr, die sofort ablaeuft, waere keine. */
    private static final class InlineScheduler implements rpg.core.scheduler.Scheduler {

        @Override
        public rpg.core.scheduler.TaskHandle runSyncAtLocation(
                rpg.core.scheduler.WorldPosition position, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public rpg.core.scheduler.TaskHandle runSyncOnEntity(
                rpg.core.scheduler.EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public rpg.core.scheduler.TaskHandle runSyncOnEntityDelayed(
                rpg.core.scheduler.EntityRef entity, java.time.Duration delay, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public rpg.core.scheduler.TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public rpg.core.scheduler.TaskHandle runAsyncDelayed(java.time.Duration delay, Runnable task) {
            // NICHT ausfuehren: das ist die Auswahl-Uhr, und sie darf nicht im selben Tick ablaufen.
            return handle();
        }

        private static rpg.core.scheduler.TaskHandle handle() {
            return new rpg.core.scheduler.TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    /** Nichts gespeichert: dieser Test kommt bis zur Charakterwahl gar nicht. */
    private static final class StubRepository implements rpg.core.session.CharacterRepository {

        @Override
        public java.util.concurrent.CompletableFuture<List<rpg.core.session.PlayerCharacter>>
                findByPlayer(java.util.UUID playerId) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.util.Optional<rpg.core.session.PlayerCharacter>>
                find(java.util.UUID characterId) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletableFuture<rpg.core.session.PlayerCharacter> create(
                java.util.UUID playerId, rpg.core.session.CharacterClass characterClass) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new UnsupportedOperationException("dieser Test waehlt keine Klasse"));
        }

        @Override
        public void markDirty(java.util.UUID characterId) {}
    }

    /** Kennt niemanden - der Zugang braucht die Sitzung nicht, nur den Halt. */
    private static final class StubSessions implements rpg.core.session.SessionRegistry {

        @Override
        public java.util.Optional<rpg.core.session.PlayerSession> find(java.util.UUID playerId) {
            return java.util.Optional.empty();
        }

        @Override
        public rpg.core.session.PlayerSession require(java.util.UUID playerId) {
            throw new java.util.NoSuchElementException("keine Sitzung in diesem Test");
        }

        @Override
        public boolean isReady(java.util.UUID playerId) {
            return false;
        }

        @Override
        public int activeSessionCount() {
            return 0;
        }
    }
}

package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.ClassSelection;
import rpg.core.event.DefaultEventBus;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.session.CharacterClass;
import rpg.core.session.CharacterRepository;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;

/**
 * Was nach der Wahl passiert: der Spieler betritt den Spielzustand oder bleibt in der Auswahl.
 *
 * <p>Der Grund für diesen Test ist eine Lücke, die alle Modultests von B07 überlebt hätte. Die Wahl
 * legt den Charakter in der Datenbank an - aber die laufende Sitzung hatte keinen Weg, ihn anzunehmen.
 * Ohne den Eintritt wirkt die Wahl erst beim nächsten Login: kein Stat-Holder, kein Level, keine
 * Stufen, keine Ausrüstung. Sichtbar wäre nur, dass sich das Menü schließt.
 *
 * <p>Der Klick selbst wird nicht nachgestellt. MockBukkit kann {@code InventoryClickEvent} nicht bauen
 * ({@code SimpleInventoryViewMock.convertSlot} ist nicht implementiert), und JUnit meldet das als
 * <em>übersprungen</em>, nicht als Fehler - ein Test über das Ereignis wäre also stillschweigend nie
 * gelaufen. Geprüft wird deshalb {@code choose}, das der Klick aufruft.
 */
class ClassSelectionEntryTest {

    private static final Logger QUIET = Logger.getLogger("class-selection-entry-test");

    private ServerMock server;
    private RecordingScheduler scheduler;
    private NoCharacterGuardListener guard;
    private StubSessions sessions;
    private RecordingEntry entry;
    private ClassSelectionListener listener;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        scheduler = new RecordingScheduler();
        guard = new NoCharacterGuardListener(QUIET);
        sessions = new StubSessions();
        entry = new RecordingEntry();
        listener =
                new ClassSelectionListener(
                        new ClassSelection(new StubRepository(), new DefaultEventBus(QUIET), QUIET),
                        new ClassSelectionMenu(
                                PlatformClassFixture.registry(), PlatformClassFixture.messages()),
                        sessions,
                        guard,
                        entry,
                        PlatformClassFixture.emptySlots(),
                        new SelectionTimeout(server, scheduler, PlatformClassFixture.messages()),
                        scheduler,
                        QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Wahl führt in den Spielzustand und löst erst dann den Halt (US1.1)")
    void anAcceptedChoiceEntersTheGameState() {
        PlayerMock player = held();

        choose(player);

        assertThat(entry.entered)
                .as("der angelegte Charakter wird der Sitzung übergeben")
                .hasSize(1);
        assertThat(entry.entered.get(0).characterClass()).isEqualTo(CharacterClass.WARRIOR);
        assertThat(guard.isHeld(player.getUniqueId()))
                .as("erst nach dem Eintritt ist der Halt aufgehoben")
                .isFalse();
    }

    @Test
    @DisplayName("scheitert der Eintritt, bleibt der Spieler in der Auswahl statt in der Welt")
    void aFailedEntryKeepsThePlayerInTheMenu() {
        entry.succeed = false;
        PlayerMock player = held();

        choose(player);

        assertThat(entry.entered).as("es wurde versucht").hasSize(1);
        assertThat(guard.isHeld(player.getUniqueId()))
                .as("ein Spieler ohne Werte und ohne Ausrüstung darf nicht in die Welt")
                .isTrue();
    }

    @Test
    @DisplayName("wirft der Eintritt, ist das ein gescheiterter Eintritt - kein Absturz (Prinzip VI)")
    void aThrowingEntryIsTreatedAsAFailure() {
        entry.failure = new IllegalStateException("Datenbank weg");
        PlayerMock player = held();

        choose(player);

        assertThat(guard.isHeld(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("der Eintritt läuft auf dem Tick des Spielers, nicht auf dem Datenbank-Thread")
    void theEntryRunsOnThePlayersOwnTick() {
        PlayerMock player = held();

        listener.choose(player, sessions.require(player.getUniqueId()), CharacterClass.WARRIOR);

        // Noch nichts passiert: die Fortsetzung liegt als Aufgabe beim entitätsgebundenen Scheduler.
        assertThat(entry.entered).isEmpty();
        assertThat(scheduler.entityTasks).hasSize(1);
        assertThat(scheduler.globalTasks)
                .as("nie der globale Scheduler (Prinzip I, ADR-007)")
                .isZero();

        scheduler.drain();
        assertThat(entry.entered).hasSize(1);
    }

    // --- fixtures ---

    private PlayerMock held() {
        PlayerMock player = server.addPlayer();
        sessions.put(player.getUniqueId(), new PlayerSession(player.getUniqueId(), null, List.of()));
        guard.hold(player);
        return player;
    }

    private void choose(PlayerMock player) {
        listener.choose(player, sessions.require(player.getUniqueId()), CharacterClass.WARRIOR);
        scheduler.drain();
    }

    private static final class RecordingEntry implements CharacterEntry {

        private final List<PlayerCharacter> entered = new ArrayList<>();
        private boolean succeed = true;
        private RuntimeException failure;

        @Override
        public boolean enter(org.bukkit.entity.Player player, PlayerCharacter character) {
            entered.add(character);
            if (failure != null) {
                throw failure;
            }
            return succeed;
        }
    }

    private static final class RecordingScheduler implements Scheduler {

        private final List<Runnable> entityTasks = new ArrayList<>();
        private int globalTasks;
        private int delayed;

        void drain() {
            List<Runnable> pending = new ArrayList<>(entityTasks);
            entityTasks.clear();
            pending.forEach(Runnable::run);
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            globalTasks++;
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            entityTasks.add(task);
            return handle();
        }

        /** ADR-024: verzoegert, aber im Test genauso behandelt wie sofort. */
        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            entityTasks.add(task);
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            // Nicht ausfuehren: eine verzoegerte Aufgabe ist noch nicht faellig. Wer sie sofort laufen
            // laesst, laesst die Auswahl-Uhr in demselben Tick ablaufen, in dem sie gestartet wurde.
            delayed++;
            return handle();
        }

        private static TaskHandle handle() {
            return new TaskHandle() {
                @Override
                public void cancel() {
                    // nichts abzubrechen
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    private static final class StubSessions implements SessionRegistry {

        private final java.util.Map<UUID, PlayerSession> byId = new java.util.HashMap<>();

        void put(UUID playerId, PlayerSession session) {
            byId.put(playerId, session);
        }

        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            return Optional.ofNullable(byId.get(playerId));
        }

        @Override
        public PlayerSession require(UUID playerId) {
            return find(playerId).orElseThrow();
        }

        @Override
        public boolean isReady(UUID playerId) {
            return byId.containsKey(playerId);
        }

        @Override
        public int activeSessionCount() {
            return byId.size();
        }
    }

    /** Legt an, was verlangt wird - die Regeln des Anlegens sind in rpg-core geprüft. */
    private static final class StubRepository implements CharacterRepository {

        @Override
        public CompletableFuture<List<PlayerCharacter>> findByPlayer(UUID playerId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Optional<PlayerCharacter>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<PlayerCharacter> create(UUID playerId, CharacterClass id) {
            return CompletableFuture.completedFuture(
                    PlayerCharacter.create(playerId, id, Instant.EPOCH));
        }

        @Override
        public void markDirty(UUID characterId) {
            // nichts zu merken
        }
    }
}

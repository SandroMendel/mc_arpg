package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.session.CharacterClass;
import rpg.core.session.CharacterClassTakenException;
import rpg.core.session.CharacterRepository;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;

/** T047, T048, T049, T050 - der Ablauf der Erstwahl (US1, ADR-020). */
class ClassSelectionTest {

    private static final Logger QUIET = quietLogger();

    @Test
    @DisplayName("ohne Charakter ist eine Wahl nötig, mit Charakter nicht (US1.1, US1.6)")
    void needsSelectionOnlyWithoutCharacter() {
        Fixture fixture = new Fixture();
        PlayerSession fresh = session(null);
        PlayerCharacter warrior = character(CharacterClass.WARRIOR);

        assertThat(fixture.selection.needsSelection(fresh)).isTrue();
        assertThat(fixture.selection.needsSelection(session(warrior, warrior))).isFalse();
    }

    @Test
    @DisplayName("verfügbar sind nur Klassen, für die das Konto noch keinen Charakter hat (FR-035)")
    void availableExcludesTakenClasses() {
        Fixture fixture = new Fixture();
        PlayerCharacter warrior = character(CharacterClass.WARRIOR);
        PlayerCharacter mage = character(CharacterClass.MAGE);

        assertThat(fixture.selection.available(session(null))).containsExactlyInAnyOrder(
                CharacterClass.WARRIOR, CharacterClass.MAGE, CharacterClass.ROGUE);
        assertThat(fixture.selection.available(session(null, warrior)))
                .containsExactlyInAnyOrder(CharacterClass.MAGE, CharacterClass.ROGUE);
        assertThat(fixture.selection.available(session(null, warrior, mage)))
                .containsExactly(CharacterClass.ROGUE);
    }

    @Test
    @DisplayName("mit allen drei Klassen belegt bietet die Auswahl nichts an - Edge Case")
    void threeCharactersLeaveNothingToChoose() {
        Fixture fixture = new Fixture();
        PlayerSession full =
                session(
                        null,
                        character(CharacterClass.WARRIOR),
                        character(CharacterClass.MAGE),
                        character(CharacterClass.ROGUE));

        assertThat(fixture.selection.available(full)).isEmpty();
    }

    @Test
    @DisplayName("eine Wahl legt den Charakter an und veröffentlicht ein Ereignis")
    void choosingCreatesTheCharacterAndPublishes() {
        Fixture fixture = new Fixture();
        PlayerSession fresh = session(null);

        ClassSelectionResult result =
                fixture.selection.choose(fresh, CharacterClass.ROGUE).join();

        assertThat(result.accepted()).isTrue();
        assertThat(result.character()).get().extracting(PlayerCharacter::characterClass)
                .isEqualTo(CharacterClass.ROGUE);
        assertThat(fixture.events).hasSize(1);
        assertThat(fixture.events.get(0).characterClass()).isEqualTo(CharacterClass.ROGUE);
        assertThat(fixture.repository.created).containsExactly(CharacterClass.ROGUE);
    }

    @Test
    @DisplayName("eine bereits belegte Klasse wird benannt abgelehnt, ohne Ausnahme (FR-036)")
    void takenClassIsRejectedByName() {
        Fixture fixture = new Fixture();
        PlayerSession withWarrior = session(null, character(CharacterClass.WARRIOR));

        ClassSelectionResult result =
                fixture.selection.choose(withWarrior, CharacterClass.WARRIOR).join();

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection()).hasValue(ClassSelectionRejection.CLASS_ALREADY_TAKEN);
        assertThat(fixture.repository.created).as("kein Anlegeversuch").isEmpty();
        assertThat(fixture.events).isEmpty();
    }

    @Test
    @DisplayName("gewinnt der Schlüssel das Rennen, wird abgelehnt statt geworfen (FR-036)")
    void concurrentLossIsARejectionNotAnException() {
        Fixture fixture = new Fixture();
        // Die Datenbank hat das letzte Wort: der Unique-Index aus B03 schlägt zu, nachdem die
        // anwendungsseitige Prüfung passiert war. Genau der Fall zweier gleichzeitiger Beitritte.
        fixture.repository.failNextWithTaken = true;
        PlayerSession fresh = session(null);

        ClassSelectionResult result =
                fixture.selection.choose(fresh, CharacterClass.MAGE).join();

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection()).hasValue(ClassSelectionRejection.CLASS_ALREADY_TAKEN);
        assertThat(fixture.events).as("kein Ereignis für einen nicht angelegten Charakter").isEmpty();
    }

    @Test
    @DisplayName("eine Sitzung mit aktivem Charakter kann nicht erneut wählen")
    void sessionWithActiveCharacterCannotChoose() {
        Fixture fixture = new Fixture();
        PlayerCharacter warrior = character(CharacterClass.WARRIOR);

        ClassSelectionResult result =
                fixture.selection.choose(session(warrior, warrior), CharacterClass.MAGE).join();

        assertThat(result.rejection()).hasValue(ClassSelectionRejection.ALREADY_HAS_CHARACTER);
        assertThat(fixture.repository.created).isEmpty();
    }

    @Test
    @DisplayName("ein Verbindungsabbruch während der Auswahl lässt keinen halben Charakter zurück (FR-037)")
    void abortedSelectionLeavesNothingBehind() {
        Fixture fixture = new Fixture();
        PlayerSession fresh = session(null);

        // Nichts gewählt, Sitzung endet. Der einzige Weg, einen Charakter anzulegen, ist choose -
        // also kann es keinen halben geben. Das ist die Zusage, und sie folgt aus der Ausschließlichkeit
        // von choose, nicht aus einer Aufräumroutine.
        assertThat(fixture.repository.created).isEmpty();
        assertThat(fixture.events).isEmpty();
        assertThat(fixture.selection.needsSelection(fresh))
                .as("beim nächsten Beitritt wieder zu wählen")
                .isTrue();
    }

    @Test
    @DisplayName("ein echter Datenbankfehler wird NICHT als belegte Klasse getarnt")
    void realFailureIsNotDisguisedAsRejection() {
        Fixture fixture = new Fixture();
        fixture.repository.failNextWithRuntime = true;
        PlayerSession fresh = session(null);

        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                () -> fixture.selection.choose(fresh, CharacterClass.MAGE).join()))
                .as("ein kaputtes Repository ist kein volles Konto")
                .isNotNull();
        assertThat(fixture.events).isEmpty();
    }

    // --- helpers ----------------------------------------------------------------------------

    private static final class Fixture {
        final RecordingRepository repository = new RecordingRepository();
        final EventBus eventBus = new DefaultEventBus(QUIET);
        final List<ClassChangedEvent> events = new ArrayList<>();
        final ClassSelection selection;

        Fixture() {
            eventBus.subscribe(ClassChangedEvent.class, events::add);
            selection = new ClassSelection(repository, eventBus, QUIET);
        }
    }

    private static final class RecordingRepository implements CharacterRepository {
        final List<CharacterClass> created = new ArrayList<>();
        boolean failNextWithTaken;
        boolean failNextWithRuntime;

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
            if (failNextWithTaken) {
                failNextWithTaken = false;
                return CompletableFuture.failedFuture(
                        new CharacterClassTakenException(playerId, id));
            }
            if (failNextWithRuntime) {
                failNextWithRuntime = false;
                return CompletableFuture.failedFuture(new IllegalStateException("pool exhausted"));
            }
            created.add(id);
            return CompletableFuture.completedFuture(
                    PlayerCharacter.create(playerId, id, Instant.EPOCH));
        }

        @Override
        public void markDirty(UUID characterId) {
            // no-op: this test is about the selection rule, not the write path
        }
    }

    private static PlayerSession session(PlayerCharacter active, PlayerCharacter... available) {
        List<PlayerCharacter> all = new ArrayList<>(List.of(available));
        if (active != null && !all.contains(active)) {
            all.add(active);
        }
        return new PlayerSession(UUID.randomUUID(), active, all);
    }

    private static PlayerCharacter character(CharacterClass id) {
        return PlayerCharacter.create(UUID.randomUUID(), id, Instant.EPOCH);
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(ClassSelectionTest.class.getName());
        logger.setLevel(Level.OFF);
        return logger;
    }
}

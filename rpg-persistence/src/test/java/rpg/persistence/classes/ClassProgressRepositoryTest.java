package rpg.persistence.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.ClassProgress;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.persistence.support.PersistenceHarness;

/**
 * T129 und T131: die erreichten Stufen gehen durch die echte Datenbank und kommen wieder heraus.
 *
 * <p>Echtes PostgreSQL, keine Attrappen (Prinzip VII). Was hier zählt, ist nicht das Schreiben allein,
 * sondern der Revisionszähler und das Verhalten, wenn zwischen Markierung und Flush niemand mehr da
 * ist - genau die beiden Stellen, an denen ein Schreibpuffer stillschweigend etwas verliert.
 *
 * <p>Der Neustart ist hier ein <b>zweites Repository</b> auf derselben Datenbank. Mehr ist ein
 * Serverneustart für diese Schicht nicht: derselbe Zustand auf der Platte, ein Objekt ohne Gedächtnis
 * davor (SC-006, US3.5).
 */
class ClassProgressRepositoryTest {

    private static final Logger QUIET = Logger.getLogger("class-progress-repository-test");

    private PersistenceHarness harness;
    private JdbcClassProgressRepository repository;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        harness = new PersistenceHarness();
        repository = newRepository();
        harness.flushCycle.register(AggregateType.CHARACTER_CLASS_PROGRESS, repository);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("geschriebene Stufen kommen unverändert zurück")
    void tiersSurviveTheRoundTrip() {
        UUID characterId = character(CharacterClass.WARRIOR);
        write(characterId, 3, 5);

        Optional<ClassProgress> loaded = repository.find(characterId).join();

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().armorTier()).isEqualTo(3);
        assertThat(loaded.orElseThrow().weaponTier()).isEqualTo(5);
    }

    @Test
    @DisplayName("ein Charakter ohne Zeile ist leer, nicht Stufe null")
    void anUnknownCharacterIsEmpty() {
        assertThat(repository.find(UUID.randomUUID()).join())
                .as("der Aufrufer setzt selbst ClassProgress.initial ein")
                .isEmpty();
    }

    @Test
    @DisplayName("jeder Schreibvorgang zählt die Revision hoch")
    void everyWriteAdvancesTheRevision() {
        UUID characterId = character(CharacterClass.MAGE);
        write(characterId, 1, 1);
        long first = repository.find(characterId).join().orElseThrow().revision();

        write(characterId, 2, 1);

        assertThat(repository.find(characterId).join().orElseThrow().revision())
                .as("sonst könnte niemand zwei Schreibvorgänge unterscheiden")
                .isGreaterThan(first);
    }

    @Test
    @DisplayName("ohne lebenden Stand wird nichts geschrieben, die Marke gilt aber als erledigt")
    void amarkWithoutALiveValueIsDropped() {
        UUID characterId = character(CharacterClass.ROGUE);
        repository.setLiveSource(id -> Optional.empty());

        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).join();

        assertThat(repository.find(characterId).join())
                .as("nichts erfunden, wo nichts zu schreiben war - und die Marke blockiert nicht")
                .isEmpty();
    }

    @Test
    @DisplayName("T131: die Stufen überleben einen Neustart - ein neues Repository liest sie")
    void tiersSurviveARestart() {
        UUID characterId = character(CharacterClass.WARRIOR);
        write(characterId, 4, 2);

        // Der Neustart: ein Objekt ohne Gedächtnis, dieselbe Datenbank.
        JdbcClassProgressRepository afterRestart = newRepository();

        Optional<ClassProgress> loaded = afterRestart.find(characterId).join();
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().armorTier()).isEqualTo(4);
        assertThat(loaded.orElseThrow().weaponTier()).isEqualTo(2);
    }

    @Test
    @DisplayName("der Neustart liest auch die Revision, statt bei null anzufangen")
    void therevisionSurvivesARestartToo() {
        UUID characterId = character(CharacterClass.MAGE);
        write(characterId, 1, 1);
        write(characterId, 1, 2);
        long before = repository.find(characterId).join().orElseThrow().revision();

        JdbcClassProgressRepository afterRestart = newRepository();

        assertThat(afterRestart.find(characterId).join().orElseThrow().revision())
                .as("eine zurückgesetzte Revision würde einen fremden Schreibvorgang verdecken")
                .isEqualTo(before);
    }

    // --- fixtures ---

    private JdbcClassProgressRepository newRepository() {
        return new JdbcClassProgressRepository(
                harness.pools.loginPool(),
                harness.scheduler,
                harness.flushCycle,
                Clock.systemUTC());
    }

    private UUID character(CharacterClass characterClass) {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Clock.systemUTC().instant()));
        harness.flushCycle.flushNow(FlushReason.SESSION_END).join();
        PlayerCharacter character = harness.characters.create(playerId, characterClass).join();
        return character.characterId();
    }

    private void write(UUID characterId, int armorTier, int weaponTier) {
        repository.setLiveSource(
                id ->
                        Optional.of(
                                new ClassProgress(
                                        id,
                                        armorTier,
                                        weaponTier,
                                        ClassProgress.CURRENT_DATA_VERSION,
                                        0L)));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).join();
    }
}

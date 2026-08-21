package rpg.persistence.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.inventory.CharacterInventory;
import rpg.core.persistence.AggregateType;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * Der Rundlauf des gespeicherten Inventars gegen eine echte PostgreSQL.
 *
 * <p>Der Inhalt ist hier absichtlich irgendein Bytefeld: was drinsteht, weiß nur die Plattform, und
 * dieser Test prüft die Haltung, nicht das Format. Genau das ist die Zusage - der Kern reicht die Bytes
 * unverändert durch.
 */
class CharacterInventoryPersistenceTest {

    private static final Logger QUIET = Logger.getLogger("character-inventory-test");

    private PersistenceHarness harness;
    private JdbcCharacterInventoryRepository repository;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        harness = new PersistenceHarness();
        repository =
                new JdbcCharacterInventoryRepository(
                        harness.pools.loginPool(),
                        harness.scheduler,
                        harness.flushCycle,
                        Clock.systemUTC());
        harness.flushCycle.register(AggregateType.CHARACTER_INVENTORY, repository);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("was gespeichert wurde, kommt unverändert zurück")
    void contentsSurviveTheRoundTrip() {
        UUID characterId = storedCharacter();
        byte[] contents = "irgendein serialisiertes Inventar".getBytes(StandardCharsets.UTF_8);
        repository.setLiveSource(id -> Optional.of(CharacterInventory.of(id, contents, new byte[0])));

        write(characterId);

        Optional<CharacterInventory> loaded = repository.find(characterId).join();
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().contents()).isEqualTo(contents);
    }

    @Test
    @DisplayName("die Enderchest wird getrennt gehalten und getrennt zurückgegeben")
    void theEnderChestIsItsOwnBlob() {
        // Vanilla hängt auch sie am Spieler. Ohne eigene Haltung wäre sie der eine Behälter, den sich
        // die drei Charaktere eines Kontos teilen.
        UUID characterId = storedCharacter();
        repository.setLiveSource(
                id ->
                        Optional.of(
                                CharacterInventory.of(
                                        id, new byte[] {1, 1}, new byte[] {2, 2, 2})));

        write(characterId);

        CharacterInventory loaded = repository.find(characterId).join().orElseThrow();
        assertThat(loaded.contents()).containsExactly(1, 1);
        assertThat(loaded.enderChest()).containsExactly(2, 2, 2);
    }

    @Test
    @DisplayName("ein Charakter ohne Zeile trägt nichts - das ist kein Fehler")
    void aCharacterWithoutARowIsEmpty() {
        assertThat(repository.find(UUID.randomUUID()).join()).isEmpty();
    }

    @Test
    @DisplayName("ein zweiter Schreibvorgang ersetzt den Inhalt und zählt die Revision hoch")
    void asecondWriteReplacesTheContents() {
        UUID characterId = storedCharacter();
        repository.setLiveSource(
                id -> Optional.of(CharacterInventory.of(id, new byte[] {1, 2, 3}, new byte[] {4})));
        write(characterId);
        long firstRevision = repository.find(characterId).join().orElseThrow().revision();

        repository.setLiveSource(
                id -> Optional.of(CharacterInventory.of(id, new byte[] {9, 9}, new byte[] {8})));
        write(characterId);

        CharacterInventory second = repository.find(characterId).join().orElseThrow();
        assertThat(second.contents()).containsExactly(9, 9);
        assertThat(second.revision()).isGreaterThan(firstRevision);
    }

    @Test
    @DisplayName("ohne Schnappschuss wird nichts geschrieben, die Marke gilt aber als erledigt")
    void aMarkWithoutASnapshotIsDropped() {
        UUID characterId = storedCharacter();
        repository.setLiveSource(id -> Optional.empty());

        write(characterId);

        assertThat(repository.find(characterId).join())
                .as("nichts erfunden, wo nichts zu schreiben war")
                .isEmpty();
    }

    @Test
    @DisplayName("das leere Inventar ist ein Wert, kein fehlender Eintrag")
    void carryingNothingIsStored() {
        UUID characterId = storedCharacter();
        repository.setLiveSource(id -> Optional.of(CharacterInventory.empty(id)));

        write(characterId);

        Optional<CharacterInventory> loaded = repository.find(characterId).join();
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("mit dem Charakter verschwindet auch sein Inventar (ON DELETE CASCADE)")
    void deletingTheCharacterTakesTheInventoryWithIt() throws Exception {
        UUID characterId = storedCharacter();
        repository.setLiveSource(id -> Optional.of(CharacterInventory.of(id, new byte[] {7}, new byte[0])));
        write(characterId);

        try (var connection = PostgresContainer.openConnection();
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM rpg.character WHERE character_id = ?")) {
            statement.setObject(1, characterId);
            statement.executeUpdate();
        }

        assertThat(repository.find(characterId).join())
                .as("B02s Löschpfad muss von dieser Tabelle nichts wissen")
                .isEmpty();
    }

    // --- fixtures ---

    /** Ein Charakter in der Datenbank - der Fremdschlüssel verlangt ihn. */
    private UUID storedCharacter() {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(
                rpg.core.persistence.PlayerState.initial(playerId, Clock.systemUTC().instant()));
        harness.flushCycle.flushNow(rpg.core.persistence.FlushReason.SESSION_END).join();
        PlayerCharacter character =
                harness.characters.create(playerId, CharacterClass.WARRIOR).join();
        return character.characterId();
    }

    private void write(UUID characterId) {
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(rpg.core.persistence.FlushReason.SESSION_END).join();
    }
}

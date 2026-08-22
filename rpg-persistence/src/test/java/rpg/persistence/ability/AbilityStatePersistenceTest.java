package rpg.persistence.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.AbilityState;
import rpg.core.ability.ToggleState;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T032 - SC-004: was ein Charakter je Fähigkeit besitzt, überlebt den Neustart.
 *
 * <p>Drei Zusagen, und die beiden hinteren sind die interessanten: ein <b>abgelaufener</b> Cooldown
 * wird beim Laden verworfen statt geladen, und eine Zeile, die nur noch Vorgabewerte trägt, wird
 * gelöscht. Ohne das behielte die Tabelle eine Zeile für jede je benutzte Fähigkeit.
 */
class AbilityStatePersistenceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    private PersistenceHarness harness;
    private JdbcAbilityStateRepository repository;
    private UUID characterId;

    @BeforeEach
    void setUp() {
        harness = new PersistenceHarness();
        repository =
                new JdbcAbilityStateRepository(
                        harness.pools.loginPool(),
                        harness.scheduler,
                        harness.flushCycle,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        characterId = insertCharacter();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("ein Charakter ohne Zeile besitzt nichts - das ist kein Fehler")
    void nothingStoredIsNotAnError() throws Exception {
        assertThat(repository.findAll(characterId).get()).isEmpty();
    }

    @Test
    @DisplayName("SC-004: Rang und laufender Cooldown überleben das Schreiben und Laden")
    void rankAndCooldownSurvive() throws Exception {
        Instant until = NOW.plusSeconds(60);
        repository.setLiveSource(
                id ->
                        List.of(
                                new AbilityState(
                                        id, "probe.strike", 3, until, null,
                                        AbilityState.CURRENT_DATA_VERSION, 0L)));

        flush();

        List<AbilityState> loaded = repository.findAll(characterId).get();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).rank()).isEqualTo(3);
        assertThat(loaded.get(0).cooldownUntil()).isEqualTo(until);
    }

    @Test
    @DisplayName("FR-031: ein abgelaufener Cooldown wird VERWORFEN, nicht geladen")
    void anExpiredCooldownIsDiscarded() throws Exception {
        // Rang 2, damit die Zeile ueberhaupt etwas traegt - sonst wuerde sie gar nicht erst
        // geschrieben und der Test prüfte das Falsche.
        repository.setLiveSource(
                id ->
                        List.of(
                                new AbilityState(
                                        id, "probe.strike", 2, NOW.minusSeconds(5), null,
                                        AbilityState.CURRENT_DATA_VERSION, 0L)));

        flush();

        List<AbilityState> loaded = repository.findAll(characterId).get();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).rank()).isEqualTo(2);
        assertThat(loaded.get(0).cooldownUntil())
                .as("vergangen, also nicht mitgeschleppt")
                .isNull();
    }

    @Test
    @DisplayName("eine Zeile, die auf den Vorgabewert zurückfällt, wird gelöscht")
    void aDefaultRowIsDeleted() throws Exception {
        repository.setLiveSource(
                id ->
                        List.of(
                                new AbilityState(
                                        id, "probe.strike", 4, null, null,
                                        AbilityState.CURRENT_DATA_VERSION, 0L)));
        flush();
        assertThat(repository.findAll(characterId).get()).hasSize(1);

        // Zurueck auf Rang 1, kein Cooldown, kein Toggle: nichts mehr, was eine Zeile rechtfertigt.
        repository.setLiveSource(
                id ->
                        List.of(
                                new AbilityState(
                                        id, "probe.strike", 1, null, null,
                                        AbilityState.CURRENT_DATA_VERSION, 0L)));
        flush();

        assertThat(repository.findAll(characterId).get())
                .as("sonst waechst die Tabelle mit jeder je benutzten Faehigkeit")
                .isEmpty();
    }

    @Test
    @DisplayName("FR-052d: die Spielereinstellung überlebt den Neustart")
    void theToggleSurvives() throws Exception {
        repository.setLiveSource(
                id ->
                        List.of(
                                new AbilityState(
                                        id, "probe.rise", 1, null, ToggleState.PARTIAL,
                                        AbilityState.CURRENT_DATA_VERSION, 0L)));

        flush();

        List<AbilityState> loaded = repository.findAll(characterId).get();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).toggleState()).isEqualTo(ToggleState.PARTIAL);
    }

    @Test
    @DisplayName("mehrere Fähigkeiten eines Charakters kommen in einem Zug zurück")
    void manyRowsPerCharacter() throws Exception {
        repository.setLiveSource(
                id ->
                        List.of(
                                new AbilityState(
                                        id, "probe.a", 2, null, null,
                                        AbilityState.CURRENT_DATA_VERSION, 0L),
                                new AbilityState(
                                        id, "probe.b", 5, null, null,
                                        AbilityState.CURRENT_DATA_VERSION, 0L)));

        flush();

        assertThat(repository.findAll(characterId).get())
                .extracting(AbilityState::abilityId)
                .containsExactlyInAnyOrder("probe.a", "probe.b");
    }

    private void flush() {
        repository.write(
                harness.pools.writePool(),
                List.of(
                        new DirtyMark(
                                AggregateType.CHARACTER_ABILITIES, characterId.toString(), NOW)));
    }

    /** A character to hang the rows on - the foreign key insists, and rightly so. */
    private static UUID insertCharacter() {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                java.sql.Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', 'WARRIOR')");
        } catch (Exception failure) {
            throw new AssertionError("could not insert a character", failure);
        }
        return characterId;
    }
}

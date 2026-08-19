package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T037 / ADR-004: an item stores its template id and rolled values, and nothing computed.
 *
 * <p>The last assertion is the one that matters. If a computed final value ever crept into storage,
 * every existing player item would be frozen at the balancing of the day it dropped, and a later
 * rework could not reach it.
 */
class ItemInstanceRoundTripTest {

    private PersistenceHarness harness;

    /** Since ADR-011 the owner of an item is a character, not the account. */
    private UUID owner;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        owner = insertCharacter(playerId, "WARRIOR");
    }

    /**
     * Inserts a character straight through SQL rather than through the repository.
     *
     * <p>This test is about item storage, and going through the character repository would make it
     * fail for reasons that have nothing to do with items.
     */
    private static UUID insertCharacter(UUID playerId, String characterClass) throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character (character_id, player_id,"
                                        + " character_class) VALUES (?, ?, ?)")) {
            statement.setObject(1, characterId);
            statement.setObject(2, playerId);
            statement.setString(3, characterClass);
            statement.executeUpdate();
        }
        return characterId;
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void templateIdAndRolledValuesSurviveARoundTrip() throws Exception {
        Map<String, Object> rolls = new LinkedHashMap<>();
        rolls.put("damage", 42.5d);
        rolls.put("quality", "pristine");
        rolls.put("cursed", Boolean.FALSE);

        UUID instanceId = UUID.randomUUID();
        harness.itemInstances.create(
                new ItemInstance(instanceId, owner, "sword.iron", rolls, 0L));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        ItemInstance reloaded = harness.itemInstances.load(instanceId).get().orElseThrow();

        assertThat(reloaded.templateId()).isEqualTo("sword.iron");
        assertThat(reloaded.rolledValues())
                .containsEntry("damage", 42.5d)
                .containsEntry("quality", "pristine")
                .containsEntry("cursed", Boolean.FALSE);
    }

    @Test
    void itemsCanBeListedByOwningCharacter() throws Exception {
        for (int i = 0; i < 3; i++) {
            harness.itemInstances.create(
                    new ItemInstance(UUID.randomUUID(), owner, "potion.health", Map.of(), 0L));
        }
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.itemInstances.loadByOwner(owner).get()).hasSize(3);
    }

    @Test
    void nothingButTemplateIdAndRollsIsStored() throws Exception {
        UUID instanceId = UUID.randomUUID();
        harness.itemInstances.create(
                new ItemInstance(
                        instanceId, owner, "sword.iron", Map.of("damage", 10.0d), 0L));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // Read the raw column: no computed total, no rendered lore (ADR-004).
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT template_id, rolled_values::text FROM rpg.item_instance"
                                        + " WHERE instance_id = ?")) {
            statement.setObject(1, instanceId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("sword.iron");
                String json = rows.getString(2);
                assertThat(json).contains("damage");
                assertThat(json).doesNotContain("lore").doesNotContain("finalDamage");
            }
        }
    }
}

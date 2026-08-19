package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
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
 * T063e / ADR-011: an item belongs to exactly one character.
 *
 * <p>The decision this test defends is easy to state and easy to lose: the three characters of one
 * account share no progress. If items hung off the account, the Warrior's sword would sit in the
 * Mage's inventory, and nothing in the code would look wrong - the query would simply return it.
 * That is why the separation is asserted against the database rather than against a service.
 */
class ItemOwnershipTest {

    private PersistenceHarness harness;
    private UUID playerId;
    private UUID warrior;
    private UUID mage;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        warrior = insertCharacter(playerId, "WARRIOR");
        mage = insertCharacter(playerId, "MAGE");
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void oneCharactersItemsAreNotTheOthersEvenOnTheSameAccount() throws Exception {
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), warrior, "sword.iron", Map.of(), 0L));
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), warrior, "shield.oak", Map.of(), 0L));
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), mage, "staff.ash", Map.of(), 0L));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        List<ItemInstance> warriorItems = harness.itemInstances.loadByOwner(warrior).get();
        List<ItemInstance> mageItems = harness.itemInstances.loadByOwner(mage).get();

        assertThat(warriorItems).extracting(ItemInstance::templateId)
                .containsExactlyInAnyOrder("sword.iron", "shield.oak");
        assertThat(mageItems).extracting(ItemInstance::templateId).containsExactly("staff.ash");
    }

    @Test
    void anItemWithoutAnExistingCharacterIsRefusedByTheDatabase() throws Exception {
        // The foreign key, not application code: a later block cannot write an ownerless item even
        // by accident.
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.item_instance (instance_id, owner_character_id,"
                                        + " template_id) VALUES (?, ?, 'sword.iron')")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, UUID.randomUUID()); // no such character
            org.assertj.core.api.Assertions.assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void deletingACharacterTakesItsItemsWithIt() throws Exception {
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), warrior, "sword.iron", Map.of(), 0L));
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), mage, "staff.ash", Map.of(), 0L));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "DELETE FROM rpg.character WHERE character_id = ?")) {
            statement.setObject(1, warrior);
            statement.executeUpdate();
        }

        // The Warrior's item is gone; the Mage's is untouched. An orphaned item row would be the
        // failure here, not a missing one.
        assertThat(itemCount()).isEqualTo(1L);
        assertThat(harness.itemInstances.loadByOwner(mage).get()).hasSize(1);
    }

    @Test
    void theMigrationLeftNoAccountLevelOwnerBehind() throws Exception {
        // Two owner columns would let a later block write the one that no longer means anything,
        // and nothing would fail. V3_2 drops it; this is what keeps it dropped.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_schema = 'rpg'"
                                        + " AND table_name = 'item_instance'")) {
            java.util.List<String> columns = new java.util.ArrayList<>();
            while (rows.next()) {
                columns.add(rows.getString(1));
            }
            assertThat(columns).contains("owner_character_id").doesNotContain("owner_player_id");
        }
    }

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

    private static long itemCount() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM rpg.item_instance")) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }
}

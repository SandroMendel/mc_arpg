package rpg.persistence.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.persistence.support.PostgresContainer;

/**
 * T130: {@code V7_1} legt die Tabelle an - und {@code V3_1} bleibt unangetastet (ADR-019).
 *
 * <p>Der zweite Teil ist der wichtigere. Die **Menge** der Klassen steht im Code: das Enum
 * {@code CharacterClass} und die Bedingung {@code chk_character_class}. Eine vierte Klasse ist ein
 * späteres Upgrade und kostet einen Enum-Wert plus eine eigene Migration - sie ist kein
 * Konfigurationseintrag. Hätte B07 diese Bedingung aufgeweicht, wäre die Zusage von ADR-019 still
 * verloren, und nichts anderes würde es merken.
 */
class ClassProgressMigrationTest {

    private rpg.persistence.support.PersistenceHarness harness;

    /** Die Harness wandert das Schema hoch - dasselbe, was jeder andere Persistenztest hier tut. */
    @org.junit.jupiter.api.BeforeEach
    void migrate() {
        harness = new rpg.persistence.support.PersistenceHarness();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("V7_1 legt rpg.character_class_progress an")
    void themigrationCreatesTheTable() {
        assertThat(PostgresContainer.tableExists("character_class_progress")).isTrue();
    }

    @Test
    @DisplayName("V7_2 legt rpg.character_inventory an - beide Behälter in einer Zeile")
    void theinventoryTableExistsWithBothContainers() {
        assertThat(PostgresContainer.tableExists("character_inventory")).isTrue();
        assertThat(columnsOf("character_inventory"))
                .contains("contents")
                .as("die Enderchest gehört genauso dem Charakter wie der Rucksack")
                .contains("ender_chest");
    }

    @Test
    @DisplayName("die Klasse steht NICHT in der Stufentabelle - sie lebt in rpg.character (ADR-019)")
    void theclassIsNotDuplicated() {
        assertThat(columnsOf("character_class_progress"))
                .as("eine zweite Kopie wäre eine zweite Wahrheit, und die zwei könnten abweichen")
                .doesNotContain("character_class")
                .contains("armor_tier", "weapon_tier");
    }

    @Test
    @DisplayName("chk_character_class aus V3_1 gilt weiter und nennt genau die drei Klassen")
    void thecheckConstraintFromB03IsUntouched() {
        String definition = constraintDefinition("chk_character_class");

        assertThat(definition)
                .as("ohne diese Bedingung entscheidet die Konfiguration über die Klassenmenge")
                .isNotNull()
                .contains("WARRIOR")
                .contains("MAGE")
                .contains("ROGUE");
    }

    @Test
    @DisplayName("die Stufen haben eine untere, aber KEINE obere Grenze")
    void tiersAreBoundedBelowAndNotAbove() {
        // Unten, weil eine Stufe unter 1 kein Balancing ist, sondern ein Fehler. Oben nicht, weil die
        // Leiterlänge aus classes.yml folgt und je Klasse verschieden ist - eine Bedingung
        // CHECK (armor_tier <= 5) wäre heute für zwei von drei Klassen falsch.
        assertThat(constraintDefinition("chk_class_progress_armor")).contains(">=");
        assertThat(constraintDefinition("chk_class_progress_weapon")).contains(">=");
        assertThat(allConstraintsOf("character_class_progress"))
                .as("eine obere Grenze würde nach der nächsten Balancing-Runde Charaktere aussperren")
                .noneMatch(definition -> definition.contains("<="));
    }

    // --- helpers ---

    private static java.util.List<String> columnsOf(String table) {
        return query(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'rpg' AND table_name = '"
                        + table
                        + "'");
    }

    private static java.util.List<String> allConstraintsOf(String table) {
        return query(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c"
                        + " JOIN pg_class t ON t.oid = c.conrelid"
                        + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                        + " WHERE n.nspname = 'rpg' AND t.relname = '"
                        + table
                        + "'");
    }

    private static String constraintDefinition(String name) {
        java.util.List<String> found =
                query(
                        "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                                + " WHERE conname = '"
                                + name
                                + "'");
        return found.isEmpty() ? null : found.get(0);
    }

    private static java.util.List<String> query(String sql) {
        java.util.List<String> values = new java.util.ArrayList<>();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        } catch (Exception failure) {
            throw new AssertionError("could not inspect the schema: " + sql, failure);
        }
        return values;
    }
}

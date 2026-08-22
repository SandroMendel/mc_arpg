package rpg.persistence.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T033: {@code V8_1} legt {@code rpg.character_abilities} an - und was dort <b>nicht</b> steht.
 *
 * <p>Der zweite Teil ist der wichtigere, wie schon bei B07. Drei Dinge fehlen absichtlich, und jedes
 * davon wäre ohne Test still nachrüstbar: der Freischaltzustand (folgt aus dem Level, FR-061), eine
 * obere Rangschranke (folgt aus {@code abilities.yml} und darf sich ändern) und alles Laufzeitliche -
 * Wut, Ladungen, eine laufende Fähigkeit (ADR-025).
 */
class AbilityMigrationTest {

    private PersistenceHarness harness;

    /** Die Harness wandert das Schema hoch - dasselbe, was jeder andere Persistenztest hier tut. */
    @BeforeEach
    void migrate() {
        harness = new PersistenceHarness();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("V8_1 legt rpg.character_abilities mit ihren vier Nutzspalten an")
    void theMigrationCreatesTheTable() {
        assertThat(PostgresContainer.tableExists("character_abilities")).isTrue();
        assertThat(columnsOf("character_abilities"))
                .contains("character_id", "ability_id", "rank", "cooldown_until", "toggle_state");
    }

    @Test
    @DisplayName("der Schlüssel ist (character_id, ability_id) - viele Zeilen je Charakter")
    void theKeyIsCompound() {
        // Der Unterschied zu jedem bisherigen Aggregattyp: dort trägt ein Charakter genau eine Zeile.
        assertThat(constraintDefinition("character_abilities_pkey"))
                .isNotNull()
                .contains("character_id")
                .contains("ability_id");
    }

    @Test
    @DisplayName("der Freischaltzustand steht NICHT in der Tabelle - er folgt aus dem Level")
    void unlockIsNotStored() {
        assertThat(columnsOf("character_abilities"))
                .as("gespeichert wäre er eine zweite Wahrheit, sobald jemand eine Freischaltstufe ändert")
                .doesNotContain("unlocked", "unlock_level", "unlocked_at");
    }

    @Test
    @DisplayName("Laufzeitzustand steht NICHT in der Tabelle - Wut, Ladungen, laufende Fähigkeiten")
    void runtimeStateIsNotStored() {
        assertThat(columnsOf("character_abilities"))
                .as("alle drei folgen aus einem Zeitstempel plus verstrichener Zeit (ADR-025)")
                .doesNotContain("meter", "rage", "charges", "sustained_until");
    }

    @Test
    @DisplayName("der Rang hat eine untere, aber KEINE obere Grenze")
    void rankIsBoundedBelowAndNotAbove() {
        // Unten, weil ein Rang unter 1 kein Balancing ist, sondern ein Fehler. Oben nicht, weil
        // max-rank aus abilities.yml folgt, je Fähigkeit verschieden ist und sich ändern darf -
        // dieselbe Begründung, die B07 für seine Leiterlängen gibt.
        assertThat(constraintDefinition("chk_ability_rank")).contains(">=");
        assertThat(allConstraintsOf("character_abilities"))
                .as("eine obere Grenze würde nach der nächsten Balancing-Runde Charaktere aussperren")
                .noneMatch(definition -> definition.contains("rank <="));
    }

    @Test
    @DisplayName("toggle_state lässt nur die drei Zustände zu, die es gibt")
    void theToggleIsConstrained() {
        String definition = constraintDefinition("chk_ability_toggle");

        assertThat(definition).isNotNull().contains("ON").contains("OFF").contains("PARTIAL");
    }

    @Test
    @DisplayName("das Löschen eines Charakters nimmt seine Fähigkeitszeilen mit")
    void deletingACharacterCascades() {
        // Damit ist die Anonymisierung aus B02 erledigt, ohne dass B02 wissen muss, dass B08 existiert.
        assertThat(allConstraintsOf("character_abilities"))
                .anyMatch(definition -> definition.contains("ON DELETE CASCADE"));
    }

    // --- helpers ---

    private static List<String> columnsOf(String table) {
        return query(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'rpg' AND table_name = '"
                        + table
                        + "'");
    }

    private static List<String> allConstraintsOf(String table) {
        return query(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c"
                        + " JOIN pg_class t ON t.oid = c.conrelid"
                        + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                        + " WHERE n.nspname = 'rpg' AND t.relname = '"
                        + table
                        + "'");
    }

    private static String constraintDefinition(String name) {
        List<String> found =
                query("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '"
                        + name
                        + "'");
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<String> query(String sql) {
        List<String> values = new ArrayList<>();
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

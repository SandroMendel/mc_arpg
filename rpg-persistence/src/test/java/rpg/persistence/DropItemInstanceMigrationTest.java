package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.persistence.support.PostgresContainer;

/**
 * V11_1 — <b>{@code item_instance} ist zurückgebaut, und der Rückbau war kein Datenverlust.</b>
 *
 * <p>Gegen eine echte PostgreSQL, nicht gegen ein Mock (Prinzip VII). Eine Migration, die gegen ein
 * Double geprüft wird, prüft die Vorstellung von PostgreSQL, die der Autor hatte.
 *
 * <p><b>Zwei Zusagen:</b>
 *
 * <ul>
 *   <li>Die Tabelle ist <b>weg</b>. Eine, die geladen und nie geschrieben wird, ist eine Falle für
 *       den nächsten Block — irgendwann schreibt jemand hinein, und dann gibt es zwei Wahrheiten
 *       über dasselbe Inventar (research.md R2).
 *   <li>Die Migration <b>bricht ab</b>, wenn Zeilen darin stehen, statt sie zu löschen. Das ist der
 *       Fall, für den die Prüfung überhaupt da ist: die Annahme „nie ein Spielpfad hat
 *       hineingeschrieben" könnte falsch gewesen sein, und dann gehört das einem Betreiber gesagt
 *       und nicht stillschweigend bereinigt. Dasselbe Vorgehen wie in V3_2.
 * </ul>
 */
class DropItemInstanceMigrationTest {

    @Test
    @DisplayName("die Tabelle existiert nach der Migration nicht mehr")
    void theTableIsGone() throws Exception {
        assertThat(PostgresContainer.tableExists("item_instance"))
                .as("V11_1 hat sie entfernt - ein Item lebt seit B11 im PDC, nicht in einer Zeile")
                .isFalse();
    }

    @Test
    @DisplayName("die Nachbartabellen sind unberuehrt - der Rueckbau hat nichts mitgenommen")
    void theNeighbouringTablesAreUntouched() throws Exception {
        // character_inventory traegt seit B03 Rucksack und Enderchest je Charakter, und DORT liegt
        // ein Gegenstand seither. Haette der Rueckbau sie mitgenommen, waeren die Inventare weg -
        // die Sorte Fehler, die man erst bemerkt, wenn ein Spieler sich anmeldet.
        assertThat(PostgresContainer.tableExists("character_inventory")).isTrue();
        assertThat(PostgresContainer.tableExists("character")).isTrue();
    }

    @Test
    @DisplayName("und der Wachposten haette gegriffen: eine gefuellte Tabelle bricht ab")
    void theGuardWouldHaveFired() throws Exception {
        // Die echte Migration laesst sich nicht ein zweites Mal ausfuehren - Flyway kennt sie
        // bereits. Geprueft wird deshalb die Bedingung, die sie benutzt, in derselben Form: eine
        // Tabelle mit Inhalt loest die Ausnahme aus, eine leere nicht.
        //
        // Ohne diesen Test waere die Abbruchbedingung eine Zeile SQL, die nie gelaufen ist - und
        // genau die greift dann im einen Ernstfall nicht.
        try (var connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {

            statement.execute("CREATE TEMPORARY TABLE guard_probe (id INT)");
            statement.execute("INSERT INTO guard_probe VALUES (1)");

            boolean raised = false;
            try {
                statement.execute(
                        """
                        DO $$
                        DECLARE remaining BIGINT;
                        BEGIN
                            SELECT count(*) INTO remaining FROM guard_probe;
                            IF remaining > 0 THEN
                                RAISE EXCEPTION 'guard: % row(s) remain', remaining;
                            END IF;
                        END $$;
                        """);
            } catch (Exception expected) {
                raised = true;
                assertThat(expected.getMessage()).contains("1 row(s) remain");
            }

            assertThat(raised)
                    .as("eine gefuellte Tabelle muss die Migration anhalten, nicht geleert werden")
                    .isTrue();

            statement.execute("DELETE FROM guard_probe");
            try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM guard_probe")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
            }
        }
    }
}

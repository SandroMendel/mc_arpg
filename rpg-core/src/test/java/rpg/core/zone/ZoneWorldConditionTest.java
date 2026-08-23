package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.WorldCondition;

/**
 * T118 - ohne Instanzen lautet die Antwort ueberall ja, und zwar als Entscheidung (FR-052, FR-052b).
 *
 * <p>Verhalten laesst sich hier fast nicht pruefen: eine Klasse, die {@code true} zurueckgibt, ist
 * mit zwei Zeilen abgedeckt. Der eigentliche Gegenstand ist ein anderer - dass die Antwort begruendet
 * ist und nicht vergessen wurde. Deshalb prueft der letzte Test den Kommentar. Das ist die Ausnahme
 * und kein Muster: sie ist gerechtfertigt, weil der Unterschied zwischen "entschieden" und
 * "uebersehen" hier <em>nur</em> im Kommentar steht.
 */
class ZoneWorldConditionTest {

    private final WorldCondition condition = new ZoneWorldCondition();

    @Test
    @DisplayName("in jeder der sechs Regionen lautet die Antwort ja")
    void yesInEveryRegion() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        assertThat(zones.all()).hasSize(6);
        for (Zone zone : zones.all()) {
            assertThat(condition.isOpenWorld(UUID.randomUUID())).as(zone.key()).isTrue();
        }
    }

    @Test
    @DisplayName("auch in der Wildnis zwischen den Regionen - sie ist offene Welt im klarsten Sinn")
    void yesInTheWildernessToo() {
        // Kein Aufenthalt gesetzt, keine Region bekannt. Waere die Antwort hier nein, faenden
        // Spieler ihre offene-Welt-Faehigkeiten ausgerechnet auf dem Weg zwischen zwei Regionen
        // ausgeschaltet - und niemand wuerde das mit diesem Block in Verbindung bringen.
        assertThat(condition.isOpenWorld(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("die Antwort haengt nicht davon ab, wen man fragt")
    void thesameAnswerForEverybody() {
        for (int i = 0; i < 20; i++) {
            assertThat(condition.isOpenWorld(UUID.randomUUID())).isTrue();
        }
    }

    @Test
    @DisplayName("der Kommentar nennt die Begruendung und die Stelle, die sich aendern wird")
    void thejavadocSaysWhy() throws IOException {
        String source =
                Files.readString(
                        repositoryRoot()
                                .resolve(
                                        "rpg-core/src/main/java/rpg/core/zone/ZoneWorldCondition.java"));

        assertThat(source)
                .as("eine Entscheidung, keine unfertige Umsetzung")
                .contains("decision")
                .contains("no instances");
        assertThat(source)
                .as("und der Weg, wie sie sich aendert")
                .contains("ADR-006")
                .contains("ZonePresence");
    }

    @Test
    @DisplayName("B08s Platzhalter steht nicht mehr im Plugin")
    void theplaceholderIsGone() throws IOException {
        String plugin =
                Files.readString(
                        repositoryRoot()
                                .resolve("rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java"));

        // Ohne diese Zeile waere B09 fertig und die Faehigkeit liefe weiter gegen den Standardwert -
        // dasselbe Verhalten, aber niemand haette hingesehen (ADR-012).
        assertThat(plugin).contains("setWorldCondition(new rpg.core.zone.ZoneWorldCondition())");
        assertThat(plugin)
                .as("und der alte Hinweis 'B09 existiert nicht' ebenso wenig")
                .doesNotContain("B09 owns that distinction and does not exist");
    }

    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}

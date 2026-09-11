package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.currency.BookingReason;

/**
 * T117 - stimmt die Umsetzung mit dem ueberein, was ADR-032 zugesagt hat?
 *
 * <p><b>Das ADR ist bereits geschrieben; hier wird nur abgeglichen.</b> Es erlaubt B09 vier Eingriffe
 * ausserhalb seines Zuschnitts und macht zwei davon - Fenster und Eingabe - ausdruecklich befristet.
 * Eine Befristung, die nur in einem Dokument steht, ueberlebt keine zwei Bloecke: wer B13 baut, liest
 * das ADR nicht, sondern den Quelltext. Also muss der Quelltext sie tragen.
 *
 * <p>Der Test prueft die vier Zusagen der Reihe nach: die zwei Buchungsgruende, den Preis bei dem,
 * der ihn verlangt, die Bindung an den Charakter, und den fehlenden Blocktyp-Vergleich.
 */
class Adr032ConformanceTest {

    private static final Path ROOT = repositoryRoot();

    @Test
    @DisplayName("ADR-032 ist EINGELOEST: Fenster und Eingabe liegen bei B13")
    void thetemporaryPiecesHaveArrived() throws IOException {
        // Dieser Test hiess bis B13 "sind als befristet gekennzeichnet und nennen B13" und suchte
        // die drei Dateien unter rpg/platform/zone. Er hat seinen Gegenstand verloren, weil die
        // Zusage EINGELOEST ist - und ein Test, der eine offene Schuld bewacht, wird beim
        // Begleichen nicht geloescht, sondern umgedreht.
        //
        // BEIDE, nicht nur das Fenster (FR-060): ADR-032 nennt die Eingabe ausdruecklich mit, und
        // ein Fenster ohne seinen Listener waere ein halber Umzug.
        for (String file :
                List.of(
                        "WaypointMenu.java",
                        "WaypointMenuListener.java",
                        "CrystalInteractListener.java")) {
            Path moved =
                    ROOT.resolve("rpg-platform/src/main/java/rpg/platform/ui").resolve(file);
            Path old = ROOT.resolve("rpg-platform/src/main/java/rpg/platform/zone").resolve(file);

            assertThat(Files.exists(moved)).as(file + " liegt bei B13").isTrue();
            assertThat(Files.exists(old)).as(file + " liegt nicht mehr bei B09").isFalse();
            assertThat(Files.readString(moved))
                    .as(file + " nennt weiterhin das ADR, unter dem es hierherkam")
                    .contains("ADR-032");
        }
    }

    @Test
    @DisplayName("die zwei Buchungsgruende sind da, und es sind zwei (FR-050d)")
    void bothBookingReasonsExist() {
        List<String> names = Arrays.stream(BookingReason.values()).map(Enum::name).toList();

        assertThat(names).contains("WAYPOINT_TRAVEL", "WAYPOINT_REFUND");
        assertThat(BookingReason.WAYPOINT_TRAVEL.direction())
                .isEqualTo(BookingReason.Direction.DEBIT);
        assertThat(BookingReason.WAYPOINT_REFUND.direction())
                .isEqualTo(BookingReason.Direction.CREDIT);
    }

    @Test
    @DisplayName("der Eingriff in B08b ist im fremden Quelltext als solcher vermerkt")
    void theInterventionIsMarkedWhereItHappened() throws IOException {
        String text =
                Files.readString(
                        ROOT.resolve("rpg-core/src/main/java/rpg/core/currency/BookingReason.java"));

        // B08b ist abgeschlossen. Wer diese Datei in einem Jahr liest, soll ohne Archaeologie sehen,
        // warum zwei Werte eines anderen Blocks darin stehen.
        assertThat(text).contains("ADR-032");
        assertThat(text).contains("B09");
    }

    @Test
    @DisplayName("die Persistenz haengt am Charakter, nicht am Account (ADR-011)")
    void thestateHangsOnTheCharacter() throws IOException {
        String migration =
                Files.readString(
                        ROOT.resolve(
                                "rpg-persistence/src/main/resources/db/migration/V9_1__character_zone_state.sql"));

        assertThat(migration.toLowerCase(Locale.ROOT))
                .as("Schluessel und Fremdschluessel gehen auf den Charakter")
                .contains("character_id")
                .contains("on delete cascade");
        assertThat(migration.toLowerCase(Locale.ROOT))
                .as("kein player_id irgendwo - das waere die kontogebundene Ablage")
                .doesNotContain("player_id");
    }

    @Test
    @DisplayName("der Preis steht in der Zonenkonfiguration, nicht in currency.yml (ADR-027)")
    void thepriceLivesWithTheZone() throws IOException {
        String zones = Files.readString(ROOT.resolve("rpg-plugin/src/main/resources/zones.yml"));
        String currency =
                Files.readString(ROOT.resolve("rpg-plugin/src/main/resources/currency.yml"));

        assertThat(zones).contains("price:");
        assertThat(currency.toLowerCase(Locale.ROOT)).doesNotContain("waypoint");
    }

    @Test
    @DisplayName("kein Blocktyp wird verglichen - der Kristall ist gebaut, nicht gesetzt")
    void nomaterialDecidesWhetherACrystalIsThere() throws IOException {
        String listener =
                Files.readString(
                        ROOT.resolve(
                                "rpg-platform/src/main/java/rpg/platform/ui/CrystalInteractListener.java"));

        // Das Fenster darf Materialien benutzen - es zeichnet Symbole. Die Erkennung darf es nicht,
        // sonst haengt das Reisen daran, dass niemand den Stein abbaut.
        assertThat(listener)
                .as("die Erkennung laeuft ueber den Index, nicht ueber getType()")
                .doesNotContain("getType()")
                .doesNotContain("Material.");
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

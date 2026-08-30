package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>ADR-028 und ADR-032 sind geschlossen</b> (T126, T127, SC-008).
 *
 * <p>Beide nannten ihr Fenster ausdrücklich <em>befristet</em> und B13 als Ziel. Sie standen zehn
 * Blöcke lang so da — das ist die Sorte Schuld, die niemand einfordert und die deshalb liegen
 * bleibt.
 *
 * <h2>Warum das ein Test ist und keine Durchsicht</h2>
 *
 * <p>Ein Umzug lässt sich rückgängig machen, ohne dass es jemandem auffällt: die naheliegende
 * „Verbesserung" ist, ein kleines Fenster wieder dort zu bauen, wo seine Daten liegen. Der Wächter
 * macht daraus einen roten Test statt einer Entdeckung in sechs Monaten.
 *
 * <p><b>Er sucht im Quellcode, nicht im Bytecode</b>, nach dem Muster von
 * {@code NoRawTypeNameLeftTest} und {@code ConfigOnlyAbilityTest} — und er entfernt vorher die
 * Kommentare. Ohne das wäre jede Erklärung, warum hier kein Fenster mehr steht, selbst ein Treffer.
 */
class NoDisplayCodeLeftTest {

    /**
     * Woran man ein <b>Fenster</b> erkennt.
     *
     * <p><b>{@code ItemStack} steht bewusst nicht dabei.</b> Der erste Entwurf hatte ihn, und der
     * Wächter schlug sofort an — bei {@code CoinPickupListener}, der Coin-Haufen vom Boden
     * aufhebt. Das ist B08bs <em>Mechanik</em> und keine Anzeige: ein Gegenstand in der Welt ist
     * kein Fenster, und wer ihn aufhebt, zeigt niemandem etwas.
     *
     * <p>Ein Marker, der beides nicht unterscheidet, hätte zwei Ausgänge — beide schlecht: den
     * Wächter abschalten oder eine Ausnahmeliste pflegen, die beim nächsten Mal jemand erweitert,
     * ohne nachzudenken. Die Marker unten benennen deshalb <b>Inventar-Fenster</b>, und ein
     * Fenster versteckt sich nicht.
     */
    private static final List<String> DISPLAY_MARKERS =
            List.of(
                    "Inventory ",
                    "InventoryClickEvent",
                    "InventoryCloseEvent",
                    "createInventory",
                    "openInventory",
                    "InventoryHolder");

    @Test
    @DisplayName("T126: in rpg/platform/zone/ liegt kein Anzeigecode mehr")
    void nodisplayCodeLeftInZone() throws IOException {
        // ADR-032 ist damit eingeloest - Fenster UND Eingabe (FR-060).
        assertThat(offenders(Path.of("src/main/java/rpg/platform/zone")))
                .as(
                        "WaypointMenu, WaypointMenuListener und CrystalInteractListener liegen seit"
                                + " B13 in rpg/platform/ui")
                .isEmpty();
    }

    @Test
    @DisplayName("T127: in rpg/platform/currency/ liegt kein Anzeigecode mehr")
    void nodisplayCodeLeftInCurrency() throws IOException {
        // ADR-028 ist damit eingeloest - aber nur die ANZEIGE (FR-061a).
        assertThat(offenders(Path.of("src/main/java/rpg/platform/currency")))
                .as("CurrencyMenu und CurrencyMenuListener liegen seit B13 in rpg/platform/ui")
                .isEmpty();
    }

    @Test
    @DisplayName("T127: die /coins-Schale bleibt ausdruecklich stehen")
    void thecoinsCommandStaysWhereItIs() {
        // FR-061a, und der Grund, aus dem dieser Test die Kommandoschale NICHT sucht: ADR-028 weist
        // Kommandos B14 zu, nicht B13. Eine Eingabegeste ist Praesentation, ein Kommando mit
        // Rechtebaum und Tab-Completion ist es nicht.
        //
        // Der Test steht hier als Aussage und nicht als Suche: er haelt fest, dass die Abwesenheit
        // von CoinsCommand in dieser Pruefung Absicht ist und keine Luecke.
        Path coinsCommand =
                Path.of("../rpg-plugin/src/main/java/rpg/plugin/command/CoinsCommand.java");

        assertThat(Files.exists(coinsCommand))
                .as("/coins wartet weiter auf B14 - B13 sammelt keine Kommandos ein")
                .isTrue();
    }

    @Test
    @DisplayName("die fuenf umgezogenen Klassen liegen wirklich in rpg/platform/ui")
    void thefiveMovedClassesAreInUi() {
        // Die Gegenprobe. Ohne sie waeren beide Tests oben auch dann gruen, wenn jemand die
        // Klassen schlicht GELOESCHT haette - und das ist kein Umzug, sondern ein Verlust.
        for (String moved :
                List.of(
                        "WaypointMenu",
                        "WaypointMenuListener",
                        "CrystalInteractListener",
                        "CurrencyMenu",
                        "CurrencyMenuListener")) {
            assertThat(Files.exists(Path.of("src/main/java/rpg/platform/ui/" + moved + ".java")))
                    .as(moved + " muss in rpg/platform/ui liegen")
                    .isTrue();
        }
    }

    /**
     * Jede Datei des Pakets, die nach dem Entfernen der Kommentare noch einen Marker trägt.
     *
     * <p>Die Kommentare fallen <b>vor</b> der Suche weg — nach dem Muster von
     * {@code NoRawTypeNameLeftTest}. Sonst wäre der Satz „hier liegt kein {@code Inventory} mehr"
     * selbst ein Treffer, und der Wächter verböte seine eigene Begründung.
     */
    private static List<String> offenders(Path packageDir) throws IOException {
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packageDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                for (String marker : DISPLAY_MARKERS) {
                    if (source.contains(marker)) {
                        found.add(file.getFileName() + " enthaelt " + marker);
                    }
                }
            }
        }
        return found;
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}

package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-067 — <b>kein Weg von Spieler zu Spieler.</b>
 *
 * <p>Ein Nicht-Ziel aus {@code 00-vision-scope.md}, und es gilt bisher allein dadurch, dass niemand
 * es gebaut hat. Genau diese Sorte Zusage verfällt still: sie steht in einem Dokument, das beim
 * Programmieren niemand offen hat, und der erste Handelsweg sähe wie eine Bequemlichkeit aus.
 *
 * <p><b>B11 ist der Block, der ihn aus Versehen bauen würde.</b> Er hat ein Händlerfenster, einen
 * Verkauf, einen Kauf und ein Eigentum an liegenden Gegenständen — vier Bausteine, aus denen ein
 * Tauschfenster fast von selbst entsteht. Deshalb steht der Wächter hier und nicht irgendwo sonst.
 *
 * <p><b>Der Händler ist kein Gegenbeispiel.</b> Er kauft aus dem Nichts an und verkauft ins Nichts;
 * die Coins entstehen und verschwinden in B08bs Buchführung. Nichts wandert von einem Spieler zu
 * einem anderen — und genau das ist der Unterschied, den dieser Test benennt.
 */
class NoPlayerToPlayerTradeTest {

    private static final Path PLATFORM_ITEM =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private static final Path CORE_ITEM =
            Path.of("..", "rpg-core", "src", "main", "java", "rpg", "core", "item");

    /**
     * Woran ein Handelsweg zu erkennen wäre.
     *
     * <p>Nicht am Wort „Handel" — das schreibt niemand hin. Sondern daran, dass irgendwo <em>zwei</em>
     * Spieler gleichzeitig im Spiel sind: ein zweites Inventar geöffnet, ein Vanilla-Handelsfenster,
     * ein Gegenstand, der einem anderen gegeben wird.
     */
    private static final List<String> TRADE_ROUTES =
            List.of(
                    "InventoryType.MERCHANT",
                    "openMerchant(",
                    "openTrades(",
                    "getOnlinePlayers()",
                    "getPlayerExact(");

    @Test
    @DisplayName("KEIN Weg, auf dem ein Gegenstand einen anderen Spieler erreicht")
    void nopathTakesAnItemToAnotherPlayer() throws IOException {
        List<String> violations = new ArrayList<>();

        // setRecipes gehoert NICHT in die Liste oben, und das ist der interessante Fall: der
        // Haendler ruft es auf, um Vanillas Handel AUSZUSCHALTEN. Ein leeres Angebot ist das
        // Gegenteil eines Handelswegs - gesucht wird deshalb ein Aufruf mit Inhalt.
        java.util.regex.Pattern nonEmptyRecipes =
                java.util.regex.Pattern.compile(
                        "setRecipes\\((?!\\s*(java\\.util\\.)?List\\.of\\(\\))");

        for (Path source : sources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            for (String route : TRADE_ROUTES) {
                if (code.contains(route)) {
                    violations.add(source.getFileName() + ": " + route);
                }
            }
            if (nonEmptyRecipes.matcher(code).find()) {
                violations.add(source.getFileName() + ": setRecipes mit Inhalt");
            }
        }

        assertThat(violations)
                .as(
                        "ein Nicht-Ziel, das nur gilt, weil es niemand gebaut hat, verfaellt beim"
                                + " ersten Block, der die Bausteine dafuer hat - und das ist dieser")
                .isEmpty();
    }

    @Test
    @DisplayName("und der Haendler bucht gegen die Buchfuehrung, nicht gegen einen anderen Spieler")
    void andthevendorBooksAgainstTheLedgerAndNotAgainstAnotherPlayer() throws IOException {
        String vendor =
                SourceGuard.codeOnly(
                        Files.readString(PLATFORM_ITEM.resolve("VendorListener.java")));

        // Der Unterschied in einer Zeile: der Haendler kennt EINEN Charakter. Ein Tauschweg
        // brauchte zwei, und ein zweiter Charakter waere hier ein zweiter Parameter.
        assertThat(vendor)
                .as("die Gegenseite ist die Buchfuehrung, nicht ein Mitspieler")
                .doesNotContain("otherCharacter")
                .doesNotContain("targetPlayer")
                .doesNotContain("recipient");
    }

    @Test
    @DisplayName("und es gibt kein Crafting-Rezept in diesem Block")
    void andthereIsNoCraftingRecipeHere() throws IOException {
        // Das zweite Nicht-Ziel aus derselben Zeile in 00-vision-scope.md, und aus demselben Grund
        // hier: wer Vorlagen und Materialien in der Hand hat, ist einen Schritt von einem Rezept
        // entfernt.
        List<String> violations = new ArrayList<>();
        for (Path source : sources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            if (code.contains("ShapedRecipe") || code.contains("addRecipe(")) {
                violations.add(source.getFileName().toString());
            }
        }

        assertThat(violations).as("kein Crafting (00-vision-scope.md)").isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static List<Path> sources() throws IOException {
        List<Path> all = new ArrayList<>(sourcesIn(PLATFORM_ITEM));
        all.addAll(sourcesIn(CORE_ITEM));
        return all;
    }

    private static List<Path> sourcesIn(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}

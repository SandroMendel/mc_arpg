package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-010 — <b>jedes Item hat feste Attributwerte.</b> Kein Würfeln, keine Wertebereiche, keine
 * Affixe (ADR-027).
 *
 * <p><b>Warum das einen eigenen Test verdient.</b> Dieses Projekt hat den Roll-Mechanismus am
 * 2026-08-22 abgeschafft — und der Wortlaut „Template-ID <em>und gewürfelte Roll-Werte</em>" stand
 * danach noch sechs Tage in Prinzip IV der Constitution, in drei Fassungen gleichzeitig, bis er beim
 * Planen von B11 auffiel. Eine Zusage, die niemand prüft, überlebt ihre eigene Abschaffung.
 *
 * <p>Der Test sieht deshalb an zwei Stellen nach: die Werte selbst, und der Quelltext, aus dem ein
 * Wurf käme.
 */
class TwoCopiesAreIdenticalTest {

    @Test
    @DisplayName("zwei Exemplare derselben Vorlage tragen dieselben Werte")
    void twoCopiesCarryTheSameNumbers() {
        ItemConfig config = configWith(healing(40.0));

        // Hundert Abfragen. Gaebe es einen Wurf, faellt er hier auf - und zwar mit einer
        // Wahrscheinlichkeit, die kein Zufall mehr erklaert.
        ConsumableEffect first = config.template("potion.test").orElseThrow().effect();
        for (int attempt = 0; attempt < 100; attempt++) {
            ConsumableEffect again = config.template("potion.test").orElseThrow().effect();
            assertThat(again)
                    .as("Abfrage %d liefert etwas anderes - dann wird irgendwo gewuerfelt", attempt)
                    .isEqualTo(first);
        }
    }

    @Test
    @DisplayName("dieselbe Vorlage zweimal gebunden ergibt dasselbe Ergebnis")
    void bindingTheSameTemplateTwiceIsStable() {
        ItemTemplate once = healingTemplate();
        ItemTemplate twice = healingTemplate();

        assertThat(once).isEqualTo(twice);
        assertThat(once.effect().heal()).isEqualTo(twice.effect().heal());
        assertThat(once.sellPrice()).isEqualTo(twice.sellPrice());
    }

    @Test
    @DisplayName("nirgends in diesem Block wird gewuerfelt - ausser bei der Stueckzahl der Beute")
    void nothingRollsExceptTheLootCount() throws IOException {
        // FR-021 nennt die Stueckzahl ausdruecklich als den EINZIGEN Zufall dieses Blocks. Ueberall
        // sonst waere ein Wurf der Roll-Mechanismus unter anderem Namen.
        //
        // Geprueft wird der AUFRUF, nicht der Typ. Der erste Anlauf verbot das Wort "Random"
        // ueberhaupt - und schlug bei LootPlanner an, der einen Generator entgegennimmt und an
        // LootTable weiterreicht, ohne selbst zu wuerfeln. Ihn auf die Ausnahmeliste zu setzen
        // haette die Pruefung stumpf gemacht: dann duerfte er spaeter auch wuerfeln, und niemand
        // saehe es. Wer wuerfeln will, ruft eine dieser Methoden auf.
        List<String> forbidden = List.of(".nextDouble(", ".nextInt(", ".nextLong(", "Math.random");
        List<String> offenders = new ArrayList<>();

        for (Path source : productionSources()) {
            String name = source.getFileName().toString();
            if (name.equals("LootTable.java")) {
                continue; // Die Stueckzahl. Die eine erlaubte Stelle.
            }
            String code = SourceGuard.codeOnly(Files.readString(source));
            for (String call : forbidden) {
                if (code.contains(call)) {
                    offenders.add(name + " calls " + call);
                }
            }
        }

        assertThat(offenders)
                .as(
                        "ein Wurf ausserhalb der Stueckzahl waere der Roll-Mechanismus unter"
                                + " anderem Namen (FR-010, FR-021, ADR-027)")
                .isEmpty();
    }

    private static ItemConfig configWith(ConsumableEffect effect) {
        return new ItemConfig(
                java.util.Map.of("potion.test", template(effect)),
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L, 120L, 400L, 1200L, 3000L)),
                LootTables.empty(),
                java.util.Map.of());
    }

    private static ItemTemplate healingTemplate() {
        return template(healing(40.0));
    }

    private static ConsumableEffect healing(double amount) {
        return ConsumableEffect.healing(amount, Duration.ofSeconds(8));
    }

    private static ItemTemplate template(ConsumableEffect effect) {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                "POTION",
                Rarity.COMMON,
                null,
                null,
                3L,
                null,
                effect,
                null);
    }

    private static List<Path> productionSources() throws IOException {
        Path root = Path.of("src", "main", "java", "rpg", "core", "item");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}

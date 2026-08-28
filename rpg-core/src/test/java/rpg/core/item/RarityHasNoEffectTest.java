package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-014 — <b>Rarität ist ein Etikett und kein Werteträger.</b>
 *
 * <p>ADR-027 hat den Roll-Mechanismus abgeschafft und die acht Stufen behalten, ausdrücklich mit
 * dieser Begründung: <em>„Rarität als Wertträger hätte Wertebereiche zurückgebracht, die
 * Entscheidung 3 gerade abschafft."</em> Ein epischer Trank heilt nicht mehr als ein gewöhnlicher;
 * er ist seltener.
 *
 * <p>Diese Zusage ist leicht gesagt und noch leichter gebrochen. Es genügt <em>eine</em> Methode
 * {@code Rarity.multiplier()}, und ab da hängt an der Skala ein Wert — sie funktioniert weiter, die
 * Tests bleiben grün, und die nächste Anforderung, die einen Aufschlag braucht, findet ihn schon
 * vor. Deshalb wird hier nicht geprüft, ob es <em>stimmt</em>, sondern ob es <b>unmöglich ist, dass
 * es nicht stimmt</b>.
 */
class RarityHasNoEffectTest {

    @Test
    @DisplayName("keine Methode von Rarity liefert eine Zahl")
    void rarityExposesNoNumber() {
        List<String> offenders = new ArrayList<>();

        for (Method method : Rarity.class.getDeclaredMethods()) {
            if (method.isSynthetic() || !method.getReturnType().isPrimitive()) {
                continue;
            }
            Class<?> returned = method.getReturnType();
            boolean numeric =
                    returned == int.class
                            || returned == long.class
                            || returned == double.class
                            || returned == float.class;
            // ordinal() erbt Rarity von Enum und ist eine Ordnung, keine Staerke - deshalb steht
            // sie hier nicht zur Debatte. Was zaehlt, ist, was DIESE Klasse selbst erklaert.
            if (numeric) {
                offenders.add(method.getName() + " returns " + returned.getSimpleName());
            }
        }

        assertThat(offenders)
                .as(
                        "steht hier eine Zahl, haengt an der Raritaet ein Wert - und FR-014 waere"
                                + " eine Behauptung statt einer Zusage (ADR-027)")
                .isEmpty();
    }

    @Test
    @DisplayName("zwei Vorlagen gleicher Wirkung wirken gleich, egal wie selten sie sind")
    void sameEffectDifferentRarityWorksTheSame() {
        ConsumableEffect effect = ConsumableEffect.healing(40.0, Duration.ofSeconds(8));

        ItemTemplate common = template("potion.a", Rarity.COMMON, effect);
        ItemTemplate divine = template("potion.b", Rarity.DIVINE, effect);

        assertThat(common.effect()).isEqualTo(divine.effect());
        assertThat(common.effect().heal()).isEqualTo(divine.effect().heal());
    }

    @Test
    @DisplayName("die Raritaet steht in keiner Formel dieses Blocks")
    void noFormulaReadsTheRarity() throws IOException {
        // Die zweite Art, dieselbe Zusage zu brechen: nicht Rarity eine Zahl geben, sondern
        // anderswo auf sie verzweigen. `if (rarity == EPIC) amount *= 2` waere genauso ein
        // Werttraeger, nur schlechter zu finden.
        List<String> offenders = new ArrayList<>();

        for (Path source : productionSources()) {
            String name = source.getFileName().toString();
            if (name.equals("Rarity.java")
                    || name.equals("ItemTemplate.java")
                    || name.equals("ItemConfigSchema.java")
                    || name.equals("ItemMessageKeys.java")) {
                // Die vier duerfen sie kennen: eine definiert sie, eine traegt sie, eine liest sie
                // aus der Konfiguration, eine macht einen Nachrichtenschluessel daraus.
                continue;
            }
            String code = Files.readString(source);
            if (code.contains("Rarity.")) {
                offenders.add(name + " reads the rarity");
            }
        }

        assertThat(offenders)
                .as("ausserhalb von Definition, Traeger, Schema und Anzeige hat die Raritaet nichts"
                        + " zu suchen")
                .isEmpty();
    }

    private static ItemTemplate template(String key, Rarity rarity, ConsumableEffect effect) {
        return new ItemTemplate(
                key, ItemCategory.CONSUMABLE, "POTION", rarity, null, null, 3L, null, effect, null);
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

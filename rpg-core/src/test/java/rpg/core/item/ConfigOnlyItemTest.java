package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-002 und SC-004 — <b>eine neue Vorlage entsteht rein aus Konfiguration.</b>
 *
 * <p>Nach dem Muster von {@code ConfigOnlyMobTest} und {@code ConfigOnlyAbilityTest}, und aus
 * demselben Grund: die Zusage ist leicht gesagt und ebenso leicht gebrochen. Es genügt <em>eine</em>
 * Verzweigung im Code, die eine Vorlage beim Namen nennt —
 * {@code if (key.equals("potion.stoneskin"))} —, und ab da ist diese eine Vorlage anders als alle
 * anderen. Sie funktioniert weiter, die Tests bleiben grün, und die nächste Vorlage, die dasselbe
 * braucht, bekommt die nächste Verzweigung.
 *
 * <p>Deshalb wird hier nicht geprüft, ob es <em>geht</em>, sondern ob es <b>unmöglich ist, dass es
 * nicht geht</b>: kein Bezeichner einer einzelnen Vorlage steht im Produktivcode.
 */
class ConfigOnlyItemTest {

    private static final Path ROOT = repositoryRoot();

    @Test
    @DisplayName("SC-002 - eine neue Vorlage entsteht aus Konfiguration, ohne eine Zeile Java")
    void aNewTemplateComesFromConfigurationAlone() throws IOException {
        List<String> templateKeys = templateKeysFromConfiguration();
        assertThat(templateKeys)
                .as("ohne konfigurierte Vorlagen prueft dieser Test nichts")
                .isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (Path source : productionSources()) {
            String code = codeOnly(Files.readString(source));
            for (String templateKey : templateKeys) {
                if (code.contains(templateKey)) {
                    offenders.add(source.getFileName() + " names " + templateKey);
                }
            }
        }

        assertThat(offenders)
                .as(
                        "steht hier etwas, kennt der Code DIESE Vorlage - und SC-002 waere eine"
                                + " Behauptung statt einer Zusage")
                .isEmpty();
    }

    @Test
    @DisplayName("auch kein Vanilla-Material steht als Sonderfall im Code")
    void noVanillaMaterialIsSingledOut() throws IOException {
        // Die zweite Art, dieselbe Zusage zu brechen: nicht die Vorlage beim Namen nennen, sondern
        // ihr Material. `if (material == POTION)` waere genauso ein Sonderfall, nur schlechter zu
        // finden - und er faende alle Traenke auf einmal.
        List<String> offenders = new ArrayList<>();

        for (Path source : itemSources()) {
            String code = codeOnly(Files.readString(source));
            for (String material :
                    List.of(
                            "\"POTION\"",
                            "\"SPLASH_POTION\"",
                            "\"NETHERITE_UPGRADE_SMITHING_TEMPLATE\"")) {
                if (code.contains(material)) {
                    offenders.add(source.getFileName() + " names " + material);
                }
            }
        }

        assertThat(offenders)
                .as("welches Material eine Vorlage benutzt, steht in items.yml und sonst nirgends")
                .isEmpty();
    }

    @Test
    @DisplayName("und keine Kategorie ist im Code an eine Vorlage gebunden")
    void noCategoryIsTiedToATemplate() throws IOException {
        // Die dritte Art: `if (key.startsWith("trim."))`. Ein Praefix ist ein Bezeichner mit
        // Auslassungspunkten - und die Kategorie steht bereits an der Vorlage (FR-017).
        List<String> offenders = new ArrayList<>();

        for (Path source : itemSources()) {
            String code = codeOnly(Files.readString(source));
            for (String prefix : List.of("\"trim.", "\"potion.")) {
                if (code.contains(prefix)) {
                    offenders.add(source.getFileName() + " branches on " + prefix);
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    // -------------------------------------------------------------------------------------

    private static List<String> templateKeysFromConfiguration() throws IOException {
        Path file = ROOT.resolve("rpg-plugin/src/main/resources/items.yml");
        List<String> keys = new ArrayList<>();
        boolean inTemplates = false;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("templates:")) {
                inTemplates = true;
                continue;
            }
            if (inTemplates && !line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                break;
            }
            if (inTemplates && line.matches("^ {2}[^ #].*:$")) {
                keys.add(line.trim().replace(":", ""));
            }
        }
        return keys;
    }

    private static List<Path> productionSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : List.of("rpg-core", "rpg-platform", "rpg-plugin", "rpg-persistence")) {
            Path main = ROOT.resolve(module + "/src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        return sources;
    }

    private static List<Path> itemSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : List.of("rpg-core", "rpg-platform")) {
            Path main = ROOT.resolve(module + "/src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> path.toString().contains("item"))
                        .forEach(sources::add);
            }
        }
        return sources;
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-core"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("repository root not found");
        }
        return here;
    }
}

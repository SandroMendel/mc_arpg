package rpg.core.mob;

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
 * SC-001 und FR-003 — die Zusage, um die herum dieser Block gebaut ist: <b>eine neue Mob-Art
 * entsteht rein aus Konfiguration.</b>
 *
 * <p>Nach dem Muster von {@code ConfigOnlyAbilityTest} aus B08, und aus demselben Grund: die Zusage
 * ist leicht gesagt und ebenso leicht gebrochen. Es genügt <em>eine</em> Verzweigung im Code, die
 * eine Art beim Namen nennt — {@code if (kind.equals("greenfields.warden"))} —, und ab da ist diese
 * eine Art anders als alle anderen. Sie funktioniert weiter, die Tests bleiben grün, und die
 * nächste Art, die dasselbe braucht, bekommt die nächste Verzweigung.
 *
 * <p>Deshalb wird hier nicht geprüft, ob es <em>geht</em>, sondern ob es <b>unmöglich ist, dass es
 * nicht geht</b>: kein Bezeichner einer einzelnen Art steht im Produktivcode.
 */
class ConfigOnlyMobTest {

    private static final Path ROOT = repositoryRoot();

    @Test
    @DisplayName("SC-001 - eine neue Art entsteht aus Konfiguration, ohne eine Zeile Java")
    void aNewKindComesFromConfigurationAlone() throws IOException {
        // Der Beweis ist die Abwesenheit von Code: gaebe es irgendwo eine Verzweigung auf einen
        // Artschluessel, waere eine neue Art nicht mehr nur eine Zeile in mobs.yml.
        List<String> offenders = new ArrayList<>();

        List<String> kindKeys = kindKeysFromConfiguration();
        assertThat(kindKeys)
                .as("ohne konfigurierte Arten prueft dieser Test nichts")
                .isNotEmpty();

        for (Path source : productionSources()) {
            String code = codeOnly(Files.readString(source));
            for (String kindKey : kindKeys) {
                if (code.contains(kindKey)) {
                    offenders.add(source.getFileName() + " names " + kindKey);
                }
            }
        }

        assertThat(offenders)
                .as(
                        "steht hier etwas, kennt der Code DIESE Art - und SC-001 waere eine"
                                + " Behauptung statt einer Zusage")
                .isEmpty();
    }

    @Test
    @DisplayName("auch kein Vanilla-Basistyp steht als Sonderfall im Code")
    void noVanillaBaseTypeIsSingledOutInCode() throws IOException {
        // Die zweite Art, dieselbe Zusage zu brechen: nicht die Art beim Namen nennen, sondern ihre
        // Basis. `if (base == ZOMBIE)` waere genauso ein Sonderfall, nur schlechter zu finden.
        List<String> offenders = new ArrayList<>();

        for (Path source : mobSources()) {
            String code = codeOnly(Files.readString(source));
            for (String base : List.of("ZOMBIE", "SKELETON", "SPIDER", "HUSK", "SLIME", "DROWNED", "STRAY")) {
                if (code.contains("\"" + base + "\"")) {
                    offenders.add(source.getFileName() + " names " + base);
                }
            }
        }

        assertThat(offenders).as("welche Basis eine Art hat, entscheidet mobs.yml").isEmpty();
    }

    // --- fixtures ---

    /** Die Artschluessel aus der ausgelieferten Konfiguration - ohne YAML-Parser, per Einrueckung. */
    private static List<String> kindKeysFromConfiguration() throws IOException {
        Path file = ROOT.resolve("rpg-plugin/src/main/resources/mobs.yml");
        List<String> keys = new ArrayList<>();
        boolean inKinds = false;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("kinds:")) {
                inKinds = true;
                continue;
            }
            if (inKinds && !line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                break;
            }
            if (inKinds && line.matches("^ {2}[^ #].*:$")) {
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

    private static List<Path> mobSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : List.of("rpg-core/src/main/java/rpg/core/mob", "rpg-platform/src/main/java/rpg/platform/mob")) {
            Path dir = ROOT.resolve(module);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
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

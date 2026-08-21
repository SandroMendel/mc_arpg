package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T063 und T133 - Zusagen über den Quelltext dieses Blocks, nicht über sein Verhalten.
 *
 * <p>Beides ist nur so prüfbar. Dass `SourceKind.CLASS` unbenutzt bleibt, kann kein Verhaltenstest
 * zeigen: er könnte nur zeigen, dass an einer bestimmten Stelle kein Modifikator ankommt, nicht dass
 * nirgends einer gesetzt wird.
 */
class ClassSourceInvariantsTest {

    private static final String B07_CORE = "/rpg/core/classes/";

    @Test
    @DisplayName("T063: kein Quelltext dieses Blocks belegt SourceKind.CLASS (FR-010a)")
    void sourceKindClassStaysUnused() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            String content = codeOf(source);
            if (content.contains("SourceKind")) {
                violations.add(source.toString().replace('\\', '/') + " mentions SourceKind");
            }
        }

        assertThat(violations)
                .as(
                        "FR-010a: Klassenwerte sind Basiswerte, keine Modifikatoren (research.md R1)."
                                + " Die Modifikatorquelle bleibt für spätere, tatsächlich"
                                + " modifikatorförmige Klasseneffekte frei - dieselbe Zusage, die B06"
                                + " für SourceKind.LEVEL hält (ADR-015).")
                .isEmpty();
    }

    @Test
    @DisplayName("T133: kein Quelltext dieses Blocks erwähnt Bukkit, Paper oder NMS (Prinzip III.1)")
    void coreStaysBukkitFree() throws IOException {
        List<String> forbidden = List.of("org.bukkit", "io.papermc", "net.minecraft", "org.spigotmc");
        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            String content = codeOf(source);
            for (String needle : forbidden) {
                if (content.contains(needle)) {
                    violations.add(source.toString().replace('\\', '/') + " mentions " + needle);
                }
            }
        }

        assertThat(violations)
                .as(
                        "Prinzip III.1: die Regeln müssen ohne laufenden Server prüfbar sein."
                                + " Materialien und Trims reisen als Zeichenketten; erst"
                                + " rpg-platform löst sie auf.")
                .isEmpty();
    }

    @Test
    @DisplayName("kein Quelltext dieses Blocks greift nach Items oder Inventaren (Workflow-Regel 5)")
    void noItemAccess() throws IOException {
        // B11 besitzt Items. B07 benennt Materialien und liefert das Bindungsprädikat - hier endet es.
        List<String> forbidden = List.of("ItemStack", "getInventory", "getEquipment", "ItemMeta");
        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            String content = codeOf(source);
            for (String needle : forbidden) {
                if (content.contains(needle)) {
                    violations.add(source.toString().replace('\\', '/') + " mentions " + needle);
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("kein Quelltext dieses Blocks legt den cost-Block aus (FR-021)")
    void costStaysUninterpreted() throws IOException {
        // Coins, Preise und Materialkosten gehören B11 und B16. Der Block reist durch, nie hindurch.
        List<String> forbidden = List.of("coins", "Coins", "price", "Price");
        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            String content = codeOf(source);
            for (String needle : forbidden) {
                if (content.contains(needle)) {
                    violations.add(source.toString().replace('\\', '/') + " mentions " + needle);
                }
            }
        }

        assertThat(violations)
                .as("Workflow-Regel 5: wer den Kostenblock ausliest, koppelt B07 an B11")
                .isEmpty();
    }

    @Test
    @DisplayName("und die Prüfung hat diesen Block wirklich angesehen")
    void scanCoversTheBlock() throws IOException {
        assertThat(productionSources()).hasSizeGreaterThanOrEqualTo(15);
    }

    /**
     * The source with comments removed.
     *
     * <p>Necessary, not convenient: every one of these invariants is explained in a javadoc that names
     * the very thing it forbids. A scan over raw text would fail on its own justification, and the
     * obvious fix - deleting the explanation - would be the wrong one.
     */
    private static String codeOf(Path source) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        return content.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static List<Path> productionSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path path : javaSources()) {
            String normalised = path.toString().replace('\\', '/');
            if (normalised.contains(B07_CORE) && !normalised.contains("/test/")) {
                sources.add(path);
            }
        }
        return sources;
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("could not find the repository root");
        }
        return candidate;
    }
}

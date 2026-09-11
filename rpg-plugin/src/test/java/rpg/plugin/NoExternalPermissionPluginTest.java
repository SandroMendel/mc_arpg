package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T032 — <b>FR-011 als Test: keine Abhängigkeit zu einem Rechte-Plugin.</b>
 *
 * <p>Der Rechtebaum steht im {@code permissions:}-Block von {@code plugin.yml}, und genau dort
 * bedient ihn <em>jedes</em> Plugin, das Bukkit-Rechte vergibt — LuckPerms also automatisch mit.
 * Eine Bibliothek dafür einzubinden wäre nicht nur überflüssig, sondern ein zweiter
 * {@code libraries:}-Eintrag mit eigenem Klassenlader, und dieses Projekt hat schon einmal einen
 * halben Tag daran verloren, dass grüne Tests nichts über Papers Klassenlader beweisen.
 *
 * <p><b>Der Test prüft eine Abwesenheit</b>, und deshalb prüft er die Orte, an denen sie
 * verschwinden könnte: die Gradle-Abhängigkeiten, den {@code libraries:}-Block und die Importe im
 * Produktivcode. Eine Absichtserklärung ohne Prüfer wäre keine Zusage.
 */
class NoExternalPermissionPluginTest {

    /** Wonach gesucht wird. Kleingeschrieben verglichen. */
    private static final List<String> PERMISSION_PLUGINS =
            List.of(
                    "luckperms",
                    "net.luckperms",
                    "vault",
                    "net.milkbowl.vault",
                    "permissionsex",
                    "groupmanager",
                    "ultrapermissions",
                    "bpermissions");

    @Test
    @DisplayName("die Gradle-Abhaengigkeiten enthalten kein Rechte-Plugin")
    void thebuildFileNamesNoPermissionPlugin() throws IOException {
        String build = readFirstThatExists("build.gradle.kts", "build.gradle");

        assertThat(offendersIn(build))
                .as("FR-011: der Rechtebaum muss ohne fremde Bibliothek bedienbar bleiben")
                .isEmpty();
    }

    @Test
    @DisplayName("der libraries:-Block enthaelt kein Rechte-Plugin")
    void thelibrariesBlockNamesNoPermissionPlugin() throws IOException {
        String pluginYml =
                Files.readString(
                        Path.of("src", "main", "resources", "plugin.yml"), StandardCharsets.UTF_8);

        String libraries = between(pluginYml, "libraries:", "\n\n");

        assertThat(offendersIn(libraries))
                .as("ein Eintrag hier braeuchte einen eigenen Klassenlader")
                .isEmpty();
    }

    @Test
    @DisplayName("kein depend/softdepend auf ein Rechte-Plugin")
    void nodependOnAPermissionPlugin() throws IOException {
        String pluginYml =
                Files.readString(
                        Path.of("src", "main", "resources", "plugin.yml"), StandardCharsets.UTF_8);

        for (String line : pluginYml.split("\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("depend:") || lower.startsWith("softdepend:")) {
                assertThat(offendersIn(lower))
                        .as("auch ein softdepend macht aus einer Wahl eine Voraussetzung")
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("kein Produktivcode importiert ein Rechte-Plugin")
    void noproductionCodeImportsOne() throws IOException {
        try (var sources = Files.walk(Path.of("src", "main", "java"))) {
            List<String> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code =
                                                    Files.readString(path, StandardCharsets.UTF_8);
                                            return code.lines()
                                                    .filter(line -> line.startsWith("import "))
                                                    .anyMatch(
                                                            line -> !offendersIn(line).isEmpty());
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders).isEmpty();
        }
    }

    @Test
    @DisplayName("der Rechtebaum steht wirklich in plugin.yml - die Abwesenheit allein genuegt nicht")
    void thepermissionTreeIsActuallyThere() throws IOException {
        // Ohne diesen Test waeren die vier oben auch dann gruen, wenn es GAR KEINE Rechte gaebe.
        // "Wir haengen an keinem Rechte-Plugin" ist nur dann eine Zusage, wenn der eigene Baum
        // steht (FR-010).
        String pluginYml =
                Files.readString(
                        Path.of("src", "main", "resources", "plugin.yml"), StandardCharsets.UTF_8);

        assertThat(pluginYml).contains("permissions:");
        assertThat(pluginYml)
                .as("jeder Knoten braucht ein ausdrueckliches default")
                .contains("default:");
    }

    private static List<String> offendersIn(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return PERMISSION_PLUGINS.stream().filter(lower::contains).toList();
    }

    private static String readFirstThatExists(String... candidates) throws IOException {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException(
                "keine Build-Datei gefunden - der Test kann nichts beweisen, was er nicht liest");
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        if (from < 0) {
            return "";
        }
        int to = text.indexOf(end, from);
        return to < 0 ? text.substring(from) : text.substring(from, to);
    }
}

package rpg.plugin;

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
 * <b>B13 speichert nichts</b> (T147, FR-013b, SC-011).
 *
 * <p>Kein Schema, keine Tabelle, keine Migration. Der Block lässt sich vollständig entfernen, ohne
 * dass Spielerdaten fehlen.
 *
 * <h2>Warum das eine Zusage ist und kein Zufall</h2>
 *
 * <p>Anzeigen sind nur <b>serverweit</b> abschaltbar (FR-013a) — es gibt keine persönliche
 * Einstellung, also auch nichts, was je Spieler zu speichern wäre. Diese Entscheidung fiel in der
 * Klärung und ist der Grund, aus dem B13 nicht an B02 hängt.
 *
 * <p><b>Die naheliegende Erweiterung wäre „jeder darf seine Sidebar ausschalten".</b> Sie klingt
 * harmlos und kostet ein Schema, eine Migration, einen Ladepfad, einen Schreibpfad und eine Antwort
 * auf die Frage, was beim Charakterwechsel gilt. Wer sie einführt, findet hier den roten Test — und
 * damit die Stelle, an der die Entscheidung neu getroffen werden muss.
 */
class UiPersistsNothingTest {

    private static final Path ROOT = repositoryRoot();

    @Test
    @DisplayName("T147: es gibt keine Flyway-Migration fuer B13")
    void thereIsNoMigrationForThisBlock() throws IOException {
        Path migrations = ROOT.resolve("rpg-persistence/src/main/resources/db/migration");

        List<String> uiMigrations = new ArrayList<>();
        try (Stream<Path> walk = Files.list(migrations)) {
            for (Path file : walk.toList()) {
                String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (name.contains("_ui") || name.contains("hud") || name.contains("_display")) {
                    uiMigrations.add(name);
                }
            }
        }

        assertThat(uiMigrations)
                .as("wer in dieser Liste eine B13-Datei sucht, sucht richtig und findet keine")
                .isEmpty();
    }

    @Test
    @DisplayName("T147: kein B13-Paket enthaelt SQL oder einen java.sql-Typ")
    void nosqlInThisBlock() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (String watched :
                List.of(
                        "rpg-core/src/main/java/rpg/core/ui",
                        "rpg-platform/src/main/java/rpg/platform/ui")) {
            Path dir = ROOT.resolve(watched);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path source : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = codeOnly(Files.readString(source));
                    for (String marker :
                            List.of("java.sql", "SELECT ", "INSERT ", "UPDATE ", "Repository")) {
                        if (code.contains(marker)) {
                            offenders.add(source.getFileName() + ": " + marker);
                        }
                    }
                }
            }
        }

        assertThat(offenders)
                .as("ein Block, der nur zeichnet, speichert nichts (FR-013b)")
                .isEmpty();
    }

    @Test
    @DisplayName("T147: UiModule deklariert keine Persistenz-Abhaengigkeit")
    void themoduleDoesNotDependOnPersistence() throws IOException {
        String module =
                codeOnly(
                        Files.readString(
                                ROOT.resolve(
                                        "rpg-core/src/main/java/rpg/core/ui/UiModule.java")));

        // Es haengt an genau einem Modul, und das ist B08 - fuer die Materialpruefung (FR-032).
        assertThat(module).contains("List.of(\"abilities\")");
        assertThat(module)
                .as("kein persistence, kein session - B13 braucht beim Start keines von beiden")
                .doesNotContain("\"persistence\"")
                .doesNotContain("\"session\"");
    }

    @Test
    @DisplayName("T147: stop() haelt nichts fest")
    void stopHoldsNothing() throws IOException {
        // Ein leeres stop() ist hier die Zusage und nicht die Luecke: es gibt keinen Bestand zu
        // leeren. Die Bossbars und Scoreboards haengen an den Spielerobjekten und gehen mit ihnen;
        // was B13 je Spieler merkt, raeumt der SessionObserver (FR-004c).
        String module =
                Files.readString(ROOT.resolve("rpg-core/src/main/java/rpg/core/ui/UiModule.java"));

        assertThat(module)
                .as("die Zusage steht im Quelltext, nicht nur in der Spec")
                .contains("Ein leeres stop() ist hier die Zusage");
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
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

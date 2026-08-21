package rpg.persistence.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die beiden Startprüfungen von B07 werden wirklich gerufen.
 *
 * <p>Der Grund für einen Quelltextnachweis: die Prüfungen selbst sind reichlich getestet - gegen die
 * Konfiguration und gegen eine echte Datenbank -, aber all diese Tests rufen sie <em>selbst</em> auf.
 * Fällt der Aufruf in {@code ClassesModule.start} weg, bleiben sie grün und der Server startet
 * bereitwillig mit einer Konfiguration, die einem Charakter etwas wegnimmt. Genau die Fehlerklasse, für
 * die ADR-012 geschrieben wurde.
 *
 * <p>Über den Startvorgang statt über den Quelltext wäre schöner, geht hier aber nicht: der Nachweis
 * verlangte einen Charakter mit zu hoher Stufe <em>vor</em> dem Hochlauf und einen Start, der
 * fehlschlägt - und {@code FullBootstrapTest} lebt davon, dass der Start gelingt.
 */
class ClassStartupChecksAreWiredTest {

    private static final Path MODULE =
            Path.of("src/main/java/rpg/persistence/classes/ClassesModule.java");

    @Test
    @DisplayName("die Werte werden gegen die Caps aus ADR-008 geprüft (V13)")
    void theCapCheckIsCalled() {
        assertThat(codeOf(MODULE))
                .as("sonst wäre die Endstufe teils unerreichbar, ohne dass etwas es sagt")
                .contains("validateAgainstCaps(");
    }

    @Test
    @DisplayName("die gespeicherten Stufen werden gegen die Leiterlängen geprüft (V19, FR-024)")
    void theStoredTierCheckIsCalled() {
        String code = codeOf(MODULE);
        assertThat(code)
                .as("sonst würde eine gekürzte Leiter einen Charakter still herabsetzen")
                .contains("validateAgainstStoredTiers(");
        assertThat(code)
                .as("und sie braucht die Zeilen aus der Datenbank, nicht nur die Konfiguration")
                .contains("readAll(");
    }

    @Test
    @DisplayName("beide Prüfungen laufen vor der Registrierung des Dienstes")
    void bothChecksRunBeforeAnythingCanUseTheConfig() {
        // Die Reihenfolge ist der Punkt: eine Prüfung, die nach der Registrierung läuft, kommt zu spät -
        // dann hat schon etwas die Konfiguration in der Hand.
        String code = codeOf(MODULE);
        int caps = code.indexOf("validateAgainstCaps(");
        int storedTiers = code.indexOf("validateAgainstStoredTiers(");
        int serviceRegistration = code.indexOf("registerService(");

        assertThat(caps).isNotNegative();
        assertThat(storedTiers).isNotNegative();
        assertThat(serviceRegistration).isGreaterThan(caps).isGreaterThan(storedTiers);
    }

    /**
     * The source with comments removed.
     *
     * <p>Without this the scan finds its own explanations: the comments here name the very methods
     * being looked for, and a check that passes because of a comment about it proves nothing.
     */
    private static String codeOf(Path path) {
        try {
            String source = Files.readString(path);
            return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        } catch (IOException unreadable) {
            throw new AssertionError("could not read " + path.toAbsolutePath(), unreadable);
        }
    }
}

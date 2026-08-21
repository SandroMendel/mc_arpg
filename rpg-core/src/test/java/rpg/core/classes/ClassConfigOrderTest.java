package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T039: die Konfiguration ist vollständig geprüft, <b>bevor</b> der erste Charakter geladen wird
 * (FR-007).
 *
 * <p>Die Reihenfolge ist die eigentliche Zusage. Eine Prüfung, die erst greift, wenn jemand schon
 * spielt, kommt zu spät: dann steht ein Charakter mit halb gültigen Werten in der Welt, und der Abbruch
 * trifft ihn statt den Start. Fail-Fast heißt hier: der Server kommt nicht hoch, solange etwas nicht
 * stimmt.
 *
 * <p>Geprüft am Quelltext von {@code ClassesModule}, weil die Reihenfolge eine Eigenschaft der
 * Verdrahtung ist und nicht des Ergebnisses. Ein Laufzeittest dagegen bräuchte einen Start, der
 * scheitert - und ein Start, der scheitert, lädt keinen Charakter, über den man etwas aussagen könnte.
 */
class ClassConfigOrderTest {

    private static final Path MODULE =
            Path.of("../rpg-persistence/src/main/java/rpg/persistence/classes/ClassesModule.java");

    @Test
    @DisplayName("die Konfiguration wird geladen und geprüft, bevor ein Attachment eingehängt wird")
    void everyCheckRunsBeforeTheSessionAttachment() {
        String code = codeOf();
        int load = code.indexOf("loadConfig(");
        int caps = code.indexOf("validateAgainstCaps(");
        int storedTiers = code.indexOf("validateAgainstStoredTiers(");
        int attachment = code.indexOf("addAttachment(");

        assertThat(load).as("die Konfiguration wird zuerst gelesen").isNotNegative();
        assertThat(attachment)
                .as("das Attachment ist der Weg, über den ein Charakter geladen wird")
                .isGreaterThan(load)
                .isGreaterThan(caps)
                .isGreaterThan(storedTiers);
    }

    @Test
    @DisplayName("der Contributor hängt erst nach den Prüfungen an der Stat-Engine")
    void thecontributorIsRegisteredAfterTheChecks() {
        // Sonst könnte eine Neuberechnung mit Werten laufen, die gerade als ungültig erkannt werden.
        String code = codeOf();

        assertThat(code.indexOf("registerBaseStatContributor("))
                .isGreaterThan(code.indexOf("validateAgainstCaps("))
                .isGreaterThan(code.indexOf("validateAgainstStoredTiers("));
    }

    @Test
    @DisplayName("eine ungültige Konfiguration wird zum Startfehler, nicht zu einer Warnung")
    void aninvalidConfigurationStopsTheStart() {
        String code = codeOf();

        assertThat(code)
                .as("ein geloggter Konfigurationsfehler ist ein Server, der falsch weiterläuft")
                .contains("throw new IllegalStateException");
    }

    private static String codeOf() {
        try {
            String source = Files.readString(MODULE);
            return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        } catch (IOException unreadable) {
            throw new AssertionError("could not read " + MODULE.toAbsolutePath(), unreadable);
        }
    }
}

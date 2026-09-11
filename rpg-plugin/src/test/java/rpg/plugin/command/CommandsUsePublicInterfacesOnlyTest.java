package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T045 — <b>Kommandos rufen nur öffentliche Blockschnittstellen</b> (FR-007, Prinzip III).
 *
 * <p>Nach dem Muster von B13s {@code UiSeamGuardTest}, und aus demselben Grund: das hier sind
 * <b>Abwesenheiten</b>, und Abwesenheiten verfallen ohne Wächter. Sie brechen nicht, wenn jemand
 * etwas falsch macht, sondern wenn jemand etwas <em>hinzufügt</em> — und der Zusatz sieht für sich
 * genommen richtig aus.
 *
 * <p><b>Warum das gerade hier zählt.</b> Ein Kommando ist die bequemste Stelle im ganzen Projekt,
 * um eine Regel unterzubringen: es hat den Absender, die Argumente und den Zugriff auf alles. Genau
 * deshalb steht in {@code package-info.java}, dass der Maßstab dieser Klassen ist, wie wenig sie
 * enthalten — und deshalb misst dieser Test es nach.
 */
class CommandsUsePublicInterfacesOnlyTest {

    private static final Path COMMANDS = Path.of("src", "main", "java", "rpg", "plugin", "command");

    /**
     * Wege an einer Naht vorbei.
     *
     * <p>Jeder davon ist einzeln erklärbar und in Summe der Grund, warum Blöcke auseinanderfallen:
     * Reflection umgeht die Sichtbarkeit, NMS umgeht die API, und ein direkter Griff in eine
     * Umsetzung umgeht die Schnittstelle, an der die Regel hängt.
     */
    private static final Map<String, String> FORBIDDEN =
            Map.of(
                    "java.lang.reflect", "Reflection umgeht jede Sichtbarkeit",
                    "getDeclaredField", "dasselbe, nur ohne Import",
                    "getDeclaredMethod", "dasselbe, nur ohne Import",
                    "setAccessible", "dasselbe, nur ohne Import",
                    "net.minecraft.", "NMS umgeht die Paper-API (Prinzip III)",
                    "org.bukkit.craftbukkit", "dasselbe in Grün");

    /**
     * Umsetzungen, die ein Kommando nicht anfassen darf — es gibt für jede eine Schnittstelle.
     *
     * <p>{@code Default…} ist die Namenskonvention dieses Projekts für „die eine Umsetzung". Wer
     * sie importiert, hat die Schnittstelle daneben stehen sehen und sich dagegen entschieden.
     */
    private static final List<String> IMPLEMENTATIONS =
            List.of(
                    "DefaultCurrency",
                    "DefaultProgression",
                    "DefaultCombatPipeline",
                    "DefaultEventBus",
                    "DefaultModuleRegistry");

    @Test
    @DisplayName("FR-007: kein Kommando greift an einer Naht vorbei")
    void nocommandBypassesASeam() throws IOException {
        assertThat(COMMANDS)
                .as("ein Quellscan ist nur so viel wert wie der Ort, den er findet")
                .exists();

        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
            FORBIDDEN.forEach(
                    (needle, why) -> {
                        if (code.contains(needle)) {
                            offenders.add(path.getFileName() + ": " + needle + " - " + why);
                        }
                    });
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("FR-007: kein Kommando importiert eine Umsetzung statt ihrer Schnittstelle")
    void nocommandImportsAnImplementation() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
            for (String implementation : IMPLEMENTATIONS) {
                if (code.contains(implementation)) {
                    offenders.add(path.getFileName() + " nennt " + implementation);
                }
            }
        }

        assertThat(offenders)
                .as("es gibt fuer jede eine Schnittstelle - der Import ist eine Entscheidung dagegen")
                .isEmpty();
    }

    @Test
    @DisplayName("Prinzip III: kein Kommando schreibt selbst in die Datenbank")
    void nocommandTalksToTheDatabase() throws IOException {
        // Ein Kommando, das eine Abfrage formuliert, hat die Regel mitgenommen, die ueber der
        // Abfrage steht - und die gehoert dem Block, nicht dem Aufrufweg.
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
            if (code.contains("java.sql")
                    || code.contains("Connection")
                    || code.contains("PreparedStatement")
                    || code.contains("SELECT ")
                    || code.contains("INSERT ")
                    || code.contains("UPDATE ")) {
                offenders.add(path.getFileName().toString());
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("Prinzip I: kein join() im Tick")
    void nocommandBlocksTheTick() throws IOException {
        // Ein Kommando laeuft im Tick. Ein join() auf ein CompletableFuture haelt ihn an, bis die
        // Datenbank antwortet - auf einer leeren Testdatenbank tadellos, bei fuenfzig Spielern ein
        // haengender Server. TickReturn ist der Weg, den es stattdessen gibt.
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
            if (code.contains(".join()") || code.contains(".get()  ")) {
                offenders.add(path.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("das Ergebnis kommt ueber TickReturn zurueck, nicht durch Warten")
                .isEmpty();
    }

    // --- Aufbau ---------------------------------------------------------------

    private static List<Path> sources() throws IOException {
        try (var walk = Files.walk(COMMANDS)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Der Code ohne Kommentare und ohne Zeichenkettenliterale.
     *
     * <p>Dieselbe Vorsichtsmaßnahme wie in {@code NoManualArgumentParsingTest}: die Klassen
     * erklären in ihren Javadocs, was sie <em>nicht</em> tun, und diese Erklärungen nennen die
     * verbotenen Formen beim Namen.
     */
    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        String withoutLineComments = withoutBlockComments.replaceAll("(?m)//.*$", " ");
        return withoutLineComments.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}

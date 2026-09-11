package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T043 — <b>kein {@code args.length}, kein {@code switch (args[0])}, kein {@code args[…]}</b>
 * (FR-001, SC-002).
 *
 * <p>Der Grund steht im Vertrag: ohne diesen Test fängt das nächste Kommando wieder von vorn an.
 * Sechs Kommandos haben es getan, jedes ein bisschen anders, und jedes hat dabei seine eigene
 * Tab-Completion mitgebracht, die von seiner eigenen Prüfung abwich.
 *
 * <p><b>Der Test sieht nur Code, keine Kommentare.</b> Diese Datei und die umgezogenen Klassen
 * beschreiben in ihren Javadocs, was früher hier stand — {@code switch (args[0])} als Zitat ist
 * kein Rückfall. Ohne diese Trennung wäre der Wächter beim ersten erklärenden Satz rot.
 */
class NoManualArgumentParsingTest {

    /** Wo Kommandos wohnen. Alle, nicht nur die sechs umgezogenen. */
    private static final Path COMMAND_PACKAGE =
            Path.of("src", "main", "java", "rpg", "plugin", "command");

    /**
     * Die verbotenen Formen.
     *
     * <p>{@code args.length} und {@code args[…]} decken zusammen alles ab, was man mit einem rohen
     * Zeichenkettenfeld anstellen kann: zählen und hineingreifen. Ein {@code switch} darüber ist
     * schon vom zweiten erfasst.
     */
    private static final List<Pattern> FORBIDDEN =
            List.of(
                    Pattern.compile("\\bargs\\s*\\.\\s*length\\b"),
                    Pattern.compile("\\bargs\\s*\\["));

    @Test
    @DisplayName("SC-002: kein Kommando zerlegt noch ein Zeichenkettenfeld")
    void nocommandParsesARawStringArray() throws IOException {
        assertThat(COMMAND_PACKAGE)
                .as("ein Quellscan ist nur so viel wert wie der Ort, den er findet")
                .exists();

        List<String> offenders = new ArrayList<>();
        try (var sources = Files.walk(COMMAND_PACKAGE)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
                for (Pattern forbidden : FORBIDDEN) {
                    if (forbidden.matcher(code).find()) {
                        offenders.add(path.getFileName() + " enthaelt " + forbidden.pattern());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("Argumente werden deklariert, nicht gezaehlt (FR-001, SC-002)")
                .isEmpty();
    }

    @Test
    @DisplayName("SC-002: kein Kommando bringt seine eigene Tab-Completion mit")
    void nocommandBringsItsOwnTabCompletion() throws IOException {
        // Die andere Haelfte derselben Zusage. Fuenf handgeschriebene onTabComplete gab es, jede
        // in einer anderen Methode als die Pruefung, mit der sie uebereinstimmen sollte - und
        // /stats schlug nur Zeitraeume vor, obwohl auch Spielernamen erlaubt waren.
        List<String> offenders = new ArrayList<>();
        try (var sources = Files.walk(COMMAND_PACKAGE)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
                if (code.contains("onTabComplete") || code.contains("TabCompleter")) {
                    offenders.add(path.getFileName().toString());
                }
            }
        }

        assertThat(offenders)
                .as("Vorschlag und Pruefung kommen aus einem ArgumentType (FR-002)")
                .isEmpty();
    }

    @Test
    @DisplayName("SC-002: kein Kommando ist noch ein CommandExecutor")
    void nocommandIsACommandExecutorAnyMore() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var sources = Files.walk(COMMAND_PACKAGE)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
                if (code.contains("implements CommandExecutor")
                        || code.contains("CommandExecutor,")) {
                    offenders.add(path.getFileName().toString());
                }
            }
        }

        assertThat(offenders)
                .as("registriert wird ueber CommandTree, nicht ueber setExecutor")
                .isEmpty();
    }

    @Test
    @DisplayName("und RpgPlugin verdrahtet kein Kommando mehr von Hand")
    void theplugin_doesNotWireCommandsByHand() throws IOException {
        // Acht setExecutor/setTabCompleter standen ueber den ganzen Start verteilt, jedes mit
        // eigener Null-Pruefung gegen plugin.yml. Zwei davon brachen bei Nichtuebereinstimmung
        // mit `return` ab - und haetten damit auch das uebersprungen, was DANACH kam.
        String code =
                codeOnly(
                        Files.readString(
                                Path.of("src", "main", "java", "rpg", "plugin", "RpgPlugin.java"),
                                StandardCharsets.UTF_8));

        assertThat(code).doesNotContain("setExecutor");
        assertThat(code).doesNotContain("setTabCompleter");
        assertThat(code)
                .as("auch getCommand(...) hat keinen Zweck mehr ohne commands:-Block")
                .doesNotContain("getCommand(");
    }

    /**
     * Der Code ohne Kommentare und ohne Zeichenkettenliterale.
     *
     * <p><b>Beides muss weg</b>, sonst ist dieser Wächter nicht benutzbar: die umgezogenen Klassen
     * erklären in ihren Javadocs, was sie früher taten, und diese Erklärungen enthalten die
     * verbotenen Formen als Zitat.
     */
    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        String withoutLineComments = withoutBlockComments.replaceAll("(?m)//.*$", " ");
        return withoutLineComments.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}

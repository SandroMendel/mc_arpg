package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T052 — <b>ein am Gerüst vorbei registriertes Kommando macht diesen Test rot</b> (FR-034).
 *
 * <h2>Warum ein zweiter Registrierweg schlimmer ist als er aussieht</h2>
 *
 * <p>Er umgeht nicht ein Detail, sondern <em>alles</em>: die eine Rechteprüfung (FR-003), die
 * Vorschlagsfilterung (FR-014), den Konsolenfall (FR-008), die Sperrzeit (FR-032) und die
 * Fehlermeldungen (FR-004). Ein Kommando, das direkt bei Brigadier anklopft, funktioniert dabei
 * tadellos — es ist nur keins von diesen hier.
 *
 * <p>Und es fällt niemandem auf: im Spiel sieht es aus wie die anderen. Genau deshalb braucht die
 * Zusage einen Wächter und keine Verabredung.
 */
class CommandsRegisteredThroughFrameworkTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /** Die einzige Datei, die bei Brigadier anklopfen darf. */
    private static final String THE_ONE_PLACE = "CommandTree.java";

    @Test
    @DisplayName("FR-034: nur CommandTree spricht mit dem LifecycleEventManager")
    void onlyCommandTreeTalksToTheLifecycle() throws IOException {
        List<String> offenders = filesContaining("LifecycleEvents.COMMANDS");

        assertThat(offenders)
                .as("ein zweiter Registrierweg umgeht Recht, Vorschlaege, Konsole und Sperrzeit")
                .containsExactly(THE_ONE_PLACE);
    }

    @Test
    @DisplayName("FR-034: nur CommandTree ruft Commands.register")
    void onlyCommandTreeRegisters() throws IOException {
        assertThat(filesContaining("registrar.register")).containsExactly(THE_ONE_PLACE);
        assertThat(filesContaining("Commands.literal"))
                .as("wer einen Literalknoten baut, baut einen Baum - und davon gibt es einen")
                .containsExactly(THE_ONE_PLACE);
    }

    @Test
    @DisplayName("FR-003: nur CommandTree prueft ein Recht fuer einen Knoten")
    void onlyCommandTreeChecksNodePermissions() throws IOException {
        // CommandPermissions.allows ist die eine Pruefstelle; CommandTree ist ihr einziger
        // Aufrufer. Ein Kommando, das selbst hasPermission ruft, hat die Pruefung an sich
        // gezogen - und kann sie beim naechsten Zweig vergessen.
        //
        // ZWEI BENANNTE AUSNAHMEN, beide mit Grund:
        //
        // CoinsCommand hat zwei Rechte an EINEM Knoten - das Grundrecht an der Wurzel, das
        // Admin-Recht fuer ein fremdes Fenster - und Brigadier kennt nur eines je Knoten. Die
        // zweite Pruefung steht deshalb im Kommando, sichtbar und begruendet.
        //
        // RpgPlugin prueft rpg.admin.no-class, und das ist ueberhaupt kein Kommandorecht: es ist
        // der Zugang zur Welt OHNE Klassenwahl, gefragt beim Anmelden. Es steht hier, weil eine
        // Berechtigung Paper-Sache ist und der Listener die Antwort bekommen soll, nicht die
        // Frage stellen muessen.
        // CommandTree steht NICHT in dieser Liste, und das ist kein Versehen: es ruft
        // CommandPermissions.allows(...) und nicht hasPermission selbst. Die eine Pruefstelle ist
        // damit wirklich eine - selbst der Baum geht durch sie hindurch.
        List<String> allowed =
                List.of("CommandPermissions.java", "CoinsCommand.java", "RpgPlugin.java");

        assertThat(filesContaining("hasPermission"))
                .as("wer hier neu auftaucht, hat die Pruefung an sich gezogen")
                .containsExactlyInAnyOrderElementsOf(allowed);

        assertThat(filesContaining("CommandPermissions.allows"))
                .as("und der Baum fragt sie, statt selbst zu pruefen")
                .containsExactly("CommandTree.java");
    }

    @Test
    @DisplayName("die Kommandos kommen ALLE aus einer Sammelstelle in RpgPlugin")
    void everyCommandGoesThroughTheOneCollector() throws IOException {
        // Die beiden Sammler liefern die Spielerkommandos und die Admin-Gruppen; erst
        // registerDeclaredCommands() komponiert daraus den einen Baum. Wer einen anderen Weg
        // nimmt, umgeht die Stelle, an der /rpg dazugehaengt wird.
        String plugin =
                Files.readString(SOURCES.resolve("rpg/plugin/RpgPlugin.java"), StandardCharsets.UTF_8);

        long collected = plugin.split("registerCommand\\(", -1).length - 1;
        long collectedAdmin = plugin.split("registerAdminCommand\\(", -1).length - 1;

        assertThat(collected)
                .as("sechs Spielerkommandos plus die Methodendeklaration selbst")
                .isEqualTo(7);
        assertThat(collectedAdmin)
                .as("sechs Admin-Gruppen plus die Methodendeklaration selbst")
                .isEqualTo(7);
    }

    // --- Aufbau ---------------------------------------------------------------

    /** Alle Produktivdateien, deren <b>Code</b> diesen Text enthält. */
    private static List<String> filesContaining(String needle) throws IOException {
        List<String> hits = new ArrayList<>();
        try (var sources = Files.walk(SOURCES)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(path, StandardCharsets.UTF_8));
                if (code.contains(needle)) {
                    hits.add(path.getFileName().toString());
                }
            }
        }
        return hits;
    }

    /** Ohne Kommentare und Zeichenkettenliterale — sonst zählt jede Erklärung als Verstoß. */
    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        String withoutLineComments = withoutBlockComments.replaceAll("(?m)//.*$", " ");
        return withoutLineComments.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}

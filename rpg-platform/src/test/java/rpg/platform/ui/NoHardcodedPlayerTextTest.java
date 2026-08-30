package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Kein Spielertext steht im Code</b> (FR-014, FR-015, SC-002).
 *
 * <p>Nach dem Muster von {@code ConfigOnlyAbilityTest} und {@code NoRawTypeNameLeftTest}. Er ist die
 * <em>maschinelle</em> Hälfte einer Zusage, die sonst nur ein Vorsatz wäre — und ohne ihn wäre die
 * zweite Sprache eine Behauptung: was im Code steht, übersetzt niemand.
 *
 * <h2>Was er sucht</h2>
 *
 * <p>Zeichenketten, die an einen Spieler gehen: {@code sendMessage}, {@code sendActionBar},
 * {@code displayName}, {@code lore}, {@code Component.text} — jeweils mit einem <b>Literal</b>
 * dahinter statt einem Schlüssel.
 *
 * <h2>Was er nicht kann</h2>
 *
 * <p><b>Er sieht keinen Schlüssel, der benutzt, aber nirgends deklariert ist.</b> Das prüft die
 * Startprüfung über {@code UiMessageKeys.all()} — und auch die nur für Schlüssel, die jemand
 * angemeldet hat. Ein Schlüssel, den ein Aufrufer sich selbst zusammenbaut und den {@code all()}
 * nicht kennt, fällt durch beide Netze und erst dem Spieler auf.
 *
 * <p><b>Er sieht keine Zeichenkette, die über eine Variable geht.</b>
 * {@code String greeting = "Hallo"; player.sendMessage(greeting);} kommt durch. Das ist die Grenze
 * einer Textsuche, und sie ist hier benannt statt verschwiegen — ein Wächter, dessen Lücken man
 * kennt, ist mehr wert als einer, dem man alles zutraut.
 *
 * <h2>Kommentare fallen vor der Suche weg</h2>
 *
 * <p>Nach dem Muster von {@code NoRawTypeNameLeftTest}: eine Sache zu <em>erklären</em> ist erlaubt,
 * sie zu <em>rufen</em> nicht. Ohne das wäre dieser Klassenkommentar selbst ein Treffer.
 */
class NoHardcodedPlayerTextTest {

    private static final Path ROOT = repositoryRoot();

    /**
     * Die Pakete, die dieser Block gebaut hat.
     *
     * <p><b>Nicht der ganze Baum</b> (FR-015 sagt „Produktivcode", die Spec meint diesen Block):
     * B01 bis B12 sind abgeschlossen und abgenommen, und ein Wächter, der rückwirkend zwölf Blöcke
     * prüft, findet Fundstellen, die niemand in diesem Block verantwortet — und wird deshalb beim
     * ersten Treffer abgeschaltet statt behoben.
     *
     * <p>Was er hier findet, gehört B13, und B13 kann es beheben.
     */
    private static final List<String> WATCHED =
            List.of(
                    "rpg-core/src/main/java/rpg/core/ui",
                    "rpg-platform/src/main/java/rpg/platform/ui");

    /**
     * Wo ein Literal in einer Spielerausgabe stehen darf.
     *
     * <p><b>Leer, und das ist die Zusage.</b> Eine Ausnahmeliste ist der Weg, auf dem ein Wächter
     * über Jahre nutzlos wird: der erste Eintrag ist begründet, der fünfte ist Gewohnheit. Kommt je
     * einer dazu, muss er hier stehen <em>und</em> einen Grund tragen.
     */
    private static final List<String> ALLOWED = List.of();

    /**
     * Aufrufe, die beim Spieler landen — mit einem Literal als erstem Argument.
     *
     * <p>{@code Component.empty()} und {@code Component.text(variable)} sind nicht betroffen: gesucht
     * wird ausdrücklich das Anführungszeichen.
     */
    private static final Pattern PLAYER_TEXT =
            Pattern.compile(
                    "(sendMessage|sendActionBar|displayName|customName|Component\\.text|"
                            + "PlainTextComponentSerializer[^;]{0,60}deserialize)\\s*\\(\\s*\"");

    @Test
    @DisplayName("T137: in B13s Paketen steht kein Spielertext im Code")
    void nohardcodedPlayerTextInThisBlock() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : watchedSources()) {
            if (ALLOWED.contains(source.getFileName().toString())) {
                continue;
            }
            String code = codeOnly(Files.readString(source));
            Matcher matcher = PLAYER_TEXT.matcher(code);
            while (matcher.find()) {
                offenders.add(source.getFileName() + ": " + matcher.group(1));
            }
        }

        assertThat(offenders)
                .as(
                        "jeder Spielertext laeuft ueber einen Message-Schluessel (FR-014) - was im"
                                + " Code steht, uebersetzt niemand")
                .isEmpty();
    }

    @Test
    @DisplayName("T139: die Ausnahmeliste ist leer - und wenn nicht, zeigt sie auf echte Dateien")
    void theallowedListPointsAtRealFiles() throws IOException {
        // Ein Waechter ohne Ziel bewacht nichts: ein Tippfehler in der Liste waere eine Ausnahme,
        // die nie greift, und eine umbenannte Datei faellt still heraus. Dieselbe Selbstpruefung
        // wie in NoRawTypeNameLeftTest.
        List<String> names = new ArrayList<>();
        for (Path source : watchedSources()) {
            names.add(source.getFileName().toString());
        }

        assertThat(names).containsAll(ALLOWED);
    }

    @Test
    @DisplayName("T137: der Waechter sieht ueberhaupt etwas - er laeuft nicht ins Leere")
    void theguardActuallyLooksAtSomething() throws IOException {
        // Ohne das waere er gruen, weil er nichts findet - und niemand merkte es, wenn die Pfade in
        // WATCHED einmal nicht mehr stimmen. Genau der Fehler, den B10 bei HordeSweeps hatte: ein
        // Aufraeumen, das nie lief.
        assertThat(watchedSources())
                .as("die Pfade in WATCHED muessen auf echte Pakete zeigen")
                .hasSizeGreaterThan(15);
    }

    @Test
    @DisplayName("T140: der Waechter findet einen Verstoss, wenn es einen gibt")
    void theguardWouldCatchAViolation() {
        // Die Gegenprobe. Ein Waechter, der nie anschlaegt, ist von einem, der nicht funktioniert,
        // nicht zu unterscheiden - und dieser hier ist gruen, weil der Block sauber ist.
        String violation = "player.sendMessage(\"Willkommen!\");";

        assertThat(PLAYER_TEXT.matcher(violation).find()).isTrue();
    }

    @Test
    @DisplayName("T140: und er schlaegt NICHT bei einem Schluessel an")
    void theguardIgnoresAKey() {
        String correct = "player.sendMessage(messages.get(UiMessageKeys.SHEET_NO_CHARACTER, values));";

        assertThat(PLAYER_TEXT.matcher(correct).find()).isFalse();
    }

    @Test
    @DisplayName("T138: Kommentare fallen vor der Suche weg")
    void commentsAreStrippedFirst() {
        // Sonst waere jede Erklaerung, warum hier kein Text steht, selbst ein Treffer - und der
        // Waechter verboete seine eigene Begruendung.
        String withComment = "// player.sendMessage(\"nur ein Beispiel\");\nint x = 1;";

        assertThat(PLAYER_TEXT.matcher(codeOnly(withComment)).find()).isFalse();
    }

    // --- Aufbau ---------------------------------------------------------------

    private static List<Path> watchedSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String watched : WATCHED) {
            Path dir = ROOT.resolve(watched);
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

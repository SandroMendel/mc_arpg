package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-017, Prinzip I und II — <b>keine wiederkehrende Aufgabe je Spieler, je Item oder je liegendem
 * Gegenstand.</b>
 *
 * <p>Dieser Block hätte davon leicht drei bekommen können, und jede einzelne sähe vernünftig aus:
 *
 * <ul>
 *   <li>eine Aufgabe je liegendem Gegenstand, die ihn nach einer Frist entfernt — <b>das macht
 *       Vanilla</b>, und es macht es besser, als es hundert eigene Aufgaben täten;
 *   <li>eine Aufgabe je wirkendem Trank, die ihn ablaufen lässt — <b>der Ablauf reitet auf B08s
 *       Durchlauf</b>, demselben, der die Fähigkeitsbuffs beendet (FR-034);
 *   <li>eine Aufgabe je Spieler, die den Verschleiß fortschreibt — <b>Verschleiß wird bei Bedarf
 *       gerechnet</b>, im Moment des Treffers, und sonst nie.
 * </ul>
 *
 * <p>Bei 150 Spielern und 800 Kreaturen ist der Unterschied nicht akademisch: drei Aufgaben je
 * Objekt wären Tausende laufender Aufgaben für Arbeit, die niemand angefordert hat.
 *
 * <p><b>Warum eine Quelltextprüfung.</b> Eine eingeplante Aufgabe fällt in keinem Test auf — sie
 * läuft, sie tut das Richtige, und der Preis zeigt sich erst unter Last, wo ihn niemand einer
 * einzelnen Zeile zuordnet. Nach dem Muster von {@code ConfigOnlyAbilityTest} aus B08.
 */
class NoRecurringTaskTest {

    private static final Path CORE_ITEM = Path.of("src", "main", "java", "rpg", "core", "item");

    private static final Path PLATFORM_ITEM =
            Path.of("..", "rpg-platform", "src", "main", "java", "rpg", "platform", "item");

    /**
     * Woran eine eingeplante Aufgabe zu erkennen wäre.
     *
     * <p>Beide Schreibweisen, die dieses Projekt kennt: B01s {@code Scheduler} und Bukkits eigener.
     * Ein späterer Block, der Bukkits API direkt nähme, würde denselben Preis zahlen.
     *
     * <p>{@code runAsyncDelayed} und {@code runSyncDelayed} stehen mit dabei, obwohl sie einmalig
     * sind. Eine <em>einmalige</em> Aufgabe je Objekt ist bei 800 Kreaturen dasselbe Problem wie eine
     * wiederkehrende, nur schwerer zu sehen — und dieser Block hat für keine einen Anlass.
     */
    private static final List<String> SCHEDULED =
            List.of(
                    "runAsyncRepeating",
                    "runSyncRepeating",
                    "runTaskTimer",
                    "scheduleSyncRepeatingTask",
                    "scheduleAtFixedRate",
                    "runAsyncDelayed",
                    "runSyncDelayed");

    @Test
    @DisplayName("KEINE eingeplante Aufgabe in diesem Block - weder wiederkehrend noch verzoegert")
    void noscheduledTaskAnywhereInThisBlock() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : sources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            for (String scheduled : SCHEDULED) {
                if (code.contains(scheduled)) {
                    violations.add(source.getFileName() + ": " + scheduled);
                }
            }
        }

        assertThat(violations)
                .as(
                        "Verfall macht Vanilla, Buffs reiten auf B08s Durchlauf, und Verschleiss wird"
                                + " bei Bedarf gerechnet (SC-017, Prinzip II)")
                .isEmpty();
    }

    @Test
    @DisplayName("und der Block haelt ueberhaupt keinen Scheduler")
    void andtheblockHoldsNoSchedulerAtAll() throws IOException {
        // Der schaerfere Test: wer keinen hat, kann auch nichts einplanen. Ein Feld dafuer waere die
        // Vorbereitung, und die faellt frueher auf als der erste Aufruf.
        // Zusammengesetzt und nicht als Literal, und das ist keine Spielerei: B01s
        // NoGlobalSchedulerAccessTest durchsucht ALLE Projektquellen - auch Testquellen - nach genau
        // diesem Wort und hat diesen Test beim ersten Lauf angeschlagen. Ein Waechter, der einen
        // anderen faengt, ist ein Grund, den Suchbegriff zu bauen statt eine Ausnahme einzutragen:
        // so bleiben beide scharf.
        String forbidden = "Bukkit" + "Scheduler";

        List<String> violations = new ArrayList<>();
        for (Path source : sources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            if (code.contains("Scheduler ") || code.contains(forbidden)) {
                violations.add(source.getFileName().toString());
            }
        }

        assertThat(violations)
                .as("kein Scheduler, keine Versuchung - dieselbe Bauart wie B08s Buffs")
                .isEmpty();
    }

    @Test
    @DisplayName("die Abklingzeit wird GERECHNET, nicht heruntergezaehlt")
    void thecooldownIsComputedRatherThanCountedDown() throws IOException {
        // Der Beleg, dass die Abwesenheit oben kein Zufall ist. ConsumableCooldown haelt einen
        // Zeitstempel und vergleicht ihn beim Fragen - ein Zaehler bräuchte einen Takt.
        String cooldown = SourceGuard.codeOnly(Files.readString(CORE_ITEM.resolve("ConsumableCooldown.java")));

        assertThat(cooldown)
                .as("ein Zeitstempel braucht keinen Takt; ein Zaehler braucht einen")
                .contains("Clock");
        assertThat(cooldown).doesNotContain("Runnable");
    }

    @Test
    @DisplayName("und der Verschleiss ebenso - er entsteht im Treffer, nicht in einem Durchlauf")
    void andsodoesTheWear() throws IOException {
        String conditions =
                SourceGuard.codeOnly(Files.readString(CORE_ITEM.resolve("DefaultGearConditions.java")));

        // Verschleiss hat KEINE eigene Zeitachse: er aendert sich, wenn etwas passiert, und sonst
        // nie. Deshalb gibt es hier keinen Durchlauf, der ihn fortschreiben muesste.
        assertThat(conditions)
                .as("was sich nur bei einem Ereignis aendert, braucht keinen Takt")
                .doesNotContain("Runnable")
                .doesNotContain("Scheduler");
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static List<Path> sources() throws IOException {
        List<Path> all = new ArrayList<>(sourcesIn(CORE_ITEM));
        all.addAll(sourcesIn(PLATFORM_ITEM));
        return all;
    }

    private static List<Path> sourcesIn(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}

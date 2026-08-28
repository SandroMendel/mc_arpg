package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-079 — <b>der wahrscheinlichste Verstoß dieses Blocks, und er sähe nie wie einer aus.</b>
 *
 * <p>Die Hälfte des ursprünglichen Blockumfangs war bereits gebaut, als B11 anfing: Bindung,
 * Aussehen, Inventar, Stufenkauf, Eigentum an liegenden Gegenständen. Jedes Mal wäre die eigene
 * Fassung <em>schneller</em> gewesen als die fremde zu verstehen — und jedes Mal hätte sie sich beim
 * nächsten Balancing anders verhalten als das Original, ohne dass irgendwo etwas rot geworden wäre.
 *
 * <p><b>Deshalb liest dieser Test die Quellen und nicht das Verhalten.</b> Ein Verhaltenstest kann
 * zeigen, dass eine Prüfung funktioniert; er kann nicht zeigen, dass es sie nur <em>einmal</em> gibt.
 * Nach dem Muster von {@code NoRawTypeNameLeftTest} aus B10, das aus demselben Grund existiert.
 *
 * <p><b>Und er prüft beide Richtungen.</b> Dass die fremden Nähte nicht nachgebaut wurden, und dass
 * sie tatsächlich benutzt werden — ein Block, der die eine Fassung meidet <em>und</em> keine zweite
 * hat, hätte die Aufgabe gar nicht gelöst.
 */
class NoSecondVersionTest {

    private static final Path PLATFORM_ITEM =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private static final Path CORE_ITEM =
            Path.of("..", "rpg-core", "src", "main", "java", "rpg", "core", "item");

    /**
     * Was nicht nachgebaut werden darf, und woran ein Nachbau zu erkennen wäre.
     *
     * <p><b>Gesucht wird nach dem, was ein <em>eigener</em> Bau brauchte — nicht nach dem Namen der
     * fremden Klasse.</b> Wer {@code BoundEquipment} benutzt, nennt sie; wer sie nachbaut, nennt sie
     * gerade nicht.
     *
     * <p>Deshalb steht {@code Currency} hier NICHT: sie zu rufen ist der richtige Weg, nicht der
     * falsche. Verboten ist, was B08bs Buchfuehrung <em>ersetzen</em> wuerde — eine eigene
     * Kontozeile oder ein selbst zusammengesetzter Buchungsgrund. Und {@code new TierAppearance(}
     * steht auch nicht hier: das Ergebnis der Kosmetik IST eine, gebaut aus B07s eigenem Typ.
     */
    private static final Map<String, List<String>> FORBIDDEN =
            Map.of(
                    "eine zweite Bindungspruefung (BoundEquipment gehoert B07)",
                    List.of("class_bound", "BoundEquipment.tagFor("),
                    "eine zweite Quelle fuer Stufenwerte (EquipmentTier gehoert B07)",
                    List.of("EquipmentTier"),
                    "eine zweite Lagerung (CharacterInventory gehoert B03)",
                    List.of("CharacterInventory", "getEnderChest()"),
                    "ein zweiter Stufenaufstieg (TierAdvance gehoert B07)",
                    List.of("TierAdvance", "new EquipmentPurchase("),
                    "eine zweite Eigentumsmechanik (sie gehoert rpg.platform.drop)",
                    List.of("CoinPileRegistry", "CoinPileTag"),
                    "eine eigene Kontofuehrung (der Kontostand gehoert B08b)",
                    List.of("new CharacterBalance(", "BookingReason.valueOf("));

    @Test
    @DisplayName("KEINE zweite Fassung von irgendetwas, das schon existiert")
    void nosecondVersionOfAnythingThatAlreadyExists() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : sources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            FORBIDDEN.forEach(
                    (what, needles) -> {
                        for (String needle : needles) {
                            if (code.contains(needle)) {
                                violations.add(source.getFileName() + ": " + what + " ('" + needle + "')");
                            }
                        }
                    });
        }

        assertThat(violations)
                .as(
                        "jede zweite Fassung waere schneller gewesen als die erste zu verstehen - und"
                                + " haette sich beim naechsten Balancing anders verhalten (FR-079)")
                .isEmpty();
    }

    @Test
    @DisplayName("und die VORHANDENEN Nahtstellen werden wirklich benutzt")
    void andtheExistingSeamsAreActuallyUsed() throws IOException {
        String all = allCode();

        // Die Gegenprobe. Ein Block, der die fremden Fassungen meidet UND keine eigene hat, haette
        // die Aufgabe gar nicht geloest - er haette sie umgangen.
        assertThat(all)
                .as("die Bindung wird gelesen, nicht entschieden (FR-063)")
                .contains("BoundItemTag.tagOf(");
        assertThat(all)
                .as("der Stufenaufstieg geht durch B08bs Ergebnistyp (FR-061)")
                .contains("EquipmentPurchase.Result");
        assertThat(all)
                .as("das Eigentum an liegender Beute kommt aus rpg.platform.drop (ADR-039)")
                .contains("OwnedDrops");
        assertThat(all)
                .as("das Aussehen geht durch B07s Applier statt daran vorbei (FR-070)")
                .contains("ClassEquipmentApplier.AppearanceOverride");
    }

    @Test
    @DisplayName("und der Kern bleibt bukkit-frei (Prinzip III)")
    void andthecoreStaysBukkitFree() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : sourcesIn(CORE_ITEM)) {
            if (SourceGuard.codeOnly(Files.readString(source)).contains("org.bukkit")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("eine Bukkit-Zeile in rpg-core waere eine Regel, die nur mit Server pruefbar ist")
                .isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static String allCode() throws IOException {
        StringBuilder joined = new StringBuilder();
        for (Path source : sources()) {
            joined.append(SourceGuard.codeOnly(Files.readString(source)));
        }
        return joined.toString();
    }

    private static List<Path> sources() throws IOException {
        List<Path> all = new ArrayList<>(sourcesIn(PLATFORM_ITEM));
        all.addAll(sourcesIn(CORE_ITEM));
        return all;
    }

    private static List<Path> sourcesIn(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}

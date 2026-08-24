package rpg.platform.mob;

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
 * Kein Produktivcode führt mehr den Vanilla-Typnamen in eine der drei Schnittstellen.
 *
 * <p><b>Warum dieser Wächter existiert.</b> Die Verwechslung, wegen der B10 den Schlüsselwechsel
 * überhaupt vorgenommen hat, war keine Designfrage, sondern vier Zeilen: {@code
 * creature.getType().name()} in {@code CoinDropListener}, {@code ProgressionDeathListener}, {@code
 * MobEquipmentListener} und {@code MobNameplate}. Mit acht Arten je Region auf wenigen
 * Vanilla-Entities gaben alle vier für vier verschiedene Kreaturen dieselbe Antwort — dieselben
 * Werte, dieselbe Erfahrung, dieselben Coins.
 *
 * <p>Sie sind umgestellt. <b>Aber nichts hindert den nächsten Listener daran, es wieder so zu
 * machen</b>, und es würde niemandem auffallen: der Code läuft, die Tests bleiben grün, und der
 * Fehler zeigt sich als „alle Mobs geben gleich viel", was nach Balancing aussieht und nicht nach
 * einem Bug.
 *
 * <p>Nach dem Muster von {@code ConfigOnlyAbilityTest}, das SC-001 in B08 genauso maschinell
 * absichert: gesucht wird im Code, nicht in Kommentaren — eine Sache zu erklären ist erlaubt, sie zu
 * rufen nicht.
 */
class NoRawTypeNameLeftTest {

    private static final Path ROOT = repositoryRoot();

    /**
     * Wo der Vanilla-Typname noch stehen darf.
     *
     * <p>{@code MobKindTag} ist die eine erlaubte Ableitung — dort steht der Rückfall, der eine
     * Kreatur ohne Vermerk weiterhin mit ihrem Typnamen beantworten lässt (FR-009).
     *
     * <p>{@code MobNameplate.prettyName} macht aus {@code CAVE_SPIDER} ein {@code Cave Spider}, für
     * genau die Kreaturen, die keine Art haben. Das ist Anzeige und kein Schlüssel — es geht in
     * keine der drei Schnittstellen.
     */
    private static final List<String> ALLOWED =
            List.of("MobKindTag.java", "MobNameplate.java", "VanillaDamageMapping.java");

    @Test
    @DisplayName("niemand fuettert die drei Schnittstellen mehr mit getType().name()")
    void nobodyFeedsTheThreeInterfacesWithTheVanillaTypeName() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : productionSources()) {
            String name = source.getFileName().toString();
            if (ALLOWED.contains(name)) {
                continue;
            }
            String code = codeOnly(Files.readString(source));
            if (code.contains("getType().name()")) {
                offenders.add(name);
            }
        }

        assertThat(offenders)
                .as(
                        "die Art ist der Schluessel, nicht der Vanilla-Typ - MobKindTag.kindKeyOf ist"
                                + " die eine erlaubte Ableitung (B10, FR-006/FR-007)")
                .isEmpty();
    }

    @Test
    @DisplayName("die erlaubten Stellen gibt es wirklich - ein Wächter ohne Ziel bewacht nichts")
    void theAllowedPlacesActuallyExist() throws IOException {
        // Wird eine der drei Dateien umbenannt oder geloescht, faellt ihr Name still aus der Liste
        // und der Waechter waere um eine Ausnahme aermer, ohne dass jemand es merkt. Umgekehrt
        // waere ein Tippfehler in der Liste eine Ausnahme, die nie greift.
        List<String> names = new ArrayList<>();
        for (Path source : productionSources()) {
            names.add(source.getFileName().toString());
        }

        assertThat(names).containsAll(ALLOWED);
    }

    // --- fixtures ---

    private static List<Path> productionSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : List.of("rpg-platform", "rpg-plugin", "rpg-core", "rpg-persistence")) {
            Path main = ROOT.resolve(module + "/src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        return sources;
    }

    /** Kommentare weg: eine Klasse zu erklaeren ist erlaubt, sie zu rufen nicht. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-platform"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("repository root not found from " + Path.of("").toAbsolutePath());
        }
        return here;
    }
}

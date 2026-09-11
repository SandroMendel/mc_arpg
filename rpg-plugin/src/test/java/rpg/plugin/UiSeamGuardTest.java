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
 * Die drei Zusagen, die B13 über sich selbst macht (T144, T145, T150a).
 *
 * <p>Alle drei sind <b>Abwesenheiten</b>, und Abwesenheiten sind die Sorte Zusage, die ohne Wächter
 * verfällt: sie brechen nicht, wenn jemand etwas falsch macht, sondern wenn jemand etwas
 * <em>hinzufügt</em> — und dann sieht der Zusatz für sich genommen richtig aus.
 */
class UiSeamGuardTest {

    private static final Path ROOT = repositoryRoot();

    // --- T144: zwei Sender auf eine Flaeche ----------------------------------

    @Test
    @DisplayName("T144: es gibt genau EINE Umsetzung von HudRenderer")
    void thereIsExactlyOneHudRenderer() throws IOException {
        // Nach dem Muster von NoCompetingMobProviderTest. ZWEI SENDER AUF EINE FLAECHE HEISST: der
        // letzte gewinnt, und welcher das ist, haengt an der Registrierungsreihenfolge - also an
        // etwas, das niemand liest und jeder versehentlich aendert.
        //
        // Genau dieser Fehler hat B13 den Namen gekostet: StatusActionBar heisst ausdruecklich
        // NICHT HudRenderer, weil ein groesserer Name zwei Abstraktionen zu versoehnen gezwungen
        // haette statt eine zu erweitern.
        List<String> implementations = implementationsOf("implements HudRenderer");

        assertThat(implementations)
                .as("eine Naht, eine Umsetzung - alles andere waere ein Wettlauf")
                .containsExactly("PaperHudRenderer.java");
    }

    @Test
    @DisplayName("T144: und genau EINE von ItemRenderer")
    void thereIsExactlyOneItemRenderer() throws IOException {
        assertThat(implementationsOf("implements ItemRenderer"))
                .containsExactly("PaperItemRenderer.java");
    }

    @Test
    @DisplayName("T144: niemand ausser der Naht sendet selbst auf eine der drei Flaechen")
    void nobodyElseSendsToTheThreeSurfaces() throws IOException {
        // sendActionBar, showBossBar und setScoreboard sind die drei Wege an einer Naht vorbei.
        // Sie duerfen nur dort stehen, wo die Naht selbst umgesetzt wird.
        List<String> allowed =
                List.of(
                        "PaperHudRenderer.java",
                        "PaperBossBar.java",
                        "PaperSidebar.java",
                        // B05/B06s Actionbar-Zeile: sie zeichnet ihren Inhalt weiterhin selbst,
                        // ruft aber ueber HudRenderer (FR-023). Der Umzug hat den Inhalt gelassen
                        // und den Weg geaendert.
                        "StatusActionBar.java",
                        // B06s XP-Leiste - die eine benannte Ausnahme von FR-001 (FR-001a).
                        "ExperienceBar.java",
                        // B10s Namensschild ueber Kreaturen: keine der drei Flaechen.
                        "MobNameplate.java");

        List<String> offenders = new ArrayList<>();
        for (Path source : productionSources("rpg-platform", "rpg-plugin")) {
            String name = source.getFileName().toString();
            if (allowed.contains(name)) {
                continue;
            }
            String code = codeOnly(Files.readString(source));
            for (String direct : List.of("sendActionBar(", "showBossBar(", "setScoreboard(")) {
                if (code.contains(direct)) {
                    offenders.add(name + ": " + direct);
                }
            }
        }

        assertThat(offenders)
                .as("wer an der Naht vorbei sendet, macht SC-004 zunichte")
                .isEmpty();
    }

    // --- T145: die Naht ist austauschbar --------------------------------------

    @Test
    @DisplayName("T145: B04, B05 und B08 kennen HudRenderer nicht")
    void thegameplayBlocksDoNotKnowTheSeam() throws IOException {
        // SC-004, und die eigentliche Zusage der Naht: ein zweiter Renderer tritt an ihre Stelle,
        // ohne dass Spiellogik sich aendert. Waere HudRenderer in B04, B05 oder B08 bekannt, waere
        // der Austausch genau dort ein Umbau.
        List<String> offenders = new ArrayList<>();
        for (String block :
                List.of(
                        "rpg-core/src/main/java/rpg/core/stats",
                        "rpg-core/src/main/java/rpg/core/combat",
                        "rpg-core/src/main/java/rpg/core/ability")) {
            Path dir = ROOT.resolve(block);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path source : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    if (codeOnly(Files.readString(source)).contains("HudRenderer")) {
                        offenders.add(block + "/" + source.getFileName());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("die Naht ist B13s, nicht die der Bloecke, die die Daten fuehren")
                .isEmpty();
    }

    @Test
    @DisplayName("T145: rpg-core kennt keinen HudRenderer - er lebt bei seiner Umsetzung")
    void thecoreDoesNotKnowTheSeam() throws IOException {
        // Begruendung in contracts/hud-api.md §2: HudRenderer fuehrt keinen Paper-Typ und KOENNTE
        // nach unten, haette dort aber weder Aufrufer noch Umsetzung. Sollte je ein Kernmodul
        // zeichnen wollen, ist der Umzug eine Verschiebung ohne Signaturaenderung.
        List<String> offenders = new ArrayList<>();
        for (Path source : productionSources("rpg-core")) {
            if (codeOnly(Files.readString(source)).contains("HudRenderer")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertThat(offenders).isEmpty();
    }

    // --- T150a: die vier, die niemand anfasst ---------------------------------

    @Test
    @DisplayName("T150a: die vier nicht anzufassenden Klassen tauchen in rpg/platform/ui nicht auf")
    void theuntouchedBlocksStayUntouched() throws IOException {
        // FR-024 (AbilityHotbar, ADR-052), FR-070 (ClassSelectionMenu), FR-071 (B12s zwei Fenster).
        //
        // B13 sichert JEDE andere Zusage per Test. Ausgerechnet die vier "nicht anfassen" nur dem
        // Augenschein zu ueberlassen hiesse, sie beim ersten gut gemeinten Umbau zu verlieren - und
        // ein Umbau sieht fuer sich genommen immer richtig aus.
        List<String> untouched =
                List.of(
                        "AbilityHotbar",
                        "ClassSelectionMenu",
                        "StatisticsMenu",
                        "LeaderboardMenu");

        List<String> offenders = new ArrayList<>();
        Path ui = ROOT.resolve("rpg-platform/src/main/java/rpg/platform/ui");
        try (Stream<Path> walk = Files.walk(ui)) {
            for (Path source : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(source));
                for (String name : untouched) {
                    if (code.contains(name)) {
                        offenders.add(source.getFileName() + " nennt " + name);
                    }
                }
            }
        }

        assertThat(offenders)
                .as("B13 baut sie nicht um und zieht sie nicht hinter seine Naht")
                .isEmpty();
    }

    @Test
    @DisplayName("T150a: und es gibt sie wirklich - ein Waechter ohne Ziel bewacht nichts")
    void thefourClassesActuallyExist() throws IOException {
        // Wird eine umbenannt, faellt ihr Name still aus der Pruefung und der Waechter waere um
        // eine Zusage aermer, ohne dass jemand es merkt. Dieselbe Selbstpruefung wie in
        // NoRawTypeNameLeftTest.
        List<String> names = new ArrayList<>();
        for (Path source : productionSources("rpg-platform")) {
            names.add(source.getFileName().toString());
        }

        assertThat(names)
                .contains(
                        "AbilityHotbar.java",
                        "ClassSelectionMenu.java",
                        "StatisticsMenu.java",
                        "LeaderboardMenu.java");
    }

    // --- Aufbau ---------------------------------------------------------------

    private static List<String> implementationsOf(String declaration) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path source : productionSources("rpg-platform", "rpg-plugin", "rpg-core")) {
            if (codeOnly(Files.readString(source)).contains(declaration)) {
                found.add(source.getFileName().toString());
            }
        }
        return found;
    }

    private static List<Path> productionSources(String... modules) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : modules) {
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

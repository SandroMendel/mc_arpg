package rpg.core.zone;

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
 * T128a - niemand greift am Vertrag vorbei (FR-060, Prinzip III).
 *
 * <p>Der Vertrag dieses Blocks ist {@link Zones}, {@link Waypoints} und {@link Travel}. Alles andere
 * - der Index, die Konfiguration, die Implementierung dahinter - ist Innenleben. Ein Block, der
 * {@code ChunkZoneIndex} direkt anfasst, funktioniert sofort und bindet sich dabei an eine Datenform,
 * die sich beim naechsten Nachladen aendern darf.
 *
 * <p>Nach dem Muster von {@code ClassSourceInvariantsTest}: gesucht wird nach Benutzung im Code, nicht
 * nach Erwaehnung in einem Kommentar - eine Klasse zu erklaeren ist erlaubt, sie zu rufen nicht.
 */
class ZoneSourceInvariantsTest {

    private static final Path ROOT = repositoryRoot();

    /** Was ausserhalb von {@code rpg.core.zone} niemand anfassen soll. */
    private static final List<String> INTERNALS =
            List.of("ChunkZoneIndex", "ChunkTable", "ZoneConfigSchema", "DefaultZones", "ZoneConfig");

    @Test
    @DisplayName("kein anderes Paket benutzt das Innenleben dieses Blocks")
    void nobodyReachesPastTheContract() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : sourcesOutsideTheBlock()) {
            String code = TravelFixture.codeOnly(Files.readString(source));
            for (String internal : INTERNALS) {
                if (code.contains(internal)) {
                    offenders.add(source.getFileName() + " uses " + internal);
                }
            }
        }

        assertThat(offenders)
                .as("der Vertrag ist Zones, Waypoints und Travel - alles andere ist Innenleben")
                .isEmpty();
    }

    @Test
    @DisplayName("zones.yml wird nur von diesem Block gelesen")
    void onlyThisBlockReadsItsFile() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : sourcesOutsideTheBlock()) {
            String code = TravelFixture.codeOnly(Files.readString(source));
            if (code.contains("zones.yml")) {
                offenders.add(source.getFileName().toString());
            }
        }

        // Das Plugin nennt die Datei einmal, weil sie mit ausgeliefert werden muss - das ist eine
        // Liste von Dateinamen und kein zweiter Leser. Alles andere waere eine zweite Auslegung
        // derselben Konfiguration.
        assertThat(offenders)
                .as("eine Datei, ein Leser")
                .containsExactlyInAnyOrderElementsOf(List.of("RpgPlugin.java"));
    }

    @Test
    @DisplayName("das Plattformpaket kennt nur den Vertrag, nicht die Umsetzung")
    void theplatformSideKnowsOnlyTheContract() throws IOException {
        Path platform = ROOT.resolve("rpg-platform/src/main/java/rpg/platform/zone");

        try (Stream<Path> walk = Files.walk(platform)) {
            for (Path source : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = TravelFixture.codeOnly(Files.readString(source));

                assertThat(code)
                        .as(source.getFileName().toString())
                        .doesNotContain("ChunkZoneIndex")
                        .doesNotContain("DefaultZones");
            }
        }
    }

    @Test
    @DisplayName("der Index ist paketprivat und kann gar nicht von aussen geholt werden")
    void theindexIsNotReachable() throws IOException {
        String code =
                TravelFixture.codeOnly(
                        Files.readString(
                                ROOT.resolve("rpg-core/src/main/java/rpg/core/zone/DefaultZones.java")));

        // Nicht "niemand tut es", sondern "niemand kann": der Zugriff waere ein Compilerfehler.
        assertThat(code).contains("ChunkZoneIndex index()");
        assertThat(code).doesNotContain("public ChunkZoneIndex index()");
    }

    /** Jede Java-Quelle des Projekts ausserhalb von {@code rpg.core.zone} und ausserhalb der Tests. */
    private static List<Path> sourcesOutsideTheBlock() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module :
                List.of("rpg-core", "rpg-platform", "rpg-persistence", "rpg-plugin")) {
            Path main = ROOT.resolve(module).resolve("src/main/java");
            if (!Files.exists(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().replace('\\', '/').contains("/rpg/core/zone/"))
                        .forEach(sources::add);
            }
        }
        return sources;
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

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
 * T128 - kein Typ aus {@code org.bukkit} im Zonenpaket der Domaenenschicht (FR-059, Prinzip III).
 *
 * <p><b>Der Compiler kann das nicht sagen</b>, weil {@code rpg-core} Paper zwar nicht als
 * Abhaengigkeit fuehrt, die Bibliothek aber im Testpfad und ueber die anderen Module im selben Bau
 * liegt. Ein Import faellt also nicht auf, bis jemand versucht, die Regel ohne Server zu testen - und
 * dann ist die Schichtgrenze schon durchbrochen.
 *
 * <p>Die Grenze ist kein Selbstzweck: sie ist der Grund, warum die Reiseabfolge, das Levelband, die
 * Schadenserlaubnis und der Index in Millisekunden pruefbar sind, ohne dass ein Server hochfaehrt.
 */
class NoBukkitInCoreTest {

    private static final Path PACKAGE =
            repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");

    @Test
    @DisplayName("keine Quelle des Domaenenpakets importiert etwas aus org.bukkit")
    void nothingImportsBukkit() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : sources()) {
            String code = TravelFixture.codeOnly(Files.readString(source));
            if (code.contains("org.bukkit") || code.contains("io.papermc")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("Geometrie und Regeln sind Rechnung; ein Server gehoert nicht dazu (FR-059)")
                .isEmpty();
    }

    @Test
    @DisplayName("auch keine Adventure-Komponente - Text ist Sache der Plattform")
    void nothingImportsAdventure() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : sources()) {
            String code = TravelFixture.codeOnly(Files.readString(source));
            if (code.contains("net.kyori")) {
                offenders.add(source.getFileName().toString());
            }
        }

        // Der Block gibt Schluessel heraus und formuliert nie (Prinzip V). Eine Component hier waere
        // fertiger Text und damit ein zweiter Ort fuer Wortlaut.
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("der einzige Ortstyp ist WorldPosition aus rpg.core.scheduler")
    void theonlyPlaceTypeIsTheProjectsOwn() throws IOException {
        // Ein eigener Ortstyp waere die naheliegende Erfindung gewesen und der zweite Ort, an dem das
        // Projekt weiss, was eine Position ist.
        boolean anybodyUsesIt = false;
        for (Path source : sources()) {
            String code = TravelFixture.codeOnly(Files.readString(source));
            assertThat(code)
                    .as(source.getFileName().toString())
                    .doesNotContain("record ZonePosition")
                    .doesNotContain("class ZonePosition")
                    .doesNotContain("org.bukkit.Location");
            anybodyUsesIt |= code.contains("WorldPosition");
        }

        assertThat(anybodyUsesIt).as("und benutzt wird er auch").isTrue();
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(PACKAGE)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
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

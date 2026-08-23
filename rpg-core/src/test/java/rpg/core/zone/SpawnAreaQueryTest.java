package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T119 - was B10 vorfindet: Kennung und Geometrie, und sonst nichts (FR-053a, FR-054, FR-055a).
 *
 * <p>Der zweite Teil ist der wichtigere. Es waere leicht gewesen, den Bereichen eine Art mitzugeben -
 * "normal" und "boss" - und damit haette B09 behauptet, es gebe genau einen Boss je Region. Das ist
 * eine Aussage ueber Kreaturen, und Kreaturen gehoeren B10. Ein Bossbereich unterscheidet sich
 * geometrisch von keinem anderen; er unterscheidet sich in dem, was darin steht.
 */
class SpawnAreaQueryTest {

    @Test
    @DisplayName("jede der sechs Regionen liefert ihre Bereiche mit Kennung und Geometrie")
    void everyRegionHandsOutItsAreas() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        for (Zone zone : zones.all()) {
            List<SpawnArea> areas = zones.spawnAreasOf(zone.key());

            assertThat(areas).as(zone.key()).hasSizeGreaterThan(1);
            assertThat(areas)
                    .allSatisfy(
                            area -> {
                                assertThat(area.key()).isNotBlank();
                                assertThat(area.area().parts()).isNotEmpty();
                            });
        }
    }

    @Test
    @DisplayName("die Kennungen sind innerhalb ihrer Region eindeutig")
    void thekeysAreUniqueWithinTheirRegion() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        for (Zone zone : zones.all()) {
            assertThat(zones.spawnAreasOf(zone.key()))
                    .extracting(SpawnArea::key)
                    .as(zone.key())
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("eine unbekannte Region liefert eine leere Liste, keine Ausnahme")
    void anunknownRegionIsEmpty() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        // B10 wird diese Abfrage aus einem Spawn-Ereignis heraus stellen. Eine Ausnahme dort waere
        // ein Fehler im falschen Block, ausgeloest durch eine Konfiguration, die sich geaendert hat.
        assertThat(zones.spawnAreasOf("atlantis")).isEmpty();
        assertThat(zones.spawnAreasOf(null)).isEmpty();
    }

    @Test
    @DisplayName("ein Bereich traegt keine Rolle, keine Art und keine Kreaturenliste (FR-053a)")
    void anareaCarriesNothingButItsShape() throws IOException {
        String source =
                Files.readString(
                        repositoryRoot()
                                .resolve("rpg-core/src/main/java/rpg/core/zone/SpawnArea.java"));
        String code = TravelFixture.codeOnly(source);

        assertThat(code)
                .as("der Record hat genau zwei Komponenten")
                .contains("record SpawnArea(String key, Area area)");
        assertThat(code)
                .doesNotContain("boss")
                .doesNotContain("Kind")
                .doesNotContain("role")
                .doesNotContain("mob")
                .doesNotContain("count");
    }

    @Test
    @DisplayName("dieser Block setzt nichts in die Bereiche hinein (FR-054)")
    void thisblockSpawnsNothing() throws IOException {
        Path zonePackage = repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");
        Path platformPackage =
                repositoryRoot().resolve("rpg-platform/src/main/java/rpg/platform/zone");

        for (Path directory : List.of(zonePackage, platformPackage)) {
            try (Stream<Path> sources = Files.walk(directory)) {
                List<String> offenders =
                        sources.filter(path -> path.toString().endsWith(".java"))
                                .filter(SpawnAreaQueryTest::spawnsSomething)
                                .map(path -> path.getFileName().toString())
                                .toList();

                assertThat(offenders)
                        .as("Geometrie ist der Handel dieses Blocks, Kreaturen sind B10s")
                        .isEmpty();
            }
        }
    }

    private static boolean spawnsSomething(Path source) {
        try {
            String code = TravelFixture.codeOnly(Files.readString(source));
            return code.contains("spawnEntity")
                    || code.contains("EntityType")
                    || code.contains("LivingEntity");
        } catch (IOException unreadable) {
            throw new IllegalStateException("could not read " + source, unreadable);
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

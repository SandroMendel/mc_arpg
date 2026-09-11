package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/**
 * T126 - eine siebte Region entsteht durch Konfiguration allein (SC-003).
 *
 * <p>Das ist die Abnahmebedingung, an der sich entscheidet, ob dieser Block datengetrieben ist oder
 * nur so aussieht (Prinzip V). Sechs Regionen sind ausgeliefert, aber nirgends steht die Zahl sechs:
 * kein Enum, keine Konstante, keine Fallunterscheidung. Der Test fuegt eine hinzu und prueft, dass
 * sie in allem mitspielt - Zonenabfrage, Levelband, Schutzkern, Kristall, Spawn-Bereiche.
 */
class SeventhRegionTest {

    @Test
    @DisplayName("eine siebte Region entsteht allein durch einen Eintrag in zones.yml")
    void aseventhRegionNeedsNoCode() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zones(document)
                .put("frostmarch", ZoneFixture.region("frostmarch", 4500, 0, 61, 70, false));

        Zones zones = TravelFixture.load(document);

        assertThat(zones.all()).hasSize(7);
        assertThat(zones.byKey("frostmarch")).isPresent();
    }

    @Test
    @DisplayName("die neue Region beantwortet jede Frage, die die sechs anderen beantworten")
    void thenewRegionJoinsEverything() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zones(document)
                .put("frostmarch", ZoneFixture.region("frostmarch", 4500, 0, 61, 70, false));

        Zones zones = TravelFixture.load(document);
        WorldPosition inside = new WorldPosition(ZoneFixture.WORLD, 4500.5d, 65.0d, 0.5d);

        assertThat(zones.zoneKeyAt(inside)).isEqualTo("frostmarch");
        assertThat(zones.inSafeCore(inside)).isTrue();
        assertThat(zones.respawnPointOf("frostmarch")).isPresent();
        assertThat(zones.spawnAreasOf("frostmarch")).hasSize(2);
        assertThat(zones.crystalByKey("frostmarch-crystal")).isPresent();
        assertThat(zones.byKey("frostmarch").orElseThrow().levelBand())
                .isEqualTo(new LevelBand(61, 70));
    }

    @Test
    @DisplayName("ihr Kristall reist wie jeder andere")
    void thenewCrystalTravelsLikeTheOthers() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zones(document)
                .put("frostmarch", ZoneFixture.region("frostmarch", 4500, 0, 61, 70, false));

        Zones zones = TravelFixture.load(document);
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("frostmarch-crystal"),
                        holder -> false,
                        new TravelFixture.RecordingCurrency(1000L),
                        teleporter);

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "frostmarch-crystal"))
                .isEqualTo(TravelResult.OK);
        assertThat(teleporter.destinations)
                .containsExactly(zones.respawnPointOf("frostmarch").orElseThrow());
    }

    @Test
    @DisplayName("nirgends im Block steht die Zahl sechs als Annahme")
    void thenumberSixIsNowhereInTheCode() throws Exception {
        java.nio.file.Path zonePackage =
                repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");

        try (var sources = java.nio.file.Files.walk(zonePackage)) {
            java.util.List<String> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(SeventhRegionTest::assumesSixRegions)
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("sechs ist eine Zahl in zones.yml und keine Annahme im Code")
                    .isEmpty();
        }
    }

    private static boolean assumesSixRegions(java.nio.file.Path source) {
        try {
            String code = TravelFixture.codeOnly(java.nio.file.Files.readString(source));
            // Nach Zaehlungen und festen Groessen gesucht, nicht nach der Ziffer irgendwo: eine 6 in
            // einer Koordinate oder einem Verschiebewert ist harmlos.
            return code.contains("== 6")
                    || code.contains("MAX_ZONES")
                    || code.contains("ZONE_COUNT")
                    || code.contains("new Zone[6]");
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("could not read " + source, unreadable);
        }
    }


    private static java.nio.file.Path repositoryRoot() {
        java.nio.file.Path at = java.nio.file.Path.of("").toAbsolutePath();
        while (at != null && !java.nio.file.Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}

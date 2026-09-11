package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T104 - ein Rechtsklick findet seinen Kristall ueber einen Lookup, nicht ueber eine Runde durch alle
 * Kristalle (Prinzip II, research.md R5).
 *
 * <p>Das ist der teuerste Pfad des Blocks, obwohl er der seltenste aussieht: Rechtsklicks passieren
 * beim Bauen, beim Essen, beim Oeffnen jeder Kiste - der Listener laeuft also dauernd, und in fast
 * allen Faellen steht dort kein Kristall. Dieser Fall muss der billigste sein, nicht der teuerste.
 */
class CrystalIndexTest {

    private static final java.util.UUID WORLD = ZoneFixture.WORLD;

    @Test
    @DisplayName("ein Klick ohne Kristall endet nach einem Tabellenzugriff")
    void aClickWithoutACrystalCostsOneLookup() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        // Mitten in der Wildnis, weit weg von jedem Schutzkern.
        assertThat(zones.crystalAt(new rpg.core.scheduler.WorldPosition(WORLD, 700, 70, 700)))
                .isEmpty();
    }

    @Test
    @DisplayName("jeder der sechs Kristalle wird an seinem Ausloesebereich gefunden")
    void everyShippedCrystalIsFoundWhereItStands() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        for (CrystalPlacement placement : zones.crystals()) {
            Cuboid box = placement.crystal().triggerArea().parts().get(0);
            int x = (box.minX() + box.maxX()) / 2;
            int y = (box.minY() + box.maxY()) / 2;
            int z = (box.minZ() + box.maxZ()) / 2;

            assertThat(zones.crystalAt(new rpg.core.scheduler.WorldPosition(WORLD, x, y, z)))
                    .as(placement.crystal().key())
                    .contains(placement);
        }
    }

    @Test
    @DisplayName("ein Klick knapp neben dem Ausloesebereich findet nichts")
    void justOutsideTheTriggerBoxIsNothing() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        Cuboid box =
                zones.crystalByKey("greenfields-crystal")
                        .orElseThrow()
                        .crystal()
                        .triggerArea()
                        .parts()
                        .get(0);

        assertThat(
                        zones.crystalAt(
                                new rpg.core.scheduler.WorldPosition(
                                        WORLD, box.maxX() + 1, box.maxY(), box.maxZ())))
                .isEmpty();
        assertThat(
                        zones.crystalAt(
                                new rpg.core.scheduler.WorldPosition(
                                        WORLD, box.minX(), box.maxY() + 1, box.minZ())))
                .as("ueber dem Kristall zu stehen ist kein Klick auf ihn")
                .isEmpty();
    }

    @Test
    @DisplayName("die Suche nach dem Kristall laeuft durch keine Liste")
    void theCrystalLookupWalksNothing() throws IOException {
        String source =
                Files.readString(
                        repositoryRoot()
                                .resolve("rpg-core/src/main/java/rpg/core/zone/ChunkZoneIndex.java"));
        String lookup = methodBody(source, "public CrystalPlacement crystalAt(");

        // Dieselbe Zusage, die ZoneIndexNoIterationTest fuer zoneAt gibt - und derselbe Grund: mit
        // sechs Kristallen waere eine Schleife nicht messbar und trotzdem falsch.
        assertThat(lookup).contains("table.get(");

        // Eine Schleife steht durchaus darin - aber ueber die Kristalle DIESES Chunks, die die
        // Tabelle gerade herausgegeben hat. Der erste Entwurf dieses Tests verbot jede Schleife
        // und war damit strenger als der Entwurf: geteilte Chunks brauchen den exakten Quadertest.
        // Verboten ist nur der Griff in die Gesamtmenge.
        assertThat(lookup).doesNotContain("crystalsByKey");
        assertThat(lookup).doesNotContain("crystals()");
        assertThat(lookup)
                .as("die Schleife laeuft ueber den Eimer aus der Tabelle, nicht ueber alles")
                .contains("(CrystalPlacement[]) found");
    }

    private static String methodBody(String source, String signatureStart) {
        int at = source.indexOf(signatureStart);
        assertThat(at).as("Methode nicht gefunden: " + signatureStart).isNotNegative();
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("unbalanced braces after " + signatureStart);
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

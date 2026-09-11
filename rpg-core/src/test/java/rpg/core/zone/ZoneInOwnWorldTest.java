package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/**
 * T127 - eine Zone zieht per Konfigurationszeile in eine eigene Welt um (SC-004, ADR-006).
 *
 * <p><b>Das ist die Abnahmebedingung, die ADR-006 gekauft hat.</b> Eine Zone ist niemals eine
 * {@code World}, sondern ein Paar aus Weltkennung und Geometrie. Der Preis dafuer war, dass jede
 * Abfrage eine Weltkennung mitfuehren muss. Der Gegenwert steht hier: eine Instanzwelt, ein eigener
 * Serverbereich, ein abgetrennter Endgame-Kontinent - alles davon ist eine Zeile {@code world:} und
 * keine Codeaenderung.
 *
 * <p>Der zweite Test ist der, der es wirklich beweist: dieselben Koordinaten in zwei Welten sind zwei
 * verschiedene Orte. Ein Index, der die Welt vergaesse, waere in allen Tests oben gruen und wuerde
 * genau hier fallen.
 */
class ZoneInOwnWorldTest {

    @Test
    @DisplayName("die world:-Zeile verschiebt eine Region, sonst aendert sich nichts")
    void themoveIsOneLine() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "pale-wilds").put("world", "nether");

        Zones zones = TravelFixture.load(document);
        Zone moved = zones.byKey("pale-wilds").orElseThrow();

        assertThat(moved.worldId()).isEqualTo(ZoneFixture.OTHER_WORLD);
        assertThat(zones.all()).as("immer noch sechs Regionen").hasSize(6);
        assertThat(moved.levelBand()).as("und sonst unveraendert").isEqualTo(new LevelBand(51, 60));
        assertThat(zones.crystalByKey("pale-wilds-crystal")).isPresent();
    }

    @Test
    @DisplayName("dieselben Koordinaten in zwei Welten sind zwei verschiedene Orte")
    void thesameCoordinatesInTwoWorldsAreTwoPlaces() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "pale-wilds").put("world", "nether");
        Zones zones = TravelFixture.load(document);

        // Pale Wilds liegt im Testdokument bei (3000, 1500). In der Hauptwelt steht dort jetzt
        // niemand mehr in einer Region; in der anderen Welt schon.
        WorldPosition inMainWorld =
                new WorldPosition(ZoneFixture.WORLD, 3000.5d, 65.0d, 1500.5d);
        WorldPosition inOtherWorld =
                new WorldPosition(ZoneFixture.OTHER_WORLD, 3000.5d, 65.0d, 1500.5d);

        assertThat(zones.zoneKeyAt(inMainWorld)).isNull();
        assertThat(zones.zoneKeyAt(inOtherWorld)).isEqualTo("pale-wilds");
    }

    @Test
    @DisplayName("der Respawn-Punkt zieht mit - sonst faende der Tod die alte Welt")
    void therespawnPointMovesWithIt() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "pale-wilds").put("world", "nether");

        assertThat(TravelFixture.load(document).respawnPointOf("pale-wilds"))
                .map(WorldPosition::worldId)
                .contains(ZoneFixture.OTHER_WORLD);
    }

    @Test
    @DisplayName("eine Reise dorthin fuehrt in die andere Welt")
    void travellingThereCrossesWorlds() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "pale-wilds").put("world", "nether");

        Zones zones = TravelFixture.load(document);
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("pale-wilds-crystal"),
                        holder -> false,
                        new TravelFixture.RecordingCurrency(1000L),
                        teleporter);

        travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "pale-wilds-crystal");

        assertThat(teleporter.destinations)
                .singleElement()
                .extracting(WorldPosition::worldId)
                .isEqualTo(ZoneFixture.OTHER_WORLD);
    }

    @Test
    @DisplayName("zwei Regionen duerfen sich ueberlappen, wenn sie in verschiedenen Welten liegen")
    void overlapAcrossWorldsIsFine() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        // Genau uebereinander gelegt und trotzdem gueltig: die Ueberlappungspruefung waere ohne
        // Weltkennung ein Fehlalarm, der die Umzugsmoeglichkeit unbrauchbar machte.
        ZoneFixture.zoneIn(document, "pale-wilds").put("world", "nether");
        ZoneFixture.zoneIn(document, "pale-wilds")
                .put("area", ZoneFixture.area(ZoneFixture.box(-500, -500, 500, 500)));
        @SuppressWarnings("unchecked")
        Map<String, Object> core =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "pale-wilds").get("safe-core");
        core.put("area", ZoneFixture.area(ZoneFixture.box(-60, -60, 60, 60)));
        core.put("respawn-point", ZoneFixture.point(0.5d, 65.0d, 0.5d));
        @SuppressWarnings("unchecked")
        Map<String, Object> crystal =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "pale-wilds").get("crystal");
        crystal.put("trigger-area", ZoneFixture.area(ZoneFixture.box(-2, 64, -2, 2, 67, 2)));
        ZoneFixture.zoneIn(document, "pale-wilds")
                .put(
                        "spawn-areas",
                        java.util.List.of(
                                ZoneFixture.spawnArea("pale-wilds-east", 120, -80, 300, 80)));

        Zones zones = TravelFixture.load(document);

        assertThat(zones.zoneKeyAt(new WorldPosition(ZoneFixture.WORLD, 0.5d, 65.0d, 0.5d)))
                .isEqualTo("greenfields");
        assertThat(zones.zoneKeyAt(new WorldPosition(ZoneFixture.OTHER_WORLD, 0.5d, 65.0d, 0.5d)))
                .isEqualTo("pale-wilds");
    }
}

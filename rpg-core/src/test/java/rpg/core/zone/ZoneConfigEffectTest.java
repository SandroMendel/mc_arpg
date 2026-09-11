package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/**
 * T125 - vier Aenderungen an {@code zones.yml} wirken, ohne dass eine Zeile Code angefasst wird
 * (SC-003, Prinzip V).
 *
 * <p>Jeder Test nimmt dasselbe gueltige Dokument, aendert genau eine Zahl und liest das Ergebnis
 * durch dieselbe Abfrage wie das Spiel. Was hier gruen ist, ist die Zusage an den Betreiber: die
 * Stellschrauben sind wirklich Stellschrauben und nicht Werte, die zufaellig auch in einer Datei
 * stehen.
 *
 * <p>Nachgeladen wird, indem eine neue {@link DefaultZones} gebaut wird - genau das, was
 * {@code ZoneModule.applyReloadedConfig} tut. Der Weg durch das Modul ist bei
 * {@code ProvisionalWarningTest} geprueft; hier geht es um die Wirkung, nicht um den Mechanismus.
 */
class ZoneConfigEffectTest {

    @Test
    @DisplayName("eine geaenderte Geometrie verschiebt die Grenze (SC-003)")
    void changedGeometryMovesTheBorder() throws Exception {
        WorldPosition probe = new WorldPosition(ZoneFixture.WORLD, 700.5d, 65.0d, 0.5d);

        Zones before = TravelFixture.load(ZoneFixture.document());
        assertThat(before.zoneKeyAt(probe)).as("zwischen den Regionen, in der Wildnis").isNull();

        Map<String, Object> widened = ZoneFixture.document();
        ZoneFixture.zoneIn(widened, "greenfields")
                .put("area", ZoneFixture.area(ZoneFixture.box(-500, -500, 900, 500)));

        assertThat(TravelFixture.load(widened).zoneKeyAt(probe)).isEqualTo("greenfields");
    }

    @Test
    @DisplayName("ein geaendertes Levelband wirkt sofort")
    void changedLevelBandTakesEffect() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "dustlands").put("level-band", ZoneFixture.band(5, 9));

        Zone dustlands = TravelFixture.load(document).byKey("dustlands").orElseThrow();

        assertThat(dustlands.levelBand()).isEqualTo(new LevelBand(5, 9));
        assertThat(dustlands.levelBand().min()).isEqualTo(5);
    }

    @Test
    @DisplayName("ein umgelegter pvp-Schalter macht aus einer friedlichen Region eine offene")
    void thepvpSwitchIsOneLine() throws Exception {
        assertThat(TravelFixture.load(ZoneFixture.document()).byKey("darkforest").orElseThrow().pvp())
                .as("die Auslieferung hat ueberall false")
                .isFalse();

        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "darkforest").put("pvp", Boolean.TRUE);

        assertThat(TravelFixture.load(document).byKey("darkforest").orElseThrow().pvp()).isTrue();
    }

    @Test
    @DisplayName("ein geaenderter Reisepreis wird beim naechsten Klick verlangt")
    void thetravelPriceIsAKnob() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> crystal =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "pale-wilds").get("crystal");
        crystal.put("price", 400);

        Zones zones = TravelFixture.load(document);
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(1000L);
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("pale-wilds-crystal"),
                        holder -> false,
                        purse,
                        new TravelFixture.RecordingTeleporter());

        travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "pale-wilds-crystal");

        assertThat(purse.balance()).as("400 statt der 25 aus dem Ausgangsdokument").isEqualTo(600L);
    }

    @Test
    @DisplayName("ein Reisepreis von null macht die Reise kostenlos, ohne Codeaenderung")
    void afreeJourneyIsAlsoJustAConfiguration() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> crystal =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "pale-wilds").get("crystal");
        crystal.put("price", 0);

        Zones zones = TravelFixture.load(document);
        TravelFixture.RecordingCurrency purse = new TravelFixture.RecordingCurrency(0L);
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("pale-wilds-crystal"),
                        holder -> false,
                        purse,
                        new TravelFixture.RecordingTeleporter());

        assertThat(travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "pale-wilds-crystal"))
                .as("mit leerem Beutel, weil nichts verlangt wird")
                .isEqualTo(TravelResult.OK);
        assertThat(purse.bookings).isEmpty();
    }

    @Test
    @DisplayName("ein verschobener Schutzkern verschiebt den Respawn-Punkt mit")
    void amovedCoreMovesTheRespawnPoint() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> core =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "greenfields").get("safe-core");
        core.put("respawn-point", ZoneFixture.point(12.5d, 70.0d, -8.5d));

        assertThat(TravelFixture.load(document).respawnPointOf("greenfields"))
                .contains(new WorldPosition(ZoneFixture.WORLD, 12.5d, 70.0d, -8.5d));
    }
}

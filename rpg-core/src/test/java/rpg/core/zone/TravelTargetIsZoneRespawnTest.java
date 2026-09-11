package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/**
 * T100 - ein Kristall traegt kein eigenes Ziel (FR-045a).
 *
 * <p>Zwei Koordinaten im selben Schutzkern haetten dasselbe zweimal gesagt, und der Unterschied waere
 * erst aufgefallen, wenn jemand nach einer Reise woanders steht als nach einem Tod. Deshalb gibt es
 * nur eine Zahl: den Respawn-Punkt der Zone. Sie bedient den Tod, den Kampf-Logout und die Reise.
 */
class TravelTargetIsZoneRespawnTest {

    @Test
    @DisplayName("gereist wird an den Respawn-Punkt der Zone des Kristalls")
    void theJourneyEndsAtTheZonesRespawnPoint() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("safari-plains-crystal"),
                        holder -> false,
                        new TravelFixture.RecordingCurrency(1000L),
                        teleporter);

        travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "safari-plains-crystal");

        assertThat(teleporter.destinations)
                .containsExactly(zones.respawnPointOf("safari-plains").orElseThrow());
    }

    @Test
    @DisplayName("Reise und Tod fuehren an dieselbe Stelle - eine Zahl, drei Verwendungen")
    void travelAndDeathShareOneCoordinate() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        TravelFixture.RecordingTeleporter teleporter = new TravelFixture.RecordingTeleporter();
        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("darkforest-crystal"),
                        holder -> false,
                        new TravelFixture.RecordingCurrency(1000L),
                        teleporter);
        RespawnRouting routing =
                new RespawnRouting(
                        new ZonePresence() {
                            @Override
                            public boolean inSafeCore(java.util.UUID holderId) {
                                return false;
                            }

                            @Override
                            public String zoneKeyOf(java.util.UUID holderId) {
                                return "darkforest";
                            }
                        },
                        () -> zones);

        travel.travelTo(TravelFixture.HOLDER, TravelFixture.CHARACTER, "darkforest-crystal");
        WorldPosition afterDeath = routing.respawnFor(TravelFixture.HOLDER);

        assertThat(teleporter.destinations).containsExactly(afterDeath);
    }

    @Test
    @DisplayName("der Kristall hat kein Zielfeld - und das soll so bleiben")
    void theCrystalRecordCarriesNoDestination() throws Exception {
        // Ohne Kommentare gelesen: der Record SAGT in seinem Javadoc, dass er kein Ziel traegt,
        // und ein naiver Textvergleich wuerde ausgerechnet an dieser Zusage scheitern.
        String text =
                TravelFixture.codeOnly(
                        Files.readString(
                                repositoryRoot()
                                        .resolve(
                                                "rpg-core/src/main/java/rpg/core/zone/WaypointCrystal.java")));

        // Der Record ist die Stelle, an der ein zweites Ziel entstehen wuerde: irgendwann will
        // jemand "nur fuer diesen einen Kristall" eine andere Koordinate.
        assertThat(text).doesNotContain("WorldPosition");
        assertThat(text).doesNotContain("destination");
        assertThat(text).doesNotContain("target");
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

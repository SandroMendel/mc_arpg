package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T099 - "gilt als im Kampf" hat genau eine Quelle (FR-051g).
 *
 * <p>Dieselbe Zusage wie T080 fuer den Kampf-Logout, und aus demselben Grund: es gibt acht
 * Kampfsekunden, sie stehen in {@code combat.yml}, und B05 liest sie. Legte dieser Block eine zweite
 * Zahl oder eine zweite Uhr an, waeren beide beim ersten Nachjustieren auseinandergelaufen - und der
 * Fehler waere ausgerechnet dort aufgetreten, wo Spieler Geld verlieren.
 */
class TravelInCombatReadsB05Test {

    @Test
    @DisplayName("die Reise fragt dieselbe Frage wie die Logout-Regel, mit demselben Ergebnis")
    void bothRulesAskTheSameSource() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        // Eine einzige Quelle fuer beide Regeln - so, wie sie im Plugin auch verdrahtet ist.
        List<UUID> asked = new ArrayList<>();
        boolean[] fighting = {true};
        java.util.function.Predicate<UUID> combatState =
                holder -> {
                    asked.add(holder);
                    return fighting[0];
                };

        Travel travel =
                new DefaultTravel(
                        () -> zones,
                        TravelFixture.storeWith("dustlands-crystal"),
                        combatState,
                        new TravelFixture.RecordingCurrency(1000L),
                        new TravelFixture.RecordingTeleporter());

        assertThat(
                        travel.travelTo(
                                TravelFixture.HOLDER,
                                TravelFixture.CHARACTER,
                                "dustlands-crystal"))
                .isEqualTo(TravelResult.IN_COMBAT);

        fighting[0] = false;
        assertThat(
                        travel.travelTo(
                                TravelFixture.HOLDER,
                                TravelFixture.CHARACTER,
                                "dustlands-crystal"))
                .isEqualTo(TravelResult.OK);

        assertThat(asked)
                .as("gefragt wird nach dem Spieler, nicht nach dem Charakter - Kampf haengt am Koerper")
                .containsOnly(TravelFixture.HOLDER);
    }

    @Test
    @DisplayName("die Reise haelt weder eine Uhr noch eine zweite Zeitangabe")
    void theTravelSequenceOwnsNoClock() throws IOException {
        String text =
                Files.readString(
                        repositoryRoot()
                                .resolve("rpg-core/src/main/java/rpg/core/zone/DefaultTravel.java"));

        assertThat(text).doesNotContain("Duration");
        assertThat(text).doesNotContain("Clock");
        assertThat(text).doesNotContain("combat-timeout");
        assertThat(text)
                .as("sie nimmt die Antwort, nicht die Zutaten - wie CombatLogoutRule")
                .contains("Predicate<UUID> inCombat");
    }

    @Test
    @DisplayName("kein Quelltext dieses Blocks nennt eine eigene Kampfdauer")
    void noSourceInTheBlockNamesItsOwnWindow() throws IOException {
        Path zonePackage = repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");

        try (var sources = Files.walk(zonePackage)) {
            List<Path> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(TravelInCombatReadsB05Test::namesItsOwnCombatWindow)
                            .toList();

            assertThat(offenders)
                    .as("acht Sekunden stehen in combat.yml und nirgends sonst")
                    .isEmpty();
        }
    }

    private static boolean namesItsOwnCombatWindow(Path source) {
        try {
            String text = Files.readString(source);
            return text.contains("combatTimeout")
                    || text.contains("combat-timeout")
                    || text.contains("COMBAT_TIMEOUT");
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

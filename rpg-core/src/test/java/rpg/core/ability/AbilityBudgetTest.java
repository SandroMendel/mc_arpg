package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T100 und T101 - was das Fähigkeiten-System kostet (SC-002, SC-007).
 *
 * <p>Gezählt werden <b>geplante Aufgaben und tatsächlich getroffene Ziele</b>, keine Millisekunden.
 * Eine Zeitmessung wäre auch mit einer Aufgabe je Spieler zufrieden - genau der Bauform, die
 * Prinzip II ausschließt. Der echte Lasttest gegen einen laufenden Server steht in Abschnitt 9 des
 * Validierungsleitfadens; das hier ist sein serverfreier Vorläufer, gebaut wie {@code
 * CombatBudgetTest} in B05.
 */
class AbilityBudgetTest {

    @Test
    @DisplayName("SC-002 - hundert gleichzeitige Flächenfähigkeiten planen keine hundert Aufgaben")
    void aHundredSimultaneousAreaAbilitiesCostNoTasks() throws Exception {
        AbilityFixture fixture = AbilityFixture.withStrike();
        // Acht Ziele je Auslösung, hundert Auslösungen: achthundert Wirkungen.
        fixture.resolvedTargets = manyTargets(8);

        for (int i = 0; i < 100; i++) {
            fixture.stats.mana = fixture.stats.maxMana;
            fixture.runtime.trigger(fixture.character, "probe.strike");
            // Über die globale Sperre und den eigenen Cooldown hinweg, sonst misst der Lauf
            // neunundneunzig Abweisungen.
            fixture.clock.advance(Duration.ofSeconds(10));
        }

        assertThat(fixture.applications).as("hundert Auslösungen sind angekommen").hasSize(100);
        assertThat(fixture.scheduling.scheduled)
                .as(
                        "eine Fähigkeit ohne Wirkzeit und ohne Dauer plant nichts - achthundert"
                                + " Wirkungen und keine einzige Aufgabe")
                .isZero();
    }

    @Test
    @DisplayName("SC-002 - der Aufwand hängt an den Zielen, nicht an den Spielern im Umkreis")
    void theCostFollowsTheTargetsNotTheCrowd() throws Exception {
        AbilityFixture fixture = AbilityFixture.withStrike();
        fixture.resolvedTargets = manyTargets(8);

        fixture.runtime.trigger(fixture.character, "probe.strike");

        // Die Obergrenze der Fähigkeit ist acht. Wie viele Wesen in Reichweite standen, sieht die
        // Laufzeit nie - der Resolver hat schon gefiltert und gedeckelt.
        assertThat(fixture.applications).hasSize(1);
        assertThat(fixture.applications.get(0).targets()).hasSize(8);
    }

    @Test
    @DisplayName("SC-007 - die Fähigkeit trägt ihre Obergrenze, und die Laufzeit deckelt nicht nach")
    void theCapIsCarriedByTheAbilityAndEnforcedOnce() throws Exception {
        AbilityFixture fixture = AbilityFixture.withStrike();
        assertThat(fixture.strike().target().maxTargets()).isEqualTo(8);

        // Der Resolver ist die EINZIGE Stelle, die deckelt. Um das zu zeigen, liefert die Fixture
        // absichtlich zwanzig Ziele - mehr, als die Fähigkeit erlaubt - und die Laufzeit gibt alle
        // zwanzig weiter, statt heimlich ein zweites Mal zu begrenzen.
        //
        // Das ist kein Loch, sondern die Absicht: zwei Stellen, die eine Regel durchsetzen, heißt,
        // dass irgendwann eine davon falsch ist. Dass der echte Deckel greift, prüft
        // PaperTargetResolverTest gegen den räumlichen Index; hier steht, dass es dahinter keinen
        // zweiten gibt.
        fixture.resolvedTargets = manyTargets(20);
        fixture.runtime.trigger(fixture.character, "probe.strike");

        assertThat(fixture.applications.get(0).targets()).hasSize(20);
    }

    // --- helpers ---

    private static List<UUID> manyTargets(int count) {
        List<UUID> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(UUID.randomUUID());
        }
        return List.copyOf(targets);
    }
}

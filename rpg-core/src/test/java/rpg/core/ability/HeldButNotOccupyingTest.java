package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eine haltende Faehigkeit, die den Spieler nicht belegt (FR-045a).
 *
 * <p><b>{@code sustained} war zwei Dinge in einem Schalter, und fuer die Haelfte der Faehigkeiten,
 * die ihn tragen, war die zweite falsch.</b> Der Block des Warriors belegt ihn wirklich: er steht in
 * der Blockhaltung und macht nichts anderes. Der Ruf des Berserkers tut das nicht - das ist ein
 * Kampfschrei, und zwoelf Sekunden ohne jede andere Faehigkeit waeren keine Ultimative, sondern eine
 * Strafe. Das Magieschild des Magiers genauso: ein Zauber, der acht Sekunden haelt, waehrend er
 * weiterzaubert.
 *
 * <p>Der Fehler war lange unsichtbar, weil beide Zustaende dieselbe Ablage benutzten. Jetzt sind es
 * zwei: eine Stelle fuer "was er gerade TUT" und eine fuer "was auf ihm liegt". Jede Abfrage von
 * {@code running} beantwortet die Frage "darf er handeln", und ein Eintrag, der darauf mal ja und mal
 * nein bedeutet, wird einmal getestet und danach fuer immer falsch gelesen.
 */
class HeldButNotOccupyingTest {

    @Test
    @DisplayName("sie wirkt sofort und laeuft weiter, wie jede haltende Faehigkeit")
    void itTakesEffectAtOnceAndKeepsRunning() throws Exception {
        AbilityFixture held = AbilityFixture.withHeld();
        held.resolvedTargets = List.of(UUID.randomUUID());

        assertThat(held.runtime.trigger(held.character, "probe.ward"))
                .isEqualTo(AbilityResult.SUSTAINING);

        assertThat(held.applications).hasSize(1);
        assertThat(held.runtime.isHolding(held.character, "probe.ward")).isTrue();
        assertThat(held.registry.remainingCooldown(held.character, "probe.ward"))
                .as("der Cooldown beginnt erst am Ende")
                .isEmpty();
    }

    @Test
    @DisplayName("der Spieler kann waehrenddessen weiterspielen - das ist der ganze Unterschied")
    void thecasterKeepsPlaying() throws Exception {
        AbilityFixture held = AbilityFixture.withHeld();
        held.runtime.trigger(held.character, "probe.ward");
        // Die globale Sperre nach jeder Ausloesung ist eine andere Grenze und gilt weiter - sie
        // wegzulassen hiesse, hier versehentlich SIE zu messen statt der Belegung.
        held.clock.advance(java.time.Duration.ofSeconds(1));

        assertThat(held.runtime.trigger(held.character, "probe.strike"))
                .as("mit exclusive: true stuende hier ALREADY_SUSTAINING")
                .isEqualTo(AbilityResult.TRIGGERED);
        assertThat(held.runtime.isHolding(held.character, "probe.ward"))
                .as("und das Schild haelt weiter")
                .isTrue();
    }

    @Test
    @DisplayName("sie belegt den einen Platz nicht, den eine belegende Faehigkeit braucht")
    void itdoesNotOccupyTheSingleSlot() throws Exception {
        AbilityFixture held = AbilityFixture.withHeld();
        held.runtime.trigger(held.character, "probe.ward");

        assertThat(held.runtime.running(held.character))
                .as("running heisst 'er tut gerade etwas' - ein Schild ist nichts, was man tut")
                .isEmpty();
    }

    @Test
    @DisplayName("ein zweiter Rechtsklick nimmt sie herunter und startet den Cooldown (FR-045a)")
    void asecondClickTakesItDown() throws Exception {
        AbilityFixture held = AbilityFixture.withHeld();
        held.runtime.trigger(held.character, "probe.ward");
        double afterTrigger = held.stats.mana;

        assertThat(held.runtime.trigger(held.character, "probe.ward"))
                .isEqualTo(AbilityResult.ENDED);

        assertThat(held.runtime.isHolding(held.character, "probe.ward")).isFalse();
        assertThat(held.stats.mana)
                .as("FR-045e: vorzeitig beenden erstattet nichts")
                .isEqualTo(afterTrigger);
        assertThat(held.registry.remainingCooldown(held.character, "probe.ward")).isPresent();
    }

    @Test
    @DisplayName("laeuft die Dauer ab, endet sie von selbst")
    void itendsOnItsOwn() throws Exception {
        AbilityFixture held = AbilityFixture.withHeld();
        held.runtime.trigger(held.character, "probe.ward");

        held.scheduling.runPending();

        assertThat(held.runtime.isHolding(held.character, "probe.ward")).isFalse();
        assertThat(held.registry.remainingCooldown(held.character, "probe.ward")).isPresent();
    }

    @Test
    @DisplayName("eine belegende Faehigkeit weist weiterhin alles ab - daran aendert sich nichts")
    void anexclusiveAbilityStillBlocksEverything() throws Exception {
        AbilityFixture occupying = AbilityFixture.withSustained();
        occupying.runtime.trigger(occupying.character, "probe.whirl");

        assertThat(occupying.runtime.trigger(occupying.character, "probe.strike"))
                .isEqualTo(AbilityResult.ALREADY_SUSTAINING);
    }

    @Test
    @DisplayName("wer nichts angibt, bekommt das Verhalten von vorher")
    void theDefaultIsTheOldBehaviour() throws Exception {
        AbilityFixture occupying = AbilityFixture.withSustained();

        // withSustained() schreibt kein `exclusive`. Waere die Voreinstellung false, liefe jede
        // bereits geschriebene haltende Faehigkeit ploetzlich anders - und das waere die Art
        // Aenderung, die niemand bemerkt, bis sie im Spiel auffaellt.
        assertThat(occupying.registry.config().require("probe.whirl").exclusive()).isTrue();
    }
}

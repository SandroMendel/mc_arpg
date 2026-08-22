package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T132 - der Deckel auf die Cooldown-Reduktion (ADR-008, FR-027).
 *
 * <p><b>Vierzig Prozent, und die Zahl steht bewusst im Code</b> - als einzige dieses Blocks. Sie ist
 * kein Balancing-Wert, sondern eine Grenze: wäre sie konfigurierbar, könnte eine Konfiguration sie
 * aufheben, und dann wäre ein Cooldown von null erreichbar. Eine Fähigkeit ohne Cooldown ist keine
 * starke Fähigkeit, sondern eine kaputte.
 *
 * <p>Der Deckel liegt bei der <em>Reduktion</em>, nicht beim Ergebnis. Das ist der Unterschied
 * zwischen "höchstens vierzig Prozent schneller" und "mindestens so viele Sekunden" - die erste Regel
 * skaliert mit dem Cooldown der Fähigkeit, die zweite hätte kurze Cooldowns unantastbar gemacht und
 * lange kaum berührt.
 */
class AbilityCooldownCapTest {

    private AbilityFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
    }

    @Test
    @DisplayName("ohne Reduktion gilt der Cooldown aus der Datei")
    void withoutReductionTheFileWins() {
        assertThat(fixture.runtime.effectiveCooldown(fixture.character, fixture.strike()))
                .isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    @DisplayName("eine Reduktion unter dem Deckel wirkt vollständig")
    void belowTheCapItAppliesInFull() {
        fixture.stats.cooldownReduction = 0.25;

        assertThat(fixture.runtime.effectiveCooldown(fixture.character, fixture.strike()))
                .as("9 s minus ein Viertel")
                .isEqualTo(Duration.ofMillis(6750));
    }

    @Test
    @DisplayName("genau am Deckel wirkt sie noch ganz")
    void exactlyAtTheCapItStillApplies() {
        fixture.stats.cooldownReduction = AbilityRuntime.MAX_COOLDOWN_REDUCTION;

        assertThat(fixture.runtime.effectiveCooldown(fixture.character, fixture.strike()))
                .isEqualTo(Duration.ofMillis(5400));
    }

    @Test
    @DisplayName("darüber wird gekappt - achtzig Prozent wirken wie vierzig")
    void aboveTheCapItIsCut() {
        fixture.stats.cooldownReduction = 0.80;

        assertThat(fixture.runtime.effectiveCooldown(fixture.character, fixture.strike()))
                .as("nicht 1,8 s, sondern 5,4 - der Deckel greift")
                .isEqualTo(Duration.ofMillis(5400));
    }

    @Test
    @DisplayName("selbst bei hundert Prozent bleibt ein Cooldown übrig")
    void evenAtOneHundredPercentSomethingRemains() {
        fixture.stats.cooldownReduction = 1.0;

        Duration effective = fixture.runtime.effectiveCooldown(fixture.character, fixture.strike());

        assertThat(effective).as("nie null - das ist der Zweck des Deckels").isPositive();
        assertThat(effective).isEqualTo(Duration.ofMillis(5400));
    }

    @Test
    @DisplayName("eine Fähigkeit ohne Cooldown bekommt durch Reduktion auch keinen")
    void zeroStaysZero() {
        fixture.stats.cooldownReduction = 0.40;

        // Die passive Fähigkeit der Fixtur hat cooldown-ms 0. Vierzig Prozent von nichts ist nichts,
        // und der Sonderfall ist da, damit die Rechnung gar nicht erst läuft.
        Ability passive = fixture.registry.config().require("probe.lifesteal");

        assertThat(fixture.runtime.effectiveCooldown(fixture.character, passive)).isZero();
    }

    @Test
    @DisplayName("der Deckel ist nicht konfigurierbar, und das ist die Zusage")
    void theCapIsNotConfigurable() {
        // Wäre er es, könnte eine Konfiguration ihn auf 1,0 setzen - und dann wäre der Deckel keiner.
        // Die einzige Zahl dieses Blocks, die im Code stehen darf, und AbilitySourceInvariantsTest
        // führt sie namentlich als Ausnahme.
        assertThat(AbilityRuntime.MAX_COOLDOWN_REDUCTION).isEqualTo(0.40);
    }
}

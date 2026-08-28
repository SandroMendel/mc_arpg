package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;

/**
 * FR-026: die Skalierung darf Attributwerte, Level oder Staerke einer Kreatur nicht veraendern -
 * dieselbe Art hat bei einem und bei zwanzig Spielern exakt dieselben Werte.
 */
class ScalingNeverTouchesAttributesTest {

    @Test
    @DisplayName("dieselbe Art hat bei einem und bei zwanzig Spielern exakt dieselben Werte")
    void sameKindSameValuesRegardlessOfPlayerCount() {
        MobKind kind =
                new MobKind(
                        "greenfields.rotling",
                        "ZOMBIE",
                        3,
                        Map.of(Attribute.HEALTH, 40.0),
                        24.0,
                        MessageKey.of("mob.greenfields.rotling.name"),
                        12L,
                        4L,
                        false);
        Budget budget = new Budget(800, 130, 12, 25);

        DensityScaling.targetDensity(budget, 0.20, 1, 0);
        DensityScaling.respawnInterval(Duration.ofSeconds(2), 0.20, 1);
        DensityScaling.targetDensity(budget, 0.20, 20, 0);
        DensityScaling.respawnInterval(Duration.ofSeconds(2), 0.20, 20);

        assertThat(kind.attributes()).containsExactlyEntriesOf(Map.of(Attribute.HEALTH, 40.0));
        assertThat(kind.level()).isEqualTo(3);
        assertThat(kind.followRange()).isEqualTo(24.0);
    }

    @Test
    @DisplayName("DensityScaling kennt MobKind nirgends - es gibt nichts an einer Art zu veraendern")
    void densityScalingHasNoMobKindInItsSignatures() {
        for (Method method : DensityScaling.class.getDeclaredMethods()) {
            assertThat(method.getReturnType()).isNotEqualTo(MobKind.class);
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType).isNotEqualTo(MobKind.class);
            }
        }
    }
}

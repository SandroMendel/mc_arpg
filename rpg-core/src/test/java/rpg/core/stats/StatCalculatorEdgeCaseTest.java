package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T015: zero, non-finite input and the boundaries themselves (SC-005). */
class StatCalculatorEdgeCaseTest {

    private static final StatConfig CONFIG = StatConfig.defaults();

    @Test
    @DisplayName("a contribution of zero changes nothing")
    void zeroContribution() {
        StatSnapshot snapshot =
                StatCalculator.compute(
                        CONFIG,
                        List.of(
                                ModifierSet.of(
                                        SourceId.of(SourceKind.BUFF, "nothing"),
                                        StatModifier.flat(Attribute.HEALTH, 0.0),
                                        StatModifier.percent(Attribute.HEALTH, 0.0))),
                        null,
                        1L);
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("an empty set contributes nothing and is not an error")
    void emptySet() {
        StatSnapshot snapshot =
                StatCalculator.compute(
                        CONFIG,
                        List.of(ModifierSet.empty(SourceId.of(SourceKind.ZONE, "hub"))),
                        null,
                        1L);
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("NaN and infinity are refused where they enter, not carried into a value")
    void nonFiniteModifierRefused() {
        assertThatThrownBy(() -> StatModifier.flat(Attribute.HEALTH, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("health")
                .hasMessageContaining("finite");

        assertThatThrownBy(
                        () -> StatModifier.percent(Attribute.HEALTH, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    @Test
    @DisplayName("a value landing exactly on a bound is kept, not nudged")
    void exactlyOnTheBound() {
        StatSnapshot snapshot =
                StatCalculator.compute(
                        CONFIG,
                        List.of(
                                ModifierSet.of(
                                        SourceId.of(SourceKind.EQUIPMENT, "cap"),
                                        StatModifier.flat(Attribute.HEALTH, 1900.0))),
                        null,
                        1L);
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("an overflow to infinity is caught by the ceiling")
    void overflowIsClamped() {
        StatSnapshot snapshot =
                StatCalculator.compute(
                        CONFIG,
                        List.of(
                                ModifierSet.of(
                                        SourceId.of(SourceKind.BUFF, "huge"),
                                        StatModifier.flat(Attribute.HEALTH, Double.MAX_VALUE),
                                        StatModifier.percent(Attribute.HEALTH, Double.MAX_VALUE))),
                        null,
                        1L);
        assertThat(snapshot.get(Attribute.HEALTH)).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("a snapshot refuses a wrong-sized value array")
    void snapshotRejectsWrongSize() {
        assertThatThrownBy(() -> new StatSnapshot(new double[3], 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(Attribute.count()));
    }

    @Test
    @DisplayName("a blank source key is refused")
    void blankSourceKey() {
        assertThatThrownBy(() -> SourceId.of(SourceKind.BUFF, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }
}

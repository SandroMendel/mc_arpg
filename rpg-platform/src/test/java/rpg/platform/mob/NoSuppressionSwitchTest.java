package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-018c: es gibt keinen Schalter, mit dem die Vanilla-Unterdrueckung abgeschaltet werden kann
 * (entschieden am 2026-08-24).
 *
 * <p>Ein Schalter waere ein Weg, das Budget zu umgehen: einmal auf {@code false} gestellt und
 * vergessen, laeuft der Server voll und niemand sieht die Ursache. Dieser Test haelt die
 * Entscheidung maschinell fest, damit sie nicht aus Bequemlichkeit zurueckkommt - {@code
 * MobConfig} und {@code MobConfigSchema} tragen bewusst kein Feld dafuer, und diese Klasse hier
 * bietet keine Methode an, die die Unterdrueckung ausschalten koennte.
 */
class NoSuppressionSwitchTest {

    @Test
    @DisplayName("VanillaSpawnSuppressor bietet keine Methode an, die die Unterdrueckung abschaltet")
    void offersNoMethodToTurnItOff() {
        for (Method method : VanillaSpawnSuppressor.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            assertThat(name)
                    .as("kein Schalter-Name im oeffentlichen oder privaten Vokabular dieser Klasse")
                    .doesNotContain("enable")
                    .doesNotContain("disable")
                    .doesNotContain("toggle")
                    .isNotEqualTo("setsuppressed");
        }
    }

    @Test
    @DisplayName("MobConfig traegt kein Feld fuer die Unterdrueckung")
    void mobConfigCarriesNoFieldForIt() {
        for (var field : rpg.core.mob.MobConfig.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            assertThat(name).doesNotContain("suppress");
        }
    }
}

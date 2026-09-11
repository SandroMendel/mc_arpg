package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-034a: ein Boss unterscheidet sich von einer gewoehnlichen Art ausschliesslich durch seine
 * Konfiguration - hoehere Attributwerte, ein eigener Platz, ein Respawn-Timer. Keine eigenen
 * Faehigkeiten, keine Phasenwechsel: beides gehoert zum spaeteren Dungeon-Boss (ADR-006). Dieser
 * Waechter haelt fest, dass keine der drei Klassen einen Faehigkeitsverweis tragen kann - eine
 * Abgrenzung, die sonst still verfaellt, sobald jemand versucht wäre, hier eine anzuflicken.
 */
class BossHasNoAbilitiesTest {

    @Test
    @DisplayName("MobKind traegt kein Feld, das auf eine Faehigkeit verweisen koennte")
    void mobKindCarriesNoAbilityField() {
        assertNoAbilityField(MobKind.class);
    }

    @Test
    @DisplayName("BossSpec traegt kein Feld, das auf eine Faehigkeit verweisen koennte")
    void bossSpecCarriesNoAbilityField() {
        assertNoAbilityField(BossSpec.class);
    }

    @Test
    @DisplayName("BossState traegt kein Feld, das auf eine Faehigkeit verweisen koennte")
    void bossStateCarriesNoAbilityField() {
        assertNoAbilityField(BossState.class);
    }

    private static void assertNoAbilityField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            assertThat(field.getName().toLowerCase(Locale.ROOT))
                    .as(type.getSimpleName() + "." + field.getName())
                    .doesNotContain("ability")
                    .doesNotContain("phase")
                    .doesNotContain("cooldown");
        }
    }
}

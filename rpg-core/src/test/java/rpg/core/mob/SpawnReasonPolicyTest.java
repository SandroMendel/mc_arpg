package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-018d: welcher Grund durchgelassen wird - als reine Regel, ohne Bukkit.
 *
 * <p>Erlaubt ist genau das absichtliche Setzen: {@code CUSTOM} (unsere eigenen Kreaturen),
 * {@code COMMAND}, {@code SPAWNER_EGG}, {@code DISPENSE_EGG}. Alles andere - insbesondere
 * Vanillas natuerliches Spawnen und die knapp vierzig anderen Wege, ueber die eine Kreatur sonst
 * entstehen kann - wird verworfen.
 */
class SpawnReasonPolicyTest {

    @Test
    @DisplayName("die vier absichtlichen Gruende sind erlaubt")
    void theFourIntentionalReasonsAreAllowed() {
        assertThat(SpawnReasonPolicy.isAllowed("CUSTOM")).isTrue();
        assertThat(SpawnReasonPolicy.isAllowed("COMMAND")).isTrue();
        assertThat(SpawnReasonPolicy.isAllowed("SPAWNER_EGG")).isTrue();
        assertThat(SpawnReasonPolicy.isAllowed("DISPENSE_EGG")).isTrue();
    }

    @Test
    @DisplayName("natuerliches Spawnen und alles andere ist nicht erlaubt")
    void naturalSpawningAndEverythingElseIsNotAllowed() {
        assertThat(SpawnReasonPolicy.isAllowed("NATURAL")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("RAID")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("NETHER_PORTAL")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("JOCKEY")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("REINFORCEMENTS")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("SLIME_SPLIT")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("TRIAL_SPAWNER")).isFalse();
        assertThat(SpawnReasonPolicy.isAllowed("DROWNED")).isFalse();
    }

    @Test
    @DisplayName("null ist nicht erlaubt und wirft nicht")
    void nullIsNotAllowedAndDoesNotThrow() {
        assertThat(SpawnReasonPolicy.isAllowed(null)).isFalse();
    }
}

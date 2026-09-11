package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T070 - the fourth death cause, added by B09 under ADR-030 (FR-040, SC-011).
 *
 * <p><b>This test carries more weight than the ADR expected it to.</b> ADR-030 says the compiler will
 * point at every place that has to handle the new value. It does not: nothing in the project switches
 * exhaustively over {@link DeathCause}, and nothing calls {@code values()}, so adding a constant
 * compiled without a single complaint. That is comfortable and it is also the problem - the safety
 * net the ADR named is not there, so the promise has to be held by an assertion instead.
 *
 * <p>What is asserted here is the one thing B12 will depend on later: that "died" and "ran away" are
 * two different answers, and that the second one exists at all.
 */
class DeathCauseLogoutTest {

    @Test
    @DisplayName("LOGOUT exists and is not COMBAT - the whole point of adding it (FR-040)")
    void logoutIsItsOwnCause() {
        assertThat(DeathCause.LOGOUT).isNotEqualTo(DeathCause.COMBAT);
        assertThat(DeathCause.valueOf("LOGOUT")).isEqualTo(DeathCause.LOGOUT);
    }

    @Test
    @DisplayName("the four shipped causes are still there, and there are exactly five now")
    void theCauseSetIsComplete() {
        // A census, like the handler count in FullBootstrapTest: a fifth cause is a decision, and a
        // decision should be argued for here rather than appear in a diff.
        assertThat(DeathCause.values())
                .containsExactly(
                        DeathCause.COMBAT,
                        DeathCause.ENVIRONMENT,
                        DeathCause.VOID,
                        DeathCause.ADMIN,
                        DeathCause.LOGOUT);
    }

    @Test
    @DisplayName("a logout death carries no killer and names a player victim")
    void aLogoutDeathHasNoKiller() {
        UUID victim = UUID.randomUUID();
        UUID character = UUID.randomUUID();

        CombatDeathEvent death =
                new CombatDeathEvent(victim, character, null, DeathCause.LOGOUT, null, true);

        assertThat(death.cause()).isEqualTo(DeathCause.LOGOUT);
        assertThat(death.killer()).as("nobody killed them - they left").isEmpty();
        assertThat(death.playerVictim()).isTrue();
        assertThat(death.victimCharacterId()).isEqualTo(character);
    }

    @Test
    @DisplayName("the ordinary causes are unchanged by the addition")
    void theOtherCausesStillBehave() {
        UUID victim = UUID.randomUUID();
        UUID killer = UUID.randomUUID();

        CombatDeathEvent combat =
                new CombatDeathEvent(victim, null, killer, DeathCause.COMBAT, null, false);

        assertThat(combat.killer()).contains(killer);
        assertThat(combat.cause()).isEqualTo(DeathCause.COMBAT);
    }
}

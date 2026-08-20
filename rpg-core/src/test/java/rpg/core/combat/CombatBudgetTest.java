package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T041-T044, T013: what the hot path costs (FR-044, FR-045, SC-005, SC-013).
 *
 * <p>The assertions count <b>scheduled tasks and reused objects</b>, not milliseconds. A timing
 * measurement would pass just as happily with a task per player and an object per hit - which is
 * precisely what Principles I and II rule out. The real load test against a running server is
 * section 9 of the validation guide; this is its server-free precursor.
 */
class CombatBudgetTest {

    @Test
    @DisplayName("10 000 hits reuse one damage context - no object per hit")
    void contextIsReused() {
        CombatFixture fixture = new CombatFixture();
        List<DamageView> seen = new ArrayList<>();
        fixture.pipeline.registerInterceptor(
                new DamageInterceptor() {
                    @Override
                    public String id() {
                        return "identity-recorder";
                    }

                    @Override
                    public PipelineStage stage() {
                        return PipelineStage.RAW_DAMAGE;
                    }

                    @Override
                    public void intercept(DamageView damage) {
                        seen.add(damage);
                    }
                });

        UUID attacker = fixture.player(1.0, 0.0, 4.0);
        UUID target = fixture.mob(2000.0, 0.0, 5.0);

        for (int i = 0; i < 10_000; i++) {
            // Topped up every 500 hits: health is capped at 2000 by B04's configuration, so a
            // target that is never healed dies after 2000 hits and the rest of the run measures
            // nothing.
            if (i % 500 == 0) {
                fixture.fillToMax(target);
            }
            fixture.pipeline.meleeAttack(attacker, target);
            fixture.clock.advanceMillis(300);
        }

        assertThat(seen).hasSize(10_000);
        // All ten thousand are the same object: the context is reused, not rebuilt.
        assertThat(seen.stream().distinct().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("an idle server schedules nothing and computes nothing")
    void idleCostsNothing() {
        CombatFixture fixture = new CombatFixture();
        for (int i = 0; i < 50; i++) {
            fixture.player(50.0, 0.0, 4.0);
            fixture.mob(200.0, 10.0, 8.0);
        }
        fixture.clearRecorded();

        for (int tick = 0; tick < 1200; tick++) {
            fixture.clock.advanceMillis(50);
        }

        assertThat(fixture.scheduler.scheduled).isZero();
        assertThat(fixture.damageEvents).isEmpty();
        assertThat(fixture.deaths).isEmpty();
    }

    @Test
    @DisplayName("the attribution window never exceeds its size, whatever happens")
    void attributionStaysBounded() {
        CombatFixture fixture = new CombatFixture();
        UUID target = fixture.mob(10_000_000.0, 0.0, 5.0);

        for (int i = 0; i < 200; i++) {
            UUID attacker = fixture.player(1.0, 0.0, 4.0);
            fixture.pipeline.meleeAttack(attacker, target);
        }

        assertThat(fixture.pipeline.currentShares(target))
                .hasValueSatisfying(
                        share -> assertThat(share.shares()).hasSizeLessThanOrEqualTo(16));
    }

    @Test
    @DisplayName("a busy round of 150 attackers against 800 targets stays inside the tick budget")
    void busyRound() {
        CombatFixture fixture = new CombatFixture();
        List<UUID> attackers = new ArrayList<>();
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            attackers.add(fixture.player(20.0, 0.0, 4.0));
        }
        for (int i = 0; i < 800; i++) {
            targets.add(fixture.mob(100_000.0, 10.0, 8.0));
        }

        // Warm-up, so the measurement is not dominated by class loading and a cold JIT.
        for (int round = 0; round < 20; round++) {
            for (int i = 0; i < attackers.size(); i++) {
                fixture.pipeline.meleeAttack(attackers.get(i), targets.get(i));
            }
            fixture.clock.advanceMillis(300);
        }

        long startedAt = System.nanoTime();
        for (int i = 0; i < attackers.size(); i++) {
            fixture.pipeline.meleeAttack(attackers.get(i), targets.get(i));
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMillis).isLessThan(5L);
    }

    @Test
    @DisplayName("holding a view past its event throws instead of returning another fight's data")
    void staleViewThrows() {
        CombatFixture fixture = new CombatFixture();
        AtomicReference<DamageView> escaped = new AtomicReference<>();
        fixture.pipeline.registerInterceptor(
                new DamageInterceptor() {
                    @Override
                    public String id() {
                        return "leaker";
                    }

                    @Override
                    public PipelineStage stage() {
                        return PipelineStage.MODIFIERS;
                    }

                    @Override
                    public void intercept(DamageView damage) {
                        escaped.set(damage);
                    }
                });

        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(1000.0, 0.0, 5.0);
        fixture.pipeline.meleeAttack(attacker, target);

        assertThatThrownBy(() -> escaped.get().rawDamage())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finished damage event");
    }

    @Test
    @DisplayName("forgetting a holder releases everything it held")
    void forgettingReleasesEverything() {
        CombatFixture fixture = new CombatFixture();

        for (int i = 0; i < 200; i++) {
            UUID attacker = fixture.player(5.0, 0.0, 4.0);
            UUID target = fixture.mob(1000.0, 0.0, 5.0);
            fixture.pipeline.meleeAttack(attacker, target);
            fixture.pipeline.forget(attacker);
            fixture.pipeline.forget(target);
        }

        int[] counts = fixture.pipeline.trackedCounts();
        assertThat(counts)
                .as("attack window, combat state, attribution, aggregation")
                .containsExactly(0, 0, 0, 0);
    }
}

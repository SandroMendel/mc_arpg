package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T014, T041, T045, T059-T062: the pipeline itself (FR-007 to FR-010, FR-025 to FR-028). */
class CombatPipelineTest {

    /** Records which stages it saw, and can cancel at one of them. */
    private static final class RecordingInterceptor implements DamageInterceptor {
        private final PipelineStage stage;
        private final List<PipelineStage> seen;
        private final boolean cancels;

        RecordingInterceptor(PipelineStage stage, List<PipelineStage> seen, boolean cancels) {
            this.stage = stage;
            this.seen = seen;
            this.cancels = cancels;
        }

        @Override
        public String id() {
            return "recorder:" + stage;
        }

        @Override
        public PipelineStage stage() {
            return stage;
        }

        @Override
        public void intercept(DamageView damage) {
            seen.add(damage.stage());
            if (cancels) {
                damage.cancel();
            }
        }
    }

    @Test
    @DisplayName("a hit runs through all six stages in order")
    void allSixStagesInOrder() {
        CombatFixture fixture = new CombatFixture();
        List<PipelineStage> seen = new ArrayList<>();
        for (PipelineStage stage : PipelineStage.values()) {
            fixture.pipeline.registerInterceptor(new RecordingInterceptor(stage, seen, false));
        }

        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(500.0, 100.0, 5.0);

        assertThat(fixture.pipeline.meleeAttack(attacker, target).applied()).isTrue();
        assertThat(seen)
                .containsExactly(
                        PipelineStage.SOURCE,
                        PipelineStage.RAW_DAMAGE,
                        PipelineStage.MODIFIERS,
                        PipelineStage.DEFENCE,
                        PipelineStage.APPLICATION,
                        PipelineStage.AFTERMATH);
    }

    @Test
    @DisplayName("a hit deals exactly the damage the formula says")
    void damageMatchesTheFormula() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(500.0, 100.0, 5.0);
        double before = fixture.health(target);

        DamageResult result = fixture.pipeline.meleeAttack(attacker, target);

        assertThat(result.applied()).isTrue();
        assertThat(result.finalDamage()).isEqualTo(25.0);
        assertThat(fixture.health(target)).isEqualTo(before - 25.0);
    }

    @Test
    @DisplayName("cancelling at any stage ends the event without damage, animation or attribution")
    void cancellingAtEveryStage() {
        for (PipelineStage stage : PipelineStage.values()) {
            if (stage == PipelineStage.AFTERMATH) {
                continue; // by then the damage has already landed - that is the point of the stage
            }
            CombatFixture fixture = new CombatFixture();
            fixture.pipeline.registerInterceptor(
                    new RecordingInterceptor(stage, new ArrayList<>(), true));

            UUID attacker = fixture.player(50.0, 0.0, 4.0);
            UUID target = fixture.mob(500.0, 0.0, 5.0);
            double before = fixture.health(target);

            DamageResult result = fixture.pipeline.meleeAttack(attacker, target);

            assertThat(result.applied()).as("cancelled at " + stage).isFalse();
            assertThat(result.reason()).isEqualTo(RejectReason.CANCELLED);
            assertThat(fixture.health(target)).isEqualTo(before);
            assertThat(fixture.pipeline.currentShares(target)).isEmpty();
        }
    }

    @Test
    @DisplayName("a throwing interceptor is confined to its event; the fight continues")
    void faultBarrier() {
        CombatFixture fixture = new CombatFixture();
        fixture.pipeline.registerInterceptor(
                new DamageInterceptor() {
                    @Override
                    public String id() {
                        return "broken";
                    }

                    @Override
                    public PipelineStage stage() {
                        return PipelineStage.MODIFIERS;
                    }

                    @Override
                    public void intercept(DamageView damage) {
                        throw new IllegalStateException("deliberate failure");
                    }
                });

        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(500.0, 100.0, 5.0);

        DamageResult result = fixture.pipeline.meleeAttack(attacker, target);

        assertThat(result.applied()).isTrue();
        assertThat(result.finalDamage()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("an interceptor may change the damage on its way through")
    void interceptorCanChangeDamage() {
        CombatFixture fixture = new CombatFixture();
        fixture.pipeline.registerInterceptor(
                new DamageInterceptor() {
                    @Override
                    public String id() {
                        return "doubler";
                    }

                    @Override
                    public PipelineStage stage() {
                        return PipelineStage.MODIFIERS;
                    }

                    @Override
                    public void intercept(DamageView damage) {
                        damage.setRawDamage(damage.rawDamage() * 2.0);
                    }
                });

        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(500.0, 100.0, 5.0);

        assertThat(fixture.pipeline.meleeAttack(attacker, target).finalDamage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("a target with no stat holder is left alone")
    void noHolderIsLeftAlone() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);

        DamageResult result = fixture.pipeline.meleeAttack(attacker, UUID.randomUUID());

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectReason.NO_HOLDER);
    }

    @Test
    @DisplayName("a death produces exactly one event, even from two lethal hits in the same tick")
    void oneDeathEvent() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(5000.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        DamageResult first = fixture.pipeline.meleeAttack(attacker, target);
        DamageResult second = fixture.pipeline.meleeAttack(attacker, target);

        assertThat(first.lethal()).isTrue();
        assertThat(second.applied()).isFalse();
        assertThat(second.reason()).isEqualTo(RejectReason.ALREADY_DEAD);
        assertThat(fixture.deaths).hasSize(1);
    }

    @Test
    @DisplayName("the death event names the killer and carries the split")
    void deathEventContents() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(5000.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        fixture.pipeline.meleeAttack(attacker, target);

        assertThat(fixture.deaths).singleElement().satisfies(death -> {
            assertThat(death.victimId()).isEqualTo(target);
            assertThat(death.killer()).contains(attacker);
            assertThat(death.cause()).isEqualTo(DeathCause.COMBAT);
            assertThat(death.playerVictim()).isFalse();
            assertThat(death.shares().shareOf(attacker)).isEqualTo(1.0);
            assertThat(death.lootRecipient()).contains(attacker);
        });
    }

    @Test
    @DisplayName("an environmental death has no killer and an empty split")
    void environmentalDeath() {
        CombatFixture fixture = new CombatFixture();
        UUID target = fixture.mob(20.0, 0.0, 5.0);

        fixture.pipeline.environmentDamage(target, EnvironmentSource.LAVA);
        fixture.pipeline.environmentDamage(target, EnvironmentSource.LAVA);
        fixture.pipeline.environmentDamage(target, EnvironmentSource.LAVA);

        assertThat(fixture.deaths).singleElement().satisfies(death -> {
            assertThat(death.killer()).isEmpty();
            assertThat(death.cause()).isEqualTo(DeathCause.ENVIRONMENT);
            assertThat(death.shares().isEmpty()).isTrue();
        });
    }

    @Test
    @DisplayName("a player victim is marked as one, with their character id")
    void playerVictimCarriesCharacterId() {
        CombatFixture fixture = new CombatFixture();
        UUID victim = fixture.player(5.0, 0.0, 4.0);

        fixture.pipeline.kill(victim, DeathCause.ADMIN);

        assertThat(fixture.deaths).singleElement().satisfies(death -> {
            assertThat(death.playerVictim()).isTrue();
            assertThat(death.victimCharacterId()).isNotNull().isNotEqualTo(victim);
            assertThat(death.cause()).isEqualTo(DeathCause.ADMIN);
        });
    }

    @Test
    @DisplayName("environment damage ignores defence")
    void environmentIgnoresDefence() {
        CombatFixture fixture = new CombatFixture();
        UUID armoured = fixture.mob(500.0, 300.0, 5.0);
        double before = fixture.health(armoured);

        fixture.pipeline.environmentDamage(armoured, EnvironmentSource.LAVA);

        // 8.0 from the shipped configuration - not 2.0, which 300 defence would have made of it.
        assertThat(fixture.health(armoured)).isEqualTo(before - 8.0);
    }
}

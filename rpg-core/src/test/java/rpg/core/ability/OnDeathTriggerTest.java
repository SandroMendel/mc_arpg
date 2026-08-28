package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.combat.DamageInterceptor;
import rpg.core.combat.DamageType;
import rpg.core.combat.DamageView;
import rpg.core.combat.PipelineStage;
import rpg.core.stats.StatSnapshot;

/**
 * T059 - US2.4 und US2.5: Second Life fängt den tödlichen Schlag ab (FR-050 bis FR-052c).
 *
 * <p>Der Kern ist die <b>Stufenwahl</b>: der Interceptor hängt auf {@code APPLICATION}, und dort läuft
 * er <em>vor</em> dem Gesundheitsabzug. Deshalb wird der Tod verhindert statt rückgängig gemacht - es
 * gibt keinen Respawn und keinen Todesbildschirm zu unterdrücken.
 */
class OnDeathTriggerTest {

    private AbilityFixture fixture;
    private DamageInterceptor interceptor;
    private final List<UUID> saved = new ArrayList<>();
    private final List<String> healed = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withCooldownPassive();
        Logger logger = Logger.getLogger(OnDeathTriggerTest.class.getName());
        logger.setLevel(Level.OFF);

        EffectDispatcher effects = new EffectDispatcher(logger);
        effects.register(EffectType.HEAL, context -> healed.add(context.ability().id()));

        PassiveDispatcher passives =
                new PassiveDispatcher(
                        fixture.registry,
                        effects,
                        fixture.stats,
                        fixture.repository,
                        fixture.clock,
                        () -> 0.0);
        interceptor =
                PassiveInterceptors.lethalBlow(passives, fixture.stats, saved::add);
    }

    @Test
    @DisplayName("der Interceptor hängt auf APPLICATION - vor dem Gesundheitsabzug")
    void itHangsWhereTheBlowCanStillBeRefused() {
        assertThat(interceptor.stage()).isEqualTo(PipelineStage.APPLICATION);
    }

    @Test
    @DisplayName("US2.4: tödlicher Schaden wird abgewiesen, statt den Charakter zu töten")
    void aLethalBlowIsRefused() {
        fixture.stats.health = 40.0;
        ProbeDamage damage = new ProbeDamage(fixture.holder, 100.0);

        interceptor.intercept(damage);

        assertThat(damage.cancelled).as("der Schlag landet gar nicht erst").isTrue();
        assertThat(saved).containsExactly(fixture.holder);
        assertThat(healed).hasSize(1);
    }

    @Test
    @DisplayName("US2.5: innerhalb des Cooldowns stirbt er regulär")
    void withinTheCooldownHeDies() {
        fixture.stats.health = 40.0;
        interceptor.intercept(new ProbeDamage(fixture.holder, 100.0));
        saved.clear();

        fixture.stats.health = 40.0;
        ProbeDamage second = new ProbeDamage(fixture.holder, 100.0);
        interceptor.intercept(second);

        assertThat(second.cancelled).isFalse();
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("nach Ablauf des Cooldowns rettet es wieder")
    void afterTheCooldownItSavesAgain() {
        fixture.stats.health = 40.0;
        interceptor.intercept(new ProbeDamage(fixture.holder, 100.0));

        fixture.clock.advance(Duration.ofMinutes(11));
        fixture.stats.health = 40.0;
        ProbeDamage later = new ProbeDamage(fixture.holder, 100.0);
        interceptor.intercept(later);

        assertThat(later.cancelled).isTrue();
    }

    @Test
    @DisplayName("überlebbarer Schaden verbraucht die Chance NICHT")
    void survivableDamageDoesNotBurnTheChance() {
        fixture.stats.health = 500.0;
        ProbeDamage scratch = new ProbeDamage(fixture.holder, 100.0);

        interceptor.intercept(scratch);

        assertThat(scratch.cancelled).isFalse();
        assertThat(saved).as("sonst wäre die Rettung nach dem ersten Kratzer weg").isEmpty();
    }

    @Test
    @DisplayName("ohne die Fähigkeit passiert nichts")
    void withoutTheAbilityNothingHappens() {
        fixture.unlocked.clear();
        fixture.stats.health = 40.0;
        ProbeDamage damage = new ProbeDamage(fixture.holder, 100.0);

        interceptor.intercept(damage);

        assertThat(damage.cancelled).isFalse();
        assertThat(saved).isEmpty();
    }

    /**
     * Eine Schadenssicht, die nur trägt, was dieser Interceptor liest - und mitschreibt, ob sie
     * abgewiesen wurde.
     */
    private static final class ProbeDamage implements DamageView {

        private final UUID targetId;
        private final double amount;
        private boolean cancelled;

        ProbeDamage(UUID targetId, double amount) {
            this.targetId = targetId;
            this.amount = amount;
        }

        @Override
        public Optional<UUID> attackerId() {
            return Optional.empty();
        }

        @Override
        public UUID targetId() {
            return targetId;
        }

        @Override
        public DamageType type() {
            return DamageType.PHYSICAL;
        }

        @Override
        public Optional<rpg.core.combat.EnvironmentSource> environmentSource() {
            return Optional.empty();
        }

        @Override
        public rpg.core.combat.DamageOrigin origin() {
            return rpg.core.combat.DamageOrigin.MELEE;
        }

        @Override
        public double factor() {
            return 1.0;
        }

        @Override
        public double rawDamage() {
            return amount;
        }

        @Override
        public double finalDamage() {
            return amount;
        }

        @Override
        public Optional<StatSnapshot> attackerSnapshot() {
            return Optional.empty();
        }

        @Override
        public StatSnapshot targetSnapshot() {
            return new StatSnapshot(new double[rpg.core.stats.Attribute.count()], 1L);
        }

        @Override
        public PipelineStage stage() {
            return PipelineStage.APPLICATION;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setRawDamage(double value) {}

        @Override
        public void setFinalDamage(double value) {}

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}

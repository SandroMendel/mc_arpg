package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.effect.EffectContext;
import rpg.core.ability.effect.ShieldEffect;
import rpg.core.combat.DamageInterceptor;
import rpg.core.combat.DamageType;
import rpg.core.combat.DamageView;
import rpg.core.combat.PipelineStage;
import rpg.core.stats.StatSnapshot;

/**
 * Der Schild und die Stelle, an der er tatsächlich etwas abnimmt.
 *
 * <p><b>Warum es diesen Test gibt.</b> {@code absorb} war geschrieben, geprüft und wurde nie
 * aufgerufen. Der Vorrat füllte sich bei jedem Wirken, der Kommentar an der Verdrahtung sagte „die
 * Pipeline muss ihn fragen können" - und niemand fragte. Block und Magieschild waren acht Sekunden
 * lang nichts, in einem Zustand, den kein Modultest bemerken konnte: die Klasse für sich war korrekt.
 *
 * <p>Geprüft wird deshalb nicht die Rechnung, sondern der <b>Abnehmer</b>: dass es ihn gibt, dass er
 * auf der richtigen Stufe hängt und dass er den Schaden wirklich kleiner macht.
 */
class ShieldAbsorptionTest {

    private AbilityFixture fixture;
    private ShieldEffect shields;
    private DamageInterceptor interceptor;
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
        shields = new ShieldEffect(java.time.Clock.systemUTC());
        interceptor = shields.interceptor();
    }

    @Test
    @DisplayName("der Schild hängt auf APPLICATION - dort, wo Leben noch nicht abgezogen ist")
    void itHangsWhereHealthHasNotBeenTakenYet() {
        assertThat(interceptor.stage()).isEqualTo(PipelineStage.APPLICATION);
    }

    @Test
    @DisplayName("ein Treffer wird kleiner - genau das fehlte")
    void aHitIsReduced() {
        give(60.0, null);
        Probe hit = new Probe(target, DamageType.PHYSICAL, 25.0);

        interceptor.intercept(hit);

        assertThat(hit.finalDamage).as("25 vom Vorrat genommen, nichts kommt durch").isZero();
    }

    @Test
    @DisplayName("was über den Vorrat hinausgeht, kommt durch")
    void whatExceedsThePoolGetsThrough() {
        give(20.0, null);
        Probe hit = new Probe(target, DamageType.PHYSICAL, 50.0);

        interceptor.intercept(hit);

        assertThat(hit.finalDamage).isEqualTo(30.0);
    }

    @Test
    @DisplayName("der Block des Warriors lässt Magie durch und bleibt danach stehen")
    void aPhysicalShieldIgnoresMagicAndSurvivesIt() {
        give(60.0, DamageType.PHYSICAL);
        Probe fireball = new Probe(target, DamageType.MAGIC, 25.0);

        interceptor.intercept(fireball);

        assertThat(fireball.finalDamage).as("ungebremst hindurch").isEqualTo(25.0);
        assertThat(shields.remaining(target, Instant.now()))
                .as("und der Vorrat ist unangetastet")
                .isEqualTo(60.0);
    }

    @Test
    @DisplayName("ohne Schild bleibt der Schaden, wie er war")
    void withoutAShieldNothingChanges() {
        Probe hit = new Probe(target, DamageType.PHYSICAL, 25.0);

        interceptor.intercept(hit);

        assertThat(hit.finalDamage).isEqualTo(25.0);
        assertThat(hit.writes).as("nicht einmal geschrieben").isZero();
    }

    /** Legt einen Vorrat an, so wie es der Effekt beim Wirken tut. */
    private void give(double amount, DamageType filter) {
        shields.apply(
                new EffectContext(
                        fixture.strike(),
                        new EffectSpec(
                                EffectType.SHIELD,
                                amount,
                                0.0,
                                Duration.ofSeconds(8),
                                null,
                                1,
                                null,
                                null,
                                filter,
                                null,
                                null,
                                0.0,
                                null,
                                0.0,
                                false),
                        UUID.randomUUID(),
                        java.util.List.of(target),
                        1,
                        new StatSnapshot(new double[rpg.core.stats.Attribute.count()], 1L),
                        null));
    }

    /** Ein Schlag, der sich merkt, was mit ihm gemacht wurde. */
    private static final class Probe implements DamageView {

        private final UUID targetId;
        private final DamageType type;
        private double finalDamage;
        private int writes;

        Probe(UUID targetId, DamageType type, double amount) {
            this.targetId = targetId;
            this.type = type;
            this.finalDamage = amount;
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
            return type;
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
            return finalDamage;
        }

        @Override
        public double finalDamage() {
            return finalDamage;
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
            return false;
        }

        @Override
        public void setRawDamage(double value) {}

        @Override
        public void setFinalDamage(double value) {
            finalDamage = value;
            writes++;
        }

        @Override
        public void cancel() {}
    }
}

package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.ability.effect.EffectContext;
import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.ability.effect.LifestealEffect;
import rpg.core.combat.DamageType;

/**
 * T057 bis T059 - US2: passive Fähigkeiten wirken, ohne ausgelöst zu werden.
 *
 * <p>Vier Zusagen stehen hier auf dem Prüfstand, und jede hat eine Gegenprobe: Lifesteal heilt den
 * <b>mitigierten</b> Betrag und nicht den rohen; eine Wahrscheinlichkeit wird <b>einmal je Auslösung</b>
 * gewürfelt und nicht je Effekt; ein Cooldown gilt auch ohne Spieler dahinter; und eine abgeschaltete
 * Passive tut gar nichts.
 */
class PassiveTriggerTest {

    private AbilityFixture fixture;
    private PassiveDispatcher passives;
    private EffectDispatcher effects;
    private final List<Double> healed = new ArrayList<>();
    private double roll = 0.0;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
        Logger logger = Logger.getLogger(PassiveTriggerTest.class.getName());
        logger.setLevel(Level.OFF);

        effects = new EffectDispatcher(logger);
        // Der echte Lifesteal-Effekt gegen eine Stat-Engine, die die Heilung aufzeichnet.
        effects.register(EffectType.LIFESTEAL, new LifestealEffect(fixture.stats));
        effects.register(EffectType.HEAL, context -> healed.add(context.value()));

        passives =
                new PassiveDispatcher(
                        fixture.registry,
                        effects,
                        fixture.stats,
                        fixture.repository,
                        fixture.clock,
                        () -> roll);
    }

    @Nested
    @DisplayName("US2.1 und US2.2 - Lifesteal beim Austeilen")
    class Lifesteal {

        @Test
        @DisplayName("US2.1: geheilt wird der TATSÄCHLICH zugefügte Betrag, nicht der rohe")
        void healsTheMitigatedAmount() {
            fixture.stats.health = 500.0;

            // 8 % von 200 mitigiertem Schaden. Der Rohschaden wäre höher gewesen - er steht hier gar
            // nicht zur Verfügung, und genau das ist der Punkt der Stufenwahl (research.md R6).
            fire(AbilityTrigger.ON_DAMAGE_DEALT, 200.0);

            assertThat(fixture.stats.health).isEqualTo(500.0 + 200.0 * 0.08);
        }

        @Test
        @DisplayName("US2.2: Heilung über das Maximum verpufft, ohne Fehler")
        void overhealingIsSilent() {
            fixture.stats.health = fixture.stats.maxHealth;

            fire(AbilityTrigger.ON_DAMAGE_DEALT, 200.0);

            assertThat(fixture.stats.health).isEqualTo(fixture.stats.maxHealth);
        }

        @Test
        @DisplayName("null Schaden heilt nichts - ein abgewehrter Schlag ist keine Quelle")
        void zeroDamageHealsNothing() {
            fixture.stats.health = 500.0;

            fire(AbilityTrigger.ON_DAMAGE_DEALT, 0.0);

            assertThat(fixture.stats.health).isEqualTo(500.0);
        }

        @Test
        @DisplayName("US2.3: eine nicht freigeschaltete Passive wirkt nicht")
        void aLockedPassiveDoesNothing() {
            fixture.unlocked.clear();
            fixture.stats.health = 500.0;

            fire(AbilityTrigger.ON_DAMAGE_DEALT, 200.0);

            assertThat(fixture.stats.health).isEqualTo(500.0);
        }

        @Test
        @DisplayName("ein anderer Trigger lässt sie kalt")
        void anotherTriggerDoesNotFireIt() {
            fixture.stats.health = 500.0;

            fire(AbilityTrigger.ON_KILL, 200.0);

            assertThat(fixture.stats.health).isEqualTo(500.0);
        }
    }

    @Nested
    @DisplayName("Wahrscheinlichkeit, Cooldown und Schalter")
    class Gates {

        @Test
        @DisplayName("FR-049: die Wahrscheinlichkeit wird EINMAL je Auslösung gewürfelt, nicht je Effekt")
        void theChanceIsRolledOncePerTrigger() throws Exception {
            // Eine Passive mit ZWEI Effekten und einer Chance von 50 %. Würfelte der Dispatcher je
            // Effekt, könnte die Hälfte greifen - ein Zustand, den kein Spieler deuten und kein Test
            // festnageln kann.
            AbilityFixture chancy = AbilityFixture.withTwoEffectPassive();
            List<String> applied = new ArrayList<>();
            EffectDispatcher recording = new EffectDispatcher(quiet());
            recording.register(EffectType.HEAL, context -> applied.add("heal"));
            recording.register(EffectType.MANA_RESTORE, context -> applied.add("mana"));
            double[] rolls = {0.9, 0.1};
            int[] index = {0};
            PassiveDispatcher dispatcher =
                    new PassiveDispatcher(
                            chancy.registry,
                            recording,
                            chancy.stats,
                            chancy.repository,
                            chancy.clock,
                            () -> rolls[index[0]++]);

            // Erster Wurf 0.9 gegen eine Chance von 0.5: verfehlt, KEIN Effekt.
            dispatcher.fire(chancy.holder, AbilityTrigger.ON_KILL, null, null);
            assertThat(applied).isEmpty();
            assertThat(index[0]).as("genau ein Wurf, obwohl es zwei Effekte sind").isEqualTo(1);

            // Zweiter Wurf 0.1: trifft, BEIDE Effekte.
            dispatcher.fire(chancy.holder, AbilityTrigger.ON_KILL, null, null);
            assertThat(applied).containsExactly("heal", "mana");
            assertThat(index[0]).isEqualTo(2);
        }

        @Test
        @DisplayName("FR-048: eine Passive mit Cooldown greift danach nicht sofort wieder")
        void aPassiveCooldownHolds() throws Exception {
            AbilityFixture guarded = AbilityFixture.withCooldownPassive();
            List<String> applied = new ArrayList<>();
            EffectDispatcher recording = new EffectDispatcher(quiet());
            recording.register(EffectType.HEAL, context -> applied.add("heal"));
            PassiveDispatcher dispatcher =
                    new PassiveDispatcher(
                            guarded.registry,
                            recording,
                            guarded.stats,
                            guarded.repository,
                            guarded.clock,
                            () -> 0.0);

            assertThat(dispatcher.fire(guarded.holder, AbilityTrigger.ON_DEATH, null, null))
                    .isTrue();
            assertThat(dispatcher.fire(guarded.holder, AbilityTrigger.ON_DEATH, null, null))
                    .as("innerhalb des Cooldowns stirbt er regulär")
                    .isFalse();

            guarded.clock.advance(Duration.ofMinutes(11));

            assertThat(dispatcher.fire(guarded.holder, AbilityTrigger.ON_DEATH, null, null))
                    .as("danach wieder")
                    .isTrue();
            assertThat(applied).hasSize(2);
        }

        @Test
        @DisplayName("FR-052d: eine abgeschaltete Passive tut gar nichts")
        void aDisabledPassiveDoesNothing() throws Exception {
            AbilityFixture togglable = AbilityFixture.withTogglePassive();
            List<String> applied = new ArrayList<>();
            EffectDispatcher recording = new EffectDispatcher(quiet());
            recording.register(EffectType.HEAL, context -> applied.add("heal"));
            PassiveDispatcher dispatcher =
                    new PassiveDispatcher(
                            togglable.registry,
                            recording,
                            togglable.stats,
                            togglable.repository,
                            togglable.clock,
                            () -> 0.0);

            togglable.registry.put(
                    togglable
                            .registry
                            .stateOf(togglable.character, "probe.toggle")
                            .withToggle(ToggleState.OFF));

            assertThat(dispatcher.fire(togglable.holder, AbilityTrigger.ON_KILL, null, null))
                    .isFalse();
            assertThat(applied).isEmpty();
        }
    }

    @Nested
    @DisplayName("Schadenstyp-Filter")
    class TypeFilter {

        @Test
        @DisplayName("FR-016a: Magic Life weicht magischem Schaden aus, physischem nicht")
        void theFilterDecides() throws Exception {
            AbilityFixture evasive = AbilityFixture.withMagicEvade();
            List<String> avoided = new ArrayList<>();
            EffectDispatcher recording = new EffectDispatcher(quiet());
            recording.register(EffectType.EVADE, context -> avoided.add("avoided"));
            PassiveDispatcher dispatcher =
                    new PassiveDispatcher(
                            evasive.registry,
                            recording,
                            evasive.stats,
                            evasive.repository,
                            evasive.clock,
                            () -> 0.0);

            assertThat(
                            dispatcher.fire(
                                    evasive.holder,
                                    AbilityTrigger.ON_DAMAGE_TAKEN,
                                    DamageType.PHYSICAL,
                                    null))
                    .as("gegen ein Schwert wirkt es nicht")
                    .isFalse();
            assertThat(avoided).isEmpty();

            assertThat(
                            dispatcher.fire(
                                    evasive.holder,
                                    AbilityTrigger.ON_DAMAGE_TAKEN,
                                    DamageType.MAGIC,
                                    null))
                    .isTrue();
            assertThat(avoided).containsExactly("avoided");
        }
    }

    // --- helpers ---

    private static Logger quiet() {
        Logger logger = Logger.getLogger(PassiveTriggerTest.class.getName() + ".quiet");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private void fire(AbilityTrigger trigger, double damage) {
        passives.fire(
                fixture.holder,
                trigger,
                DamageType.PHYSICAL,
                new EffectContext.TriggerData(damage, () -> {}));
    }
}

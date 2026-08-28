package rpg.core.ability;

import java.util.Objects;
import java.util.UUID;

import rpg.core.ability.effect.EffectContext;
import rpg.core.combat.DamageInterceptor;
import rpg.core.combat.DamageView;
import rpg.core.combat.PipelineStage;
import rpg.core.stats.StatEngine;

/**
 * The three hooks that connect the passive triggers to B05 (research.md R6).
 *
 * <p><b>B05 is not extended for any of them.</b> Every hook already exists, and which stage each one
 * uses is not interchangeable:
 *
 * <ul>
 *   <li><b>Taking damage</b> hangs on {@code MODIFIERS}, where the damage can still be refused -
 *       evasion has to prevent it, and by application it has landed.
 *   <li><b>Dealing damage</b> hangs on {@code APPLICATION}, where the mitigated amount is known -
 *       lifesteal on the raw number would heal a warrior for more than the target ever took.
 *   <li><b>Dying</b> hangs on {@code APPLICATION} too, which runs <em>before</em> health is deducted.
 *       That is what lets Second Life refuse the blow instead of resurrecting afterwards.
 * </ul>
 */
public final class PassiveInterceptors {

    private PassiveInterceptors() {}

    /** Evasion and anything else that reacts to being hit (FR-046, {@code ON_DAMAGE_TAKEN}). */
    public static DamageInterceptor damageTaken(PassiveDispatcher passives) {
        Objects.requireNonNull(passives, "passives");
        return new DamageInterceptor() {
            @Override
            public String id() {
                return "abilities.on-damage-taken";
            }

            @Override
            public PipelineStage stage() {
                return PipelineStage.MODIFIERS;
            }

            @Override
            public void intercept(DamageView damage) {
                passives.fire(
                        damage.targetId(),
                        AbilityTrigger.ON_DAMAGE_TAKEN,
                        damage.type(),
                        damage.origin(),
                        new EffectContext.TriggerData(
                                damage.rawDamage(),
                                damage::cancel,
                                // The share comes off the RAW number, which is what this stage deals
                                // in - the mitigated one does not exist yet and is computed from it
                                // afterwards. Read fresh on every call rather than captured, so two
                                // mitigations on one hit each take their share of what is left.
                                share ->
                                        damage.setRawDamage(damage.rawDamage() * (1.0 - share)),
                                damage.attackerId().orElse(null)));
            }
        };
    }

    /** Lifesteal and anything else that reacts to landing a hit ({@code ON_DAMAGE_DEALT}). */
    public static DamageInterceptor damageDealt(PassiveDispatcher passives) {
        Objects.requireNonNull(passives, "passives");
        return new DamageInterceptor() {
            @Override
            public String id() {
                return "abilities.on-damage-dealt";
            }

            @Override
            public PipelineStage stage() {
                return PipelineStage.APPLICATION;
            }

            @Override
            public void intercept(DamageView damage) {
                // No attacker on environmental damage - nobody dealt it, so nothing fires.
                damage.attackerId()
                        .ifPresent(
                                attacker ->
                                        passives.fire(
                                                attacker,
                                                AbilityTrigger.ON_DAMAGE_DEALT,
                                                damage.type(),
                                                damage.origin(),
                                                new EffectContext.TriggerData(
                                                        damage.finalDamage(),
                                                        damage::cancel,
                                                        damage.targetId())));
            }
        };
    }

    /**
     * Second Life: refuse the lethal blow and stand the character back up (FR-050, FR-052c).
     *
     * <p>Runs on {@code APPLICATION}, before health is deducted, so the death never happens - there is
     * no respawn and no death screen to suppress afterwards.
     *
     * <p><b>Unreachable by an administrative kill</b>, and that is a property of the path rather than
     * a rule written here: {@code CombatPipeline.kill} runs without formula and without attribution
     * and touches no interceptor at all (FR-051).
     */
    public static DamageInterceptor lethalBlow(
            PassiveDispatcher passives, StatEngine stats, SecondLifeHandler handler) {
        Objects.requireNonNull(passives, "passives");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(handler, "handler");
        return new DamageInterceptor() {
            @Override
            public String id() {
                return "abilities.on-death";
            }

            @Override
            public PipelineStage stage() {
                return PipelineStage.APPLICATION;
            }

            @Override
            public void intercept(DamageView damage) {
                UUID targetId = damage.targetId();
                double remaining = stats.resources(targetId).currentHealth();
                if (damage.finalDamage() < remaining) {
                    // Survivable. Asking the passives here would burn the chance on every scratch.
                    return;
                }
                boolean saved =
                        passives.fire(
                                targetId,
                                AbilityTrigger.ON_DEATH,
                                damage.type(),
                                damage.origin(),
                                new EffectContext.TriggerData(damage.finalDamage(), damage::cancel));
                if (!saved) {
                    return;
                }
                // The save IS the cancellation, and it happens here rather than in an effect. An
                // ON_DEATH ability's effects say what the character gets back; whether the blow lands
                // at all is not theirs to decide, and leaving it to them would mean every future
                // ON_DEATH ability has to remember to cancel or it silently does not save anybody.
                damage.cancel();
                handler.onSaved(targetId);
            }
        };
    }

    /**
     * What happens around a character that was saved - the title, the sound, the teleport back to
     * where it fell (FR-052c).
     *
     * <p>An interface rather than code here, because all three need the world and this block's rules
     * do not. The platform installs the real one; without it the save still works and simply looks
     * like nothing.
     */
    @FunctionalInterface
    public interface SecondLifeHandler {
        void onSaved(UUID characterId);

        /** Does nothing. The default until the platform installs one. */
        static SecondLifeHandler none() {
            return characterId -> {};
        }
    }
}

package rpg.core.ability;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import rpg.core.classes.AbilityKind;
import rpg.core.message.MessageKey;

/**
 * One ability, as loaded and validated - immutable and shared by every player (FR-002).
 *
 * <p>Eighteen of these exist for the whole server. That is why what an ability <em>does</em> is a
 * list of {@link EffectSpec} rather than an overridden method: a class per ability would have meant
 * eighteen classes and a nineteenth for the next one, and SC-001 - a new ability from configuration
 * alone - could not be true.
 *
 * @param id unique across all abilities, opaque to B07 which only names it
 * @param kind active or passive; must match what the class binding says (FR-007)
 * @param displayNameKey a message key, never text (FR-009)
 * @param manaCost zero for a passive - a passive is not triggered and costs nothing (FR-047)
 * @param cooldown zero or more; for a passive it gates how often its trigger may fire (FR-048)
 * @param castTime zero means it takes effect in the same tick, with no cast state at all (FR-044)
 * @param sustained whether it runs for a duration and can be ended by a second right-click (FR-045a)
 * @param duration required when {@code sustained}
 * @param charges more than one means the cooldown only starts once the last is spent (FR-045i)
 * @param chargeWindow required above one charge - after it the pool springs back (FR-045j)
 * @param requiresBehindTarget the hit had to land from behind; the rogue's Sneaky Backstab (FR-052a)
 * @param openWorldOnly not effective inside instances - <b>unchecked until B09</b> (FR-052b)
 * @param playerToggle whether the player may switch it off; the mage's Rise &amp; Fall (FR-052d)
 * @param interruptOnMove whether moving cancels the cast (FR-043)
 * @param trigger required for a passive, forbidden for an active
 * @param chance probability its trigger takes hold, in {@code [0, 1]} (FR-049)
 * @param target how it finds what it acts on
 * @param effects at least one - an ability without effect is always a mistake
 * @param maxRank the ceiling the rank may reach
 * @param item the vanilla material carrying it on the hotbar; required for an active, an optional
 *     marker for a passive (FR-003)
 */
public record Ability(
        String id,
        AbilityKind kind,
        MessageKey displayNameKey,
        double manaCost,
        Duration cooldown,
        Duration castTime,
        boolean sustained,
        Duration duration,
        int charges,
        Duration chargeWindow,
        boolean requiresBehindTarget,
        boolean openWorldOnly,
        boolean playerToggle,
        boolean interruptOnMove,
        AbilityTrigger trigger,
        double chance,
        TargetSpec target,
        List<EffectSpec> effects,
        int maxRank,
        String item) {

    public Ability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(castTime, "castTime");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(effects, "effects");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ability id must not be blank");
        }

        // V8
        requireNonNegative(id, "mana-cost", manaCost);
        requireNonNegative(id, "cooldown", cooldown);
        requireNonNegative(id, "cast-time", castTime);

        // V9, V10
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException(id + ": chance must lie within [0, 1], but was " + chance);
        }
        if (maxRank < 1) {
            throw new IllegalArgumentException(id + ": max-rank must be at least 1, but was " + maxRank);
        }

        // V13
        effects = List.copyOf(effects);
        if (effects.isEmpty()) {
            throw new IllegalArgumentException(
                    id + ": needs at least one effect - an ability without one is always a mistake");
        }

        validateKind(id, kind, trigger, manaCost, castTime, item);
        validateSustained(id, sustained, kind, duration);
        validateCharges(id, charges, chargeWindow);
        validateConditions(id, kind, requiresBehindTarget, trigger, playerToggle);
    }

    /** V6 and V7 - what an active needs and what a passive must not carry. */
    private static void validateKind(
            String id,
            AbilityKind kind,
            AbilityTrigger trigger,
            double manaCost,
            Duration castTime,
            String item) {
        if (kind == AbilityKind.ACTIVE) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException(
                        id + ": an active ability needs an item - without one it cannot be triggered");
            }
            if (trigger != null) {
                throw new IllegalArgumentException(
                        id + ": an active ability has no trigger - it is triggered by the player");
            }
            return;
        }
        if (trigger == null) {
            throw new IllegalArgumentException(
                    id + ": a passive ability needs a trigger - without one it would never take effect");
        }
        // A field that is never read is a misunderstanding, not a harmless surplus.
        if (manaCost > 0.0) {
            throw new IllegalArgumentException(
                    id + ": a passive ability costs no mana, but mana-cost was " + manaCost);
        }
        if (!castTime.isZero()) {
            throw new IllegalArgumentException(
                    id + ": a passive ability has no cast time, but cast-time was " + castTime);
        }
    }

    /** V31 and V32. */
    private static void validateSustained(
            String id, boolean sustained, AbilityKind kind, Duration duration) {
        if (!sustained) {
            return;
        }
        if (kind != AbilityKind.ACTIVE) {
            throw new IllegalArgumentException(
                    id
                            + ": only an active ability can be sustained - a passive has no slot on which"
                            + " a second right-click could end it");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    id + ": a sustained ability needs a positive duration - without one it never ends");
        }
    }

    /** V33. */
    private static void validateCharges(String id, int charges, Duration chargeWindow) {
        if (charges < 1) {
            throw new IllegalArgumentException(id + ": charges must be at least 1, but was " + charges);
        }
        if (charges > 1) {
            if (chargeWindow == null || chargeWindow.isZero() || chargeWindow.isNegative()) {
                throw new IllegalArgumentException(
                        id
                                + ": more than one charge needs a positive charge-window - without it the"
                                + " pool would never come back");
            }
        } else if (chargeWindow != null) {
            throw new IllegalArgumentException(id + ": charge-window means nothing at a single charge");
        }
    }

    /** V34 and V35. */
    private static void validateConditions(
            String id,
            AbilityKind kind,
            boolean requiresBehindTarget,
            AbilityTrigger trigger,
            boolean playerToggle) {
        if (requiresBehindTarget && trigger != AbilityTrigger.ON_DAMAGE_DEALT) {
            throw new IllegalArgumentException(
                    id
                            + ": requires-behind-target only works with ON_DAMAGE_DEALT - the position is"
                            + " only knowable at the moment of the hit");
        }
        if (playerToggle && kind != AbilityKind.PASSIVE) {
            throw new IllegalArgumentException(
                    id
                            + ": only a passive ability can be toggled - an active one is switched off by"
                            + " not triggering it");
        }
    }

    private static void requireNonNegative(String id, String field, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(id + ": " + field + " must not be negative, was " + value);
        }
    }

    private static void requireNonNegative(String id, String field, Duration value) {
        if (value.isNegative()) {
            throw new IllegalArgumentException(id + ": " + field + " must not be negative, was " + value);
        }
    }

    public boolean isActive() {
        return kind == AbilityKind.ACTIVE;
    }

    /** Whether triggering this creates a cast state at all - a zero cast time does not (FR-044). */
    public boolean hasCastTime() {
        return !castTime.isZero();
    }

    /** Whether any of its effects applies repeatedly, and therefore joins the shared sweep. */
    public boolean hasPeriodicEffect() {
        return effects.stream().anyMatch(EffectSpec::isPeriodic);
    }
}

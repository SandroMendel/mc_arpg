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
 * @param descriptionKey a message key for the line under the name on the hotbar item, or {@code null}
 *     for an ability that carries none. A key, never text, for the same reason the name is one
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
 * @param triggers what makes a passive fire; at least one for a passive, none for an active.
 *     <b>More than one is allowed</b>: the warrior's Rage builds on damage dealt AND taken, and a
 *     single trigger could not say that (FR-016b)
 * @param chance probability its trigger takes hold, in {@code [0, 1]} (FR-049)
 * @param target how it finds what it acts on
 * @param effects at least one - an ability without effect is always a mistake
 * @param maxRank the ceiling the rank may reach
 * @param items the vanilla materials carrying it on the hotbar. Exactly one for an active - it is
 *     the slot the player clicks. For a passive they are markers and there may be several: the
 *     mage's Rise & Fall shows a Wind Charge for the jump and a Slow Fall Potion for the fall, and
 *     with a three-way toggle those two are what the player reads (FR-003)
 */
public record Ability(
        String id,
        AbilityKind kind,
        MessageKey displayNameKey,
        MessageKey descriptionKey,
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
        java.util.Set<AbilityTrigger> triggers,
        double chance,
        TargetSpec target,
        List<EffectSpec> effects,
        int maxRank,
        /**
         * What one rank costs, exactly as configured and <b>not interpreted here</b>.
         *
         * <p>The same arrangement B07 uses for its equipment tiers: the price lives with whoever
         * charges it, and B08b reads it (ADR-027, FR-053). This block knows nothing about coins -
         * it carries the map through and hands it over.
         *
         * <p>An empty map means the rank is free (FR-054).
         */
        java.util.Map<String, Object> rankCost,
        java.util.List<String> items) {

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
        items = items == null ? List.of() : List.copyOf(items);
        rankCost = rankCost == null ? java.util.Map.of() : java.util.Map.copyOf(rankCost);
        effects = List.copyOf(effects);
        if (effects.isEmpty()) {
            throw new IllegalArgumentException(
                    id + ": needs at least one effect - an ability without one is always a mistake");
        }

        triggers = triggers == null ? java.util.Set.of() : java.util.Set.copyOf(triggers);
        validateKind(id, kind, triggers, manaCost, castTime, items);
        validateSustained(id, sustained, kind, duration);
        validateCharges(id, charges, chargeWindow);
        validateConditions(id, kind, requiresBehindTarget, triggers, playerToggle);
    }

    /** V6 and V7 - what an active needs and what a passive must not carry. */
    private static void validateKind(
            String id,
            AbilityKind kind,
            java.util.Set<AbilityTrigger> triggers,
            double manaCost,
            Duration castTime,
            java.util.List<String> items) {
        if (kind == AbilityKind.ACTIVE) {
            if (items.size() != 1 || items.get(0).isBlank()) {
                throw new IllegalArgumentException(
                        id
                                + ": an active ability needs exactly one item - it is the slot the"
                                + " player clicks, and two would be two slots for one ability");
            }
            if (!triggers.isEmpty()) {
                throw new IllegalArgumentException(
                        id + ": an active ability has no trigger - it is triggered by the player");
            }
            return;
        }
        if (triggers.isEmpty()) {
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
            java.util.Set<AbilityTrigger> triggers,
            boolean playerToggle) {
        if (requiresBehindTarget && !triggers.equals(java.util.Set.of(AbilityTrigger.ON_DAMAGE_DEALT))) {
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

    /**
     * The first material carrying it, or {@code null} for a passive without a marker.
     *
     * <p>For an active this is <em>the</em> item - there is exactly one, and V6 enforces that.
     */
    public String item() {
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * Whether this passive fires on that trigger.
     *
     * <p>Asked instead of comparing to a single value, because an ability may name several - and a
     * caller that compared would silently miss the second one.
     */
    public boolean firesOn(AbilityTrigger trigger) {
        return triggers.contains(trigger);
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

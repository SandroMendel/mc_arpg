package rpg.core.zone;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DeathCause;
import rpg.core.event.EventBus;

/**
 * Leaving the server while in combat is a death (ADR-030, FR-038 to FR-043).
 *
 * <p><b>Why the rule is exactly as harsh as dying and no harsher.</b> Death costs little in this game
 * - no experience, no items. Whoever pays more for fleeing than for dying stands still and dies
 * instead, and the punishment would have achieved the opposite of what it is for. Equal is the right
 * height: the gain of running is escaping death, so if running brings exactly death, the gain is
 * nothing.
 *
 * <p><b>The combat state is read from B05, never rebuilt.</b> The eight seconds live in
 * {@code combat.yml} and belong there; a second number for the same purpose is what FR-039 forbids.
 * This class asks a question and does not own a clock.
 *
 * <p><b>A dropped connection is treated exactly like a deliberate quit</b> (FR-043). There is no
 * distinction here for a client to produce - the alternative would be a rule anybody could avoid by
 * pulling a cable, which is worse than no rule (Constitution VI).
 *
 * <p><b>What it does not do is teleport.</b> The player is already leaving; a teleport in that moment
 * is not reliable on Paper, and the position may be written after the event. So the consequence is
 * stored as a pending respawn against the region they were in, and the next login applies it
 * (research.md R11). The marker is a zone key rather than a coordinate, because the server may
 * restart and the map may change in between.
 */
public final class CombatLogoutRule {

    private final BooleanSupplier enabled;
    private final Predicate<UUID> inCombat;
    private final ZonePresence presence;
    private final ZoneStateStore store;
    private final EventBus eventBus;

    public CombatLogoutRule(
            BooleanSupplier enabled,
            Predicate<UUID> inCombat,
            ZonePresence presence,
            ZoneStateStore store,
            EventBus eventBus) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.inCombat = Objects.requireNonNull(inCombat, "inCombat");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.store = Objects.requireNonNull(store, "store");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /**
     * Applies the rule to a session that is ending.
     *
     * <p><b>Must run before the session is torn down</b>, because the combat state and the placement
     * are both keyed by the holder and both go away with it.
     *
     * @return {@code true} when the character died by leaving
     */
    public boolean onSessionEnding(UUID holderId, UUID characterId) {
        if (!enabled.getAsBoolean()) {
            // combat-logout: none. Switched off without a code change (FR-042).
            return false;
        }
        if (!inCombat.test(holderId)) {
            // Past the grace window from combat.yml - gathering yourself after a fight and then
            // logging out is not fleeing.
            return false;
        }

        // The region they were in, or null out in the wilderness. Either way the next login knows
        // what to do: a null marker still means "you died", it just ends at the fallback point.
        store.setPendingRespawn(characterId, presence.zoneKeyOf(holderId));

        eventBus.publish(
                new CombatDeathEvent(holderId, characterId, null, DeathCause.LOGOUT, null, true));
        return true;
    }
}

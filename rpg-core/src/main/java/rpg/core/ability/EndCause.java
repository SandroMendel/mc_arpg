package rpg.core.ability;

/**
 * Why a running ability stopped (FR-042, FR-045g).
 *
 * <p>Recorded rather than reduced to a boolean because the reasons differ in what they mean to the
 * player, and B13 will want to say so: ending a whirl on purpose is not the same as having it beaten
 * out of you.
 */
public enum EndCause {

    /** The player pressed the slot again - the ordinary way to stop something (FR-045c). */
    PLAYER,

    /** Its duration ran out, or the cast completed. */
    EXPIRED,

    /** Took damage above zero after mitigation (FR-042). */
    DAMAGE_TAKEN,

    /** Switched to another hotbar slot. */
    SLOT_CHANGED,

    /** Moved, and this ability said moving cancels it (FR-043). */
    MOVED,

    /** Dealt damage - the one that ends invisibility. */
    DAMAGE_DEALT,

    DIED,

    CHARACTER_SWITCHED,

    DISCONNECTED;

    /**
     * Whether this cause means the ability never took effect, and therefore costs nothing.
     *
     * <p><b>Only for the winding-up phase</b>, and the phase decides, not this - a whirl beaten out of
     * a warrior mid-spin has still spun. This only says that none of these causes is itself a reason
     * to charge for something that was already delivered.
     */
    public boolean isInterruption() {
        return this != EXPIRED;
    }
}

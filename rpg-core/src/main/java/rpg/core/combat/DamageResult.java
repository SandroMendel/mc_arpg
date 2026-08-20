package rpg.core.combat;

/**
 * The outcome of one damage event, as the caller sees it.
 *
 * @param applied whether damage was actually dealt
 * @param finalDamage the amount after defence; {@code 0.0} when rejected
 * @param lethal whether this event brought the target to zero health
 * @param reason {@link RejectReason#NONE} on success
 */
public record DamageResult(boolean applied, double finalDamage, boolean lethal, RejectReason reason) {

    private static final DamageResult NOT_PERMITTED = rejected(RejectReason.NOT_PERMITTED);
    private static final DamageResult TOO_SOON = rejected(RejectReason.ATTACK_TOO_SOON);
    private static final DamageResult NOT_READY = rejected(RejectReason.SESSION_NOT_READY);
    private static final DamageResult NO_HOLDER = rejected(RejectReason.NO_HOLDER);
    private static final DamageResult ALREADY_DEAD = rejected(RejectReason.ALREADY_DEAD);
    private static final DamageResult CANCELLED = rejected(RejectReason.CANCELLED);
    private static final DamageResult INVALID = rejected(RejectReason.INVALID_DAMAGE);

    private static DamageResult rejected(RejectReason reason) {
        return new DamageResult(false, 0.0, false, reason);
    }

    /**
     * The shared instance for a rejection.
     *
     * <p>Cached rather than built per call: a rejected swing is the common case during click spam,
     * and this block does not allocate per hit (FR-045).
     */
    public static DamageResult of(RejectReason reason) {
        return switch (reason) {
            case NOT_PERMITTED -> NOT_PERMITTED;
            case ATTACK_TOO_SOON -> TOO_SOON;
            case SESSION_NOT_READY -> NOT_READY;
            case NO_HOLDER -> NO_HOLDER;
            case ALREADY_DEAD -> ALREADY_DEAD;
            case CANCELLED -> CANCELLED;
            case INVALID_DAMAGE -> INVALID;
            case NONE -> throw new IllegalArgumentException("NONE is not a rejection");
        };
    }

    /** A successful event. */
    public static DamageResult applied(double finalDamage, boolean lethal) {
        return new DamageResult(true, finalDamage, lethal, RejectReason.NONE);
    }
}

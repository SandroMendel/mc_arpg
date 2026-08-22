package rpg.core.ability;

/**
 * How an ability finds what it acts on (FR-019).
 *
 * <p>Every mode that can return more than one target must carry a hard cap, and the cap is a
 * required field rather than a default - a forgotten line must not be indistinguishable from a
 * decision (FR-020). {@link #multiTarget()} is what the schema checks that against.
 *
 * <p>When more candidates qualify than allowed, the nearest ones win. Not a random pick: the same
 * situation has to produce the same result, or the behaviour is not testable (FR-021).
 */
public enum TargetMode {

    /** The caster. The only mode that needs no range. */
    SELF(false),

    /** Whatever lies along the view direction within range. */
    LOOK_DIRECTION(false),

    /** The single entity under the crosshair. */
    CURSOR(false),

    /** Everything within a radius of the caster. */
    RADIUS(true),

    /** Everything inside a cone in the view direction; needs an angle. */
    CONE(true),

    /** Everything along a line in the view direction. */
    LINE(true),

    /** The single closest entity. */
    NEAREST(false),

    /**
     * Hops from target to target - the mage's Lightning.
     *
     * <p>Each further target is looked for around the <b>last one hit</b>, not around the caster, and
     * nothing is hit twice (FR-019a). That is why it cannot be expressed as {@link #NEAREST} repeated:
     * the origin moves.
     */
    CHAIN(true),

    /**
     * A patch of ground picked by the crosshair - the mage's Lightning Storm.
     *
     * <p>It anchors at a point and <b>stays there</b> even if the caster walks away, which is what
     * separates it from {@link #RADIUS}. Its {@code range} is the maximum distance from the caster;
     * the area itself has its own radius (FR-019b).
     */
    GROUND_AREA(true);

    private final boolean multiTarget;

    TargetMode(boolean multiTarget) {
        this.multiTarget = multiTarget;
    }

    /** Whether this mode can return more than one target, and therefore requires a cap (FR-020). */
    public boolean multiTarget() {
        return multiTarget;
    }

    /** Whether this mode needs a range at all - only {@link #SELF} does not. */
    public boolean needsRange() {
        return this != SELF;
    }
}

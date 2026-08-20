package rpg.core.combat;

/**
 * The environmental hazards that map onto own damage (FR-012).
 *
 * <p>A type of this block's own rather than Paper's {@code DamageCause}: that one belongs to
 * {@code rpg-platform}, and letting it reach into the rules would put a Bukkit import in the one
 * module that must not have any. The translation lives in {@code VanillaDamageMapping}.
 *
 * <p>Each constant carries its configuration key, so {@code combat.yml} and this enum cannot drift
 * apart without the configuration failing to load.
 */
public enum EnvironmentSource {
    FALL("fall"),
    FIRE("fire"),
    FIRE_TICK("fire-tick"),
    LAVA("lava"),
    HOT_FLOOR("hot-floor"),
    CAMPFIRE("campfire"),
    DROWNING("drowning"),
    SUFFOCATION("suffocation"),
    CONTACT("contact"),
    BLOCK_EXPLOSION("block-explosion"),
    ENTITY_EXPLOSION("entity-explosion"),
    LIGHTNING("lightning"),
    FALLING_BLOCK("falling-block"),
    FLY_INTO_WALL("fly-into-wall"),
    FREEZE("freeze"),
    DRYOUT("dryout"),
    DRAGON_BREATH("dragon-breath"),
    SONIC_BOOM("sonic-boom"),
    WORLD_BORDER("world-border");

    private static final EnvironmentSource[] VALUES = values();

    private final String key;

    EnvironmentSource(String key) {
        this.key = key;
    }

    /** The key under {@code environment:} in {@code combat.yml}. */
    public String key() {
        return key;
    }

    /** Without the defensive copy {@code values()} makes. Must not be modified. */
    public static EnvironmentSource[] all() {
        return VALUES;
    }

    /**
     * Whether this source computes its amount from something rather than reading a flat number.
     *
     * <p>Only the fall does: its damage grows with the height fallen (FR-012c).
     */
    public boolean isComputed() {
        return this == FALL;
    }
}

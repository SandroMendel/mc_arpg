package rpg.core.stats;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The eight attributes, as one closed set (FR-001).
 *
 * <p>Closed on purpose. A ninth attribute is one constant here plus one configuration entry -
 * calculation, modifier model and snapshot never learn about it, because they all work over {@link
 * #values()}. What a closed set buys instead is the reason this block costs nothing at idle: every
 * holder keeps its values in a {@code double[]} indexed by {@link #ordinal()}. No map lookup, no
 * boxing, no allocation in the path that runs while 200 players are fighting.
 *
 * <p>Runtime registration was considered and rejected (see research.md E1): it would make the
 * snapshot size dynamic and turn every access into a name lookup, to support a case - blocks
 * inventing their own attributes - that the architecture does not actually want.
 */
public enum Attribute {

    /** Maximum health. Own HP system per ADR-003; the vanilla bar is a percentage display. */
    HEALTH("health", AttributeKind.ABSOLUTE),

    /** Reduces incoming damage through the divisor model in {@link DamageMitigation}. */
    DEFENSE("defense", AttributeKind.ABSOLUTE),

    /** Maximum mana - the resource active abilities (B08) draw from. */
    MANA("mana", AttributeKind.ABSOLUTE),

    /** Base for weapon damage. */
    PHYSICAL_DAMAGE("physicalDamage", AttributeKind.ABSOLUTE),

    /** Base for ability damage. */
    MAGIC_DAMAGE("magicDamage", AttributeKind.ABSOLUTE),

    /** Attacks per unit of time; mirrored to the vanilla attack speed attribute. */
    ATTACK_SPEED("attackSpeed", AttributeKind.ABSOLUTE),

    /** Movement speed; mirrored to the vanilla movement speed attribute. */
    MOVEMENT_SPEED("movementSpeed", AttributeKind.ABSOLUTE),

    /** Cooldown reduction as a fraction, hard-capped by configuration (ADR-008: 40%). */
    ABILITY_COOLDOWN("abilityCooldown", AttributeKind.PERCENT);

    /** Cached because {@code values()} clones its array on every call. */
    private static final Attribute[] VALUES = values();

    private static final Map<String, Attribute> BY_KEY =
            Stream.of(VALUES).collect(Collectors.toUnmodifiableMap(Attribute::key, Function.identity()));

    private final String key;
    private final AttributeKind kind;

    Attribute(String key, AttributeKind kind) {
        this.key = key;
        this.kind = kind;
    }

    /** The configuration key, in lowerCamelCase - exactly as it appears in {@code stats.yml}. */
    public String key() {
        return key;
    }

    /** Whether this is an absolute number or a fraction. */
    public AttributeKind kind() {
        return kind;
    }

    /** How many attributes there are - the length of every value array in this block. */
    public static int count() {
        return VALUES.length;
    }

    /**
     * The attributes in declaration order, without the defensive copy {@code values()} makes.
     *
     * <p>The returned array must not be modified. It is exposed rather than copied because this is
     * iterated on every recalculation; a copy per call would allocate exactly where this block
     * promises not to.
     */
    public static Attribute[] all() {
        return VALUES;
    }

    /**
     * Resolves a configuration key.
     *
     * @throws UnknownAttributeException if no attribute carries that key - never {@code null}, and
     *     never a silently created attribute (FR-004a, FR-009)
     */
    public static Attribute byKey(String key) {
        Attribute attribute = BY_KEY.get(key);
        if (attribute == null) {
            throw new UnknownAttributeException(key);
        }
        return attribute;
    }
}

package rpg.core.combat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The validated combat configuration (Principle V).
 *
 * <p>Every balancing number of this block lives here: how long combat lasts, how many attackers a
 * target remembers, how much a fall costs, what a zombie has. None of it is in code.
 *
 * <p>Like {@code StatConfig} in B04, this exists as a type with a code-built shipping state
 * ({@link #defaults()}) so that every rule of the block is testable without a configuration file.
 * The YAML schema is attached separately.
 *
 * @param combatTimeout how long after the last hit a holder still counts as in combat (FR-030f)
 * @param maxAttackers attackers tracked per target; a fixed-size array, not a growing list (FR-032)
 * @param attributionTimeout after this, a contribution no longer counts (FR-033)
 * @param aggregationWindow hits inside this window become one display event (FR-038)
 * @param knockbackStrength vanilla knockback strength
 * @param fallDamage how a fall turns into damage (FR-012c)
 * @param environmentDamage fixed amount per hazard, without defence (FR-012a, FR-012b)
 * @param mobStats attribute values per creature type, until B10 takes over (FR-019b)
 * @param defaultMobStats values for a hostile type with no entry of its own
 */
public record CombatConfig(
        Duration combatTimeout,
        int maxAttackers,
        Duration attributionTimeout,
        Duration aggregationWindow,
        double knockbackStrength,
        FallDamageConfig fallDamage,
        Map<EnvironmentSource, Double> environmentDamage,
        Map<String, MobStats> mobStats,
        MobStats defaultMobStats) {

    /**
     * Upper bound on {@link #maxAttackers}.
     *
     * <p>Not arbitrary: the attribution window is an array per target, and at 800 mobs a generous
     * number quickly becomes real memory that never shrinks.
     */
    public static final int MAX_ATTACKERS_CEILING = 64;

    /** Attribute values of one creature type. */
    public record MobStats(double health, double defense, double physicalDamage) {
        public MobStats {
            requireNonNegative("health", health);
            requireNonNegative("defense", defense);
            requireNonNegative("physical-damage", physicalDamage);
            if (health <= 0.0) {
                throw new IllegalArgumentException(
                        "mob health must be greater than zero, but was " + health);
            }
        }

        private static void requireNonNegative(String field, double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "mob " + field + " must be finite and not negative, but was " + value);
            }
        }
    }

    public CombatConfig {
        Objects.requireNonNull(combatTimeout, "combat.combat-timeout-seconds");
        Objects.requireNonNull(attributionTimeout, "combat.attribution.timeout-seconds");
        Objects.requireNonNull(aggregationWindow, "combat.feedback.aggregation-window-millis");
        Objects.requireNonNull(fallDamage, "environment.fall");
        Objects.requireNonNull(defaultMobStats, "mobs.default");

        if (combatTimeout.isNegative() || combatTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "combat.combat-timeout-seconds must be greater than zero, but was "
                            + combatTimeout.toSeconds());
        }
        if (maxAttackers < 1 || maxAttackers > MAX_ATTACKERS_CEILING) {
            throw new IllegalArgumentException(
                    "combat.attribution.max-attackers must be between 1 and "
                            + MAX_ATTACKERS_CEILING
                            + " (it is an array per target, and at 800 mobs a generous number is"
                            + " real memory), but was "
                            + maxAttackers);
        }
        if (attributionTimeout.isNegative() || attributionTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "combat.attribution.timeout-seconds must be greater than zero, but was "
                            + attributionTimeout.toSeconds());
        }
        if (aggregationWindow.isNegative() || aggregationWindow.toMillis() > 5_000) {
            throw new IllegalArgumentException(
                    "combat.feedback.aggregation-window-millis must be between 0 and 5000, but was "
                            + aggregationWindow.toMillis());
        }
        if (!Double.isFinite(knockbackStrength) || knockbackStrength < 0.0) {
            throw new IllegalArgumentException(
                    "combat.feedback.knockback-strength must be finite and not negative, but was "
                            + knockbackStrength);
        }

        Objects.requireNonNull(environmentDamage, "environment");
        EnumMap<EnvironmentSource, Double> environmentCopy = new EnumMap<>(EnvironmentSource.class);
        environmentDamage.forEach(
                (source, amount) -> {
                    Objects.requireNonNull(source, "environment source");
                    if (amount == null || !Double.isFinite(amount) || amount < 0.0) {
                        throw new IllegalArgumentException(
                                "environment."
                                        + source.key()
                                        + " must be finite and not negative, but was "
                                        + amount);
                    }
                    environmentCopy.put(source, amount);
                });
        for (EnvironmentSource source : EnvironmentSource.all()) {
            // FALL computes its amount from the height, so it needs no flat number.
            if (!source.isComputed() && !environmentCopy.containsKey(source)) {
                throw new IllegalArgumentException(
                        "environment."
                                + source.key()
                                + " is missing - ADR-003 requires an explicit decision per source");
            }
        }
        environmentDamage = Map.copyOf(environmentCopy);

        mobStats = Map.copyOf(Objects.requireNonNull(mobStats, "mobs.by-type"));
    }

    /** The fixed amount for a hazard; the fall is computed instead (FR-012c). */
    public double environmentDamageOf(EnvironmentSource source) {
        Double amount = environmentDamage.get(source);
        return amount == null ? 0.0 : amount;
    }

    /** The stats for a creature type, falling back to the default set (FR-019b). */
    public MobStats mobStatsOf(String mobTypeKey) {
        return mobStats.getOrDefault(mobTypeKey, defaultMobStats);
    }

    /** The shipped balancing values; the same numbers live in {@code combat.yml}. */
    public static CombatConfig defaults() {
        EnumMap<EnvironmentSource, Double> environment = new EnumMap<>(EnvironmentSource.class);
        environment.put(EnvironmentSource.FIRE, 2.0);
        environment.put(EnvironmentSource.FIRE_TICK, 1.0);
        environment.put(EnvironmentSource.LAVA, 8.0);
        environment.put(EnvironmentSource.HOT_FLOOR, 2.0);
        environment.put(EnvironmentSource.CAMPFIRE, 2.0);
        environment.put(EnvironmentSource.DROWNING, 3.0);
        environment.put(EnvironmentSource.SUFFOCATION, 3.0);
        environment.put(EnvironmentSource.CONTACT, 1.0);
        environment.put(EnvironmentSource.BLOCK_EXPLOSION, 25.0);
        environment.put(EnvironmentSource.ENTITY_EXPLOSION, 25.0);
        environment.put(EnvironmentSource.LIGHTNING, 30.0);
        environment.put(EnvironmentSource.FALLING_BLOCK, 20.0);
        environment.put(EnvironmentSource.FLY_INTO_WALL, 6.0);
        environment.put(EnvironmentSource.FREEZE, 2.0);
        environment.put(EnvironmentSource.DRYOUT, 2.0);
        environment.put(EnvironmentSource.DRAGON_BREATH, 6.0);
        environment.put(EnvironmentSource.SONIC_BOOM, 20.0);
        environment.put(EnvironmentSource.WORLD_BORDER, 2.0);

        Map<String, MobStats> mobs =
                Map.of(
                        "ZOMBIE", new MobStats(80.0, 10.0, 10.0),
                        "SKELETON", new MobStats(60.0, 0.0, 9.0),
                        "CREEPER", new MobStats(50.0, 0.0, 0.0),
                        "SPIDER", new MobStats(55.0, 5.0, 7.0),
                        "ENDERMAN", new MobStats(200.0, 20.0, 25.0));

        return new CombatConfig(
                Duration.ofSeconds(8),
                16,
                Duration.ofSeconds(30),
                Duration.ofMillis(500),
                0.4,
                FallDamageConfig.defaults(),
                environment,
                mobs,
                new MobStats(60.0, 0.0, 8.0));
    }
}

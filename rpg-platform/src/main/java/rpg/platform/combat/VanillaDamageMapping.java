package rpg.platform.combat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import rpg.core.combat.EnvironmentSource;

/**
 * What happens to each vanilla damage cause (FR-011, ADR-003).
 *
 * <p><b>An exhaustive switch, not a list.</b> The B05 block brief names 17 causes; Paper 26.2 has
 * 33. A hand-kept list would have let the other sixteen through in silence - and ADR-003 is explicit
 * that <em>every</em> source needs a decision.
 *
 * <p>The switch is exhaustive over the enum, so a cause that goes missing is reported by the
 * compiler rather than by a player. And a cause a future Minecraft version <em>adds</em> falls into
 * the default: neutralised and logged once. That inverts the direction of the risk - an update
 * cannot let damage through, it can only produce a line asking for a decision.
 */
public final class VanillaDamageMapping {

    /** How a cause is handled. */
    public enum Treatment {
        /** Goes through the full pipeline as combat damage. */
        COMBAT,
        /** Neutralised, then re-applied as own environmental damage. */
        MAPPED,
        /** Neutralised, nothing else happens. */
        DISABLED,
        /** The target dies, whatever its health. */
        LETHAL
    }

    /** A cause plus, for {@link Treatment#MAPPED}, which hazard it becomes. */
    public record Mapping(Treatment treatment, EnvironmentSource source) {
        static final Mapping COMBAT = new Mapping(Treatment.COMBAT, null);
        static final Mapping DISABLED = new Mapping(Treatment.DISABLED, null);
        static final Mapping LETHAL = new Mapping(Treatment.LETHAL, null);

        static Mapping mapped(EnvironmentSource source) {
            return new Mapping(Treatment.MAPPED, source);
        }

        public Optional<EnvironmentSource> environmentSource() {
            return Optional.ofNullable(source);
        }
    }

    /** Causes already reported as unmapped, so the log stays a note rather than a flood. */
    private final Set<DamageCause> reported = EnumSet.noneOf(DamageCause.class);

    private final Logger logger;

    public VanillaDamageMapping(Logger logger) {
        this.logger = logger;
    }

    /** The treatment for one cause. Never {@code null}. */
    public Mapping of(DamageCause cause) {
        return switch (cause) {
            // --- combat: through the pipeline -----------------------------
            case ENTITY_ATTACK, PROJECTILE -> Mapping.COMBAT;

            // --- environment: neutralised and re-applied ------------------
            case FALL -> Mapping.mapped(EnvironmentSource.FALL);
            case FIRE -> Mapping.mapped(EnvironmentSource.FIRE);
            case FIRE_TICK -> Mapping.mapped(EnvironmentSource.FIRE_TICK);
            case LAVA -> Mapping.mapped(EnvironmentSource.LAVA);
            case HOT_FLOOR -> Mapping.mapped(EnvironmentSource.HOT_FLOOR);
            case CAMPFIRE -> Mapping.mapped(EnvironmentSource.CAMPFIRE);
            case DROWNING -> Mapping.mapped(EnvironmentSource.DROWNING);
            case SUFFOCATION -> Mapping.mapped(EnvironmentSource.SUFFOCATION);
            case CONTACT -> Mapping.mapped(EnvironmentSource.CONTACT);
            case BLOCK_EXPLOSION -> Mapping.mapped(EnvironmentSource.BLOCK_EXPLOSION);
            case ENTITY_EXPLOSION -> Mapping.mapped(EnvironmentSource.ENTITY_EXPLOSION);
            case LIGHTNING -> Mapping.mapped(EnvironmentSource.LIGHTNING);
            case FALLING_BLOCK -> Mapping.mapped(EnvironmentSource.FALLING_BLOCK);
            case FLY_INTO_WALL -> Mapping.mapped(EnvironmentSource.FLY_INTO_WALL);
            case FREEZE -> Mapping.mapped(EnvironmentSource.FREEZE);
            case DRYOUT -> Mapping.mapped(EnvironmentSource.DRYOUT);
            case DRAGON_BREATH -> Mapping.mapped(EnvironmentSource.DRAGON_BREATH);
            case SONIC_BOOM -> Mapping.mapped(EnvironmentSource.SONIC_BOOM);
            case WORLD_BORDER -> Mapping.mapped(EnvironmentSource.WORLD_BORDER);

            // --- vanilla systems this game does not use -------------------
            // The sweep attack would land a second time next to the block's own melee hit.
            case ENTITY_SWEEP_ATTACK -> Mapping.DISABLED;
            // Thorns is an enchantment mechanic; reflected damage is B11's if it is anyone's.
            case THORNS -> Mapping.DISABLED;
            case MAGIC, POISON, WITHER, STARVATION -> Mapping.DISABLED;
            // A melting snow golem is meaningless here.
            case MELTING -> Mapping.DISABLED;
            // Vanilla kills entities that stand too close together. On a server built around
            // hordes that would wipe out whole groups; how many mobs may stand where is B10's
            // spawn budget, not the damage path.
            case CRAMMING -> Mapping.DISABLED;
            // The channel another plugin would inject damage through. This block never uses it:
            // own damage never travels through a vanilla event.
            case CUSTOM -> Mapping.DISABLED;

            // --- lethal ---------------------------------------------------
            case VOID, KILL, SUICIDE -> Mapping.LETHAL;
        };
    }

    /**
     * The treatment, reporting an unknown cause once.
     *
     * <p>Separate from {@link #of} so tests can assert the mapping without provoking the log.
     */
    public Mapping resolve(DamageCause cause) {
        try {
            return of(cause);
        } catch (IncompatibleClassChangeError | MatchException unknown) {
            // A cause added by a Minecraft version newer than this build.
            if (reported.add(cause)) {
                logger.warning(
                        "[combat] unmapped damage cause "
                                + cause
                                + " - neutralised. Decide its treatment in VanillaDamageMapping;"
                                + " ADR-003 requires an explicit decision per source.");
            }
            return Mapping.DISABLED;
        }
    }

    /** Causes reported as unmapped so far. For diagnostics. */
    public Set<DamageCause> unmappedCauses() {
        return EnumSet.copyOf(reported.isEmpty() ? EnumSet.noneOf(DamageCause.class) : reported);
    }
}

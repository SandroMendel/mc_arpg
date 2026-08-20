package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.EnvironmentSource;
import rpg.platform.combat.VanillaDamageMapping.Treatment;

/**
 * T025-T027: every vanilla damage cause has an explicit decision (FR-011, SC-001, ADR-003).
 *
 * <p>The first test iterates over the <b>enum</b>, not over a list. That is the whole point: the B05
 * block brief named 17 causes and Paper has 33, so a hand-kept list would have let sixteen through
 * in silence. Iterating the enum means a cause added by a future Minecraft version fails this test
 * instead of quietly killing someone.
 */
class VanillaDamageMappingTest {

    private static final Logger QUIET = quiet();

    private static Logger quiet() {
        Logger logger = Logger.getLogger("vanilla-damage-mapping-test");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private final VanillaDamageMapping mapping = new VanillaDamageMapping(QUIET);

    @Test
    @DisplayName("every single DamageCause has a treatment - no exceptions, no gaps")
    void everyCauseIsDecided() {
        for (DamageCause cause : DamageCause.values()) {
            assertThat(mapping.of(cause)).as(cause.name()).isNotNull();
            assertThat(mapping.of(cause).treatment()).as(cause.name()).isNotNull();
        }
        // If this number changes, a Minecraft version added or removed a cause and the table above
        // needs a decision for it - which is exactly the reminder ADR-003 asks for.
        assertThat(DamageCause.values()).hasSize(33);
    }

    @Test
    @DisplayName("combat causes go through the pipeline")
    void combatCauses() {
        assertThat(mapping.of(DamageCause.ENTITY_ATTACK).treatment()).isEqualTo(Treatment.COMBAT);
        assertThat(mapping.of(DamageCause.PROJECTILE).treatment()).isEqualTo(Treatment.COMBAT);
    }

    @Test
    @DisplayName("the environmental causes map onto their own hazard, one for one")
    void environmentCauses() {
        assertMapped(DamageCause.FALL, EnvironmentSource.FALL);
        assertMapped(DamageCause.FIRE, EnvironmentSource.FIRE);
        assertMapped(DamageCause.FIRE_TICK, EnvironmentSource.FIRE_TICK);
        assertMapped(DamageCause.LAVA, EnvironmentSource.LAVA);
        assertMapped(DamageCause.HOT_FLOOR, EnvironmentSource.HOT_FLOOR);
        assertMapped(DamageCause.CAMPFIRE, EnvironmentSource.CAMPFIRE);
        assertMapped(DamageCause.DROWNING, EnvironmentSource.DROWNING);
        assertMapped(DamageCause.SUFFOCATION, EnvironmentSource.SUFFOCATION);
        assertMapped(DamageCause.CONTACT, EnvironmentSource.CONTACT);
        assertMapped(DamageCause.BLOCK_EXPLOSION, EnvironmentSource.BLOCK_EXPLOSION);
        assertMapped(DamageCause.ENTITY_EXPLOSION, EnvironmentSource.ENTITY_EXPLOSION);
        assertMapped(DamageCause.LIGHTNING, EnvironmentSource.LIGHTNING);
        assertMapped(DamageCause.FALLING_BLOCK, EnvironmentSource.FALLING_BLOCK);
        assertMapped(DamageCause.FLY_INTO_WALL, EnvironmentSource.FLY_INTO_WALL);
        assertMapped(DamageCause.FREEZE, EnvironmentSource.FREEZE);
        assertMapped(DamageCause.DRYOUT, EnvironmentSource.DRYOUT);
        assertMapped(DamageCause.DRAGON_BREATH, EnvironmentSource.DRAGON_BREATH);
        assertMapped(DamageCause.SONIC_BOOM, EnvironmentSource.SONIC_BOOM);
        assertMapped(DamageCause.WORLD_BORDER, EnvironmentSource.WORLD_BORDER);
    }

    @Test
    @DisplayName("every environment source is actually reachable from some cause")
    void everySourceIsUsed() {
        Set<EnvironmentSource> reached = EnumSet.noneOf(EnvironmentSource.class);
        for (DamageCause cause : DamageCause.values()) {
            mapping.of(cause).environmentSource().ifPresent(reached::add);
        }
        assertThat(reached)
                .as("a hazard nothing maps to would be dead configuration")
                .containsExactlyInAnyOrder(EnvironmentSource.all());
    }

    @Test
    @DisplayName("vanilla status effects and vanilla-only systems are switched off")
    void disabledCauses() {
        for (DamageCause cause :
                Arrays.asList(
                        DamageCause.MAGIC,
                        DamageCause.POISON,
                        DamageCause.WITHER,
                        DamageCause.STARVATION,
                        DamageCause.MELTING,
                        DamageCause.CRAMMING,
                        DamageCause.CUSTOM,
                        DamageCause.THORNS,
                        DamageCause.ENTITY_SWEEP_ATTACK)) {
            assertThat(mapping.of(cause).treatment()).as(cause.name()).isEqualTo(Treatment.DISABLED);
        }
    }

    @Test
    @DisplayName("void and the admin kill stay lethal, whatever the health")
    void lethalCauses() {
        assertThat(mapping.of(DamageCause.VOID).treatment()).isEqualTo(Treatment.LETHAL);
        assertThat(mapping.of(DamageCause.KILL).treatment()).isEqualTo(Treatment.LETHAL);
        assertThat(mapping.of(DamageCause.SUICIDE).treatment()).isEqualTo(Treatment.LETHAL);
    }

    @Test
    @DisplayName("resolve never returns null, for any cause")
    void resolveIsTotal() {
        for (DamageCause cause : DamageCause.values()) {
            assertThat(mapping.resolve(cause)).as(cause.name()).isNotNull();
        }
        assertThat(mapping.unmappedCauses()).isEmpty();
    }

    @Test
    @DisplayName("the sweep attack is off so it cannot land next to the block's own melee hit")
    void sweepIsOff() {
        // Left on, a single swing would deal damage twice: once through the pipeline and once as
        // vanilla's area effect.
        assertThat(mapping.of(DamageCause.ENTITY_SWEEP_ATTACK).treatment())
                .isEqualTo(Treatment.DISABLED);
    }

    private void assertMapped(DamageCause cause, EnvironmentSource expected) {
        assertThat(mapping.of(cause).treatment()).as(cause.name()).isEqualTo(Treatment.MAPPED);
        assertThat(mapping.of(cause).environmentSource()).as(cause.name()).contains(expected);
    }
}

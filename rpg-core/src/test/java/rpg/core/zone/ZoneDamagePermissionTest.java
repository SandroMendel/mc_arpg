package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamagePermission;
import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * T053, T054 - the damage permission as a zone rule (FR-026 to FR-031, SC-008, SC-009, SC-012).
 *
 * <p><b>The first block of tests is the one that matters most.</b> With the shipped configuration -
 * every region on {@code pvp: false} - the answer has to be identical to B05's own rule in all six
 * cases. That is what makes the swap provable rather than merely claimed: nothing about the game
 * changes, and the mechanism is in place (SC-008).
 */
class ZoneDamagePermissionTest {

    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final UUID MOB = UUID.randomUUID();

    /** Placement told to the rule by hand - the tracker's job in production. */
    private static final class Placement implements ZonePresence {

        private final Set<UUID> inCore = new HashSet<>();
        private final Map<UUID, String> zoneOf = new HashMap<>();

        @Override
        public boolean inSafeCore(UUID holderId) {
            return holderId != null && inCore.contains(holderId);
        }

        @Override
        public String zoneKeyOf(UUID holderId) {
            return holderId == null ? null : zoneOf.get(holderId);
        }

        Placement standing(UUID holderId, String zoneKey) {
            zoneOf.put(holderId, zoneKey);
            return this;
        }

        Placement inCore(UUID holderId) {
            inCore.add(holderId);
            return this;
        }
    }

    @Nested
    @DisplayName("with the shipped configuration nothing changes (SC-008)")
    class Shipped {

        private final DamagePermission zoned = rule(new Placement()
                .standing(PLAYER_A, "greenfields")
                .standing(PLAYER_B, "greenfields"), false);
        private final DamagePermission shipped = DamagePermission.defaultRule();

        @Test
        @DisplayName("all six cases answer exactly as B05's own rule does")
        void identicalToTheShippedRule() {
            assertSame("player -> mob", PLAYER_A, true, MOB, false);
            assertSame("mob -> player", MOB, false, PLAYER_A, true);
            assertSame("player -> player", PLAYER_A, true, PLAYER_B, true);
            assertSame("mob -> mob", MOB, false, UUID.randomUUID(), false);
            assertSame("player -> self", PLAYER_A, true, PLAYER_A, true);
            assertSame("environment -> player", null, false, PLAYER_A, true);
            assertSame("environment -> mob", null, false, MOB, false);
        }

        private void assertSame(
                String what, UUID attacker, boolean attackerIsPlayer, UUID target, boolean targetIsPlayer) {
            assertThat(zoned.isAllowed(attacker, attackerIsPlayer, target, targetIsPlayer))
                    .as(what)
                    .isEqualTo(shipped.isAllowed(attacker, attackerIsPlayer, target, targetIsPlayer));
        }
    }

    @Nested
    @DisplayName("the safe core refuses everything (FR-028a, SC-012)")
    class SafeCore {

        @Test
        @DisplayName("a mob cannot hit a player standing in the core")
        void mobCannotHitIntoTheCore() {
            DamagePermission rule =
                    rule(new Placement().standing(PLAYER_A, "greenfields").inCore(PLAYER_A), false);

            assertThat(rule.isAllowed(MOB, false, PLAYER_A, true)).isFalse();
        }

        @Test
        @DisplayName("a player in the core cannot hit a mob outside it - not a firing line")
        void cannotStrikeOutOfTheCore() {
            DamagePermission rule =
                    rule(new Placement().standing(PLAYER_A, "greenfields").inCore(PLAYER_A), false);

            assertThat(rule.isAllowed(PLAYER_A, true, MOB, false)).isFalse();
        }

        @Test
        @DisplayName("environmental damage is refused in the core - the one answer this turns around")
        void environmentIsRefusedInTheCore() {
            DamagePermission rule =
                    rule(new Placement().standing(PLAYER_A, "greenfields").inCore(PLAYER_A), false);

            assertThat(DamagePermission.defaultRule().isAllowed(null, false, PLAYER_A, true))
                    .as("B05 lets the environment hurt everyone")
                    .isTrue();
            assertThat(rule.isAllowed(null, false, PLAYER_A, true))
                    .as("inside a core it does not (FR-028a)")
                    .isFalse();
        }

        @Test
        @DisplayName("self damage is refused in the core too - nobody dies there")
        void selfDamageIsRefusedInTheCore() {
            DamagePermission rule =
                    rule(new Placement().standing(PLAYER_A, "greenfields").inCore(PLAYER_A), false);

            assertThat(rule.isAllowed(PLAYER_A, true, PLAYER_A, true)).isFalse();
        }

        @Test
        @DisplayName("pvp: true does not reach into the core (FR-028, SC-009)")
        void pvpDoesNotReachIntoTheCore() {
            DamagePermission rule =
                    rule(
                            new Placement()
                                    .standing(PLAYER_A, "pale-wilds")
                                    .standing(PLAYER_B, "pale-wilds")
                                    .inCore(PLAYER_B),
                            true);

            assertThat(rule.isAllowed(PLAYER_A, true, PLAYER_B, true)).isFalse();
        }
    }

    @Nested
    @DisplayName("pvp is the zone's switch (FR-027, FR-029, SC-009)")
    class Pvp {

        @Test
        @DisplayName("switched on, players may hurt each other in the danger zone")
        void allowedWhereSwitchedOn() {
            DamagePermission rule =
                    rule(
                            new Placement()
                                    .standing(PLAYER_A, "pale-wilds")
                                    .standing(PLAYER_B, "pale-wilds"),
                            true);

            assertThat(rule.isAllowed(PLAYER_A, true, PLAYER_B, true)).isTrue();
        }

        @Test
        @DisplayName("and nowhere else - a neighbouring region keeps its own answer")
        void notInTheNeighbouringRegion() {
            DamagePermission rule =
                    rule(
                            new Placement()
                                    .standing(PLAYER_A, "pale-wilds")
                                    .standing(PLAYER_B, "darkforest"),
                            true);

            assertThat(rule.isAllowed(PLAYER_A, true, PLAYER_B, true))
                    .as("the target's region decides, and that one has pvp off")
                    .isFalse();
        }

        @Test
        @DisplayName("outside every region the shipped default holds (FR-029)")
        void wildernessKeepsTheDefault() {
            DamagePermission rule =
                    rule(new Placement().standing(PLAYER_A, "pale-wilds"), true);

            // PLAYER_B has no placement at all - the wilderness.
            assertThat(rule.isAllowed(PLAYER_A, true, PLAYER_B, true)).isFalse();
        }

        @Test
        @DisplayName("the switch does not touch mobs (FR-030)")
        void mobsAreUnaffectedByTheSwitch() {
            DamagePermission rule =
                    rule(
                            new Placement()
                                    .standing(PLAYER_A, "pale-wilds")
                                    .standing(MOB, "pale-wilds"),
                            true);

            assertThat(rule.isAllowed(PLAYER_A, true, MOB, false)).as("player -> mob").isTrue();
            assertThat(rule.isAllowed(MOB, false, PLAYER_A, true)).as("mob -> player").isTrue();
            assertThat(rule.isAllowed(MOB, false, UUID.randomUUID(), false))
                    .as("mob -> mob stays refused")
                    .isFalse();
        }
    }

    /** The rule over the shipped six regions, optionally with the pale wilds switched to PvP. */
    private static DamagePermission rule(ZonePresence presence, boolean pvpInPaleWilds) {
        try {
            Map<String, Object> document = ZoneFixture.document();
            if (pvpInPaleWilds) {
                ZoneFixture.zoneIn(document, "pale-wilds").put("pvp", Boolean.TRUE);
            }
            ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
            Zones zones =
                    new DefaultZones(
                            schema.bind(
                                    SchemaValidator.validate(
                                            Path.of("zones.yml"), document, schema)));
            return new ZoneDamagePermission(presence, () -> zones);
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }
}

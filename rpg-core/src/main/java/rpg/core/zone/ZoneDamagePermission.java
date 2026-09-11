package rpg.core.zone;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import rpg.core.combat.DamagePermission;

/**
 * The damage permission, made a zone rule (FR-026 to FR-031).
 *
 * <p><b>This wraps B05's shipped rule; it does not rebuild it.</b> That is the whole point. B05 laid
 * this out as a swap point and guards it with {@code SinglePermissionPointTest}; the four cases that
 * are none of B09's business - player against mob, mob against player, environment against anyone,
 * self damage, mob against mob - still come out of {@link DamagePermission#defaultRule()}. Copying
 * them would have created the second copy that test exists to prevent, and the two would have drifted
 * the first time somebody edited one.
 *
 * <p>The order (research.md R10):
 *
 * <ol>
 *   <li>target in a safe core → no
 *   <li>attacker in a safe core → no
 *   <li>player against player → the zone's switch, default off
 *   <li>anything else → B05's own rule, untouched
 * </ol>
 *
 * <p><b>Step 1 covers environmental damage, and that is the one place this class turns the shipped
 * answer around.</b> {@code defaultRule} lets the environment hurt everyone; inside a core it does
 * not, because nobody dies there (FR-028a). It is written out as a requirement rather than left as a
 * side effect of the ordering.
 *
 * <p><b>Steps 1 and 2 come before the PvP switch on purpose.</b> A region with {@code pvp: true} still
 * refuses damage in its own core, so the switch cannot reach into the one place that is meant to be
 * safe (FR-028, SC-009).
 *
 * <p>What "in a safe core" means here is answered by {@link ZonePresence} without resolving a
 * position - see there for the reason and for the one case it deliberately does not cover.
 */
public final class ZoneDamagePermission implements DamagePermission {

    private final DamagePermission shipped;
    private final ZonePresence presence;
    private final Supplier<Zones> zones;

    public ZoneDamagePermission(ZonePresence presence, Supplier<Zones> zones) {
        this(DamagePermission.defaultRule(), presence, zones);
    }

    ZoneDamagePermission(DamagePermission shipped, ZonePresence presence, Supplier<Zones> zones) {
        this.shipped = Objects.requireNonNull(shipped, "shipped");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    @Override
    public boolean isAllowed(
            UUID attackerId, boolean attackerIsPlayer, UUID targetId, boolean targetIsPlayer) {
        // 1 and 2: the core refuses everything that gets this far, in both directions.
        //
        // This comment used to claim environmental damage was included, because attackerId is null
        // there and the target check runs regardless. The claim was wrong: B05 never consults a
        // permission on the environment path at all, so lava in a safe core was never refused here.
        // SafeCoreDamageGuard covers that path as an interceptor; this rule covers the ones with an
        // attacker.
        if (presence.inSafeCore(targetId)) {
            return false;
        }
        if (attackerId != null && presence.inSafeCore(attackerId)) {
            return false;
        }

        // 3: player against player is the zone's decision. The target's zone decides, because that
        // is where the blow lands - an attacker shooting across a border does not carry their own
        // region's rules with them.
        if (attackerIsPlayer && targetIsPlayer && !Objects.equals(attackerId, targetId)) {
            return pvpAllowedAround(targetId);
        }

        // 4: everything else is B05's, unchanged.
        return shipped.isAllowed(attackerId, attackerIsPlayer, targetId, targetIsPlayer);
    }

    /**
     * Whether players may hurt each other where this one stands.
     *
     * <p>Outside every region the answer is no (FR-029): the wilderness keeps the shipped default
     * rather than inheriting a neighbour's switch.
     */
    private boolean pvpAllowedAround(UUID holderId) {
        String zoneKey = presence.zoneKeyOf(holderId);
        if (zoneKey == null) {
            return false;
        }
        return zones.get().byKey(zoneKey).map(Zone::pvp).orElse(false);
    }
}

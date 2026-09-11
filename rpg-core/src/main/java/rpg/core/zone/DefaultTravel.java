package rpg.core.zone;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.scheduler.WorldPosition;

/**
 * The travel sequence (FR-050, FR-050e, FR-050f, research.md R1).
 *
 * <p><b>Why the fare is taken before the move, even though that is the risky order.</b> The safe
 * order would be to move first and charge afterwards, or to reserve the coins and settle later. Both
 * were ruled out: moving first means a failed booking leaves somebody standing at a destination they
 * did not pay for, and {@link Currency} refuses to hand out reservations on purpose - two callers in
 * one tick would each be told yes and each would then spend the same coins. So the fare comes first,
 * and the failure is repaired instead of prevented (FR-050f).
 *
 * <p><b>Everything happens in one tick phase.</b> The debit is atomic, the teleport is a single
 * Paper call, and the refund follows immediately. Nothing yields in between, so no other code can
 * observe a character who has paid and not travelled. That is the whole of FR-050b's promise: not
 * that the pair is atomic - it cannot be, because half of it is a server call - but that a loss
 * never survives the tick it happened in.
 *
 * <p><b>The order of the checks is the contract</b> (FR-050e). Unlocked and in-combat both come
 * before the booking, so the two most common refusals cost nothing at all: no ledger entry, no dirty
 * mark, no write. A refusal that showed up in the history would make the history harder to read for
 * the one case it exists to explain.
 */
public final class DefaultTravel implements Travel {

    private final Supplier<Zones> zones;
    private final Waypoints waypoints;
    private final Predicate<UUID> inCombat;
    private final Currency currency;
    private final Teleporter teleporter;

    /**
     * @param zones re-read on every journey, so a reload takes effect without rewiring (FR-057a)
     * @param inCombat B05's state, asked rather than copied - never a second timer (FR-051g)
     */
    public DefaultTravel(
            Supplier<Zones> zones,
            Waypoints waypoints,
            Predicate<UUID> inCombat,
            Currency currency,
            Teleporter teleporter) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.waypoints = Objects.requireNonNull(waypoints, "waypoints");
        this.inCombat = Objects.requireNonNull(inCombat, "inCombat");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
    }

    @Override
    public TravelResult travelTo(UUID holderId, UUID characterId, String crystalKey) {
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(crystalKey, "crystalKey");

        Zones current = zones.get();
        Optional<CrystalPlacement> placement = current.crystalByKey(crystalKey);
        if (placement.isEmpty()) {
            return TravelResult.NO_SUCH_CRYSTAL;
        }
        if (!waypoints.isUnlocked(characterId, crystalKey)) {
            return TravelResult.NOT_UNLOCKED;
        }
        if (inCombat.test(holderId)) {
            return TravelResult.IN_COMBAT;
        }

        // The destination is the region's respawn point, never a coordinate on the crystal
        // (FR-045a). Asked here rather than remembered, so a region that moved between the window
        // opening and the click sends the traveller to where it is now.
        Optional<WorldPosition> destination =
                current.respawnPointOf(placement.get().zone().key());
        if (destination.isEmpty()) {
            // The schema refuses a crystal in a region without a safe core (FR-051c), so this can
            // only happen if the region lost its core in a reload. Refusing costs nothing; sending
            // somebody to the fallback point would charge them for a journey they did not choose.
            return TravelResult.NO_SUCH_CRYSTAL;
        }

        long price = placement.get().crystal().price();
        if (price > 0L) {
            BookingResult booking =
                    currency.debit(characterId, price, BookingReason.WAYPOINT_TRAVEL);
            if (!booking.isSuccess()) {
                // Every refusal leaves the balance untouched, which is the only thing the outcome
                // promises. Nothing has been moved yet either, so there is nothing to undo.
                return TravelResult.NOT_ENOUGH_COINS;
            }
        }

        if (teleporter.teleport(holderId, destination.get())) {
            return TravelResult.OK;
        }

        if (price > 0L) {
            // The one place in this block where a failure could take property. It is repaired in
            // the same tick, with its own reason so the pair is recognisable as a pair (FR-050d).
            currency.credit(characterId, price, BookingReason.WAYPOINT_REFUND);
        }
        return TravelResult.TELEPORT_FAILED;
    }
}

package rpg.core.currency;

/**
 * Where a change to a balance came from (FR-005).
 *
 * <p><b>A closed set, not free text.</b> Free text would have turned typos into booking kinds, and
 * the one question this block exists to answer - "where did these coins come from" - would have
 * become unanswerable the first time somebody wrote {@code "vendor sale"} instead of
 * {@code "vendor-sale"}. Currency is the part players complain about, and a complaint is settled
 * from the ledger or not at all.
 *
 * <p><b>Three values are for B11, which does not exist yet.</b> They cost nothing standing here and
 * spare B11 an edit to an enum it does not own - the same courtesy B04 and B06 extended to their
 * successors. A value nobody produces is not dead code; it is a named place to put something.
 */
public enum BookingReason {

    /**
     * The configured starting balance, credited once when a character is created (FR-011a).
     *
     * <p>Deliberately a booking rather than a value applied while reading. A balance that appears
     * out of the configuration has no entry in the ledger, and raising the configured number later
     * would have silently enriched every character that had never touched a coin (FR-011b).
     */
    STARTING_BALANCE(Direction.CREDIT),

    /** A coin pile was picked up. The only way coins enter from ordinary play today. */
    PILE_PICKED_UP(Direction.CREDIT),

    /**
     * The pile cap was reached, so the oldest pile in the world was credited to its owner and
     * cleared away to make room (FR-030a).
     *
     * <p><b>Not the same as an expiry, and the difference is the point.</b> A pile whose timer runs
     * out is credited to nobody (FR-029) - the player had time and let it pass. A pile the server
     * clears away is credited - the player could do nothing about server load. Own neglect costs;
     * server load does not.
     */
    PILE_CASHED_IN(Direction.CREDIT),

    /** An equipment tier was paid for (B07's opaque cost block, resolved here). */
    EQUIPMENT_TIER(Direction.DEBIT),

    /** An ability rank was paid for. */
    ABILITY_RANK(Direction.DEBIT),

    /** Sold to an NPC vendor. Reserved for B11. */
    VENDOR_SALE(Direction.CREDIT),

    /** Bought from an NPC vendor. Reserved for B11. */
    VENDOR_PURCHASE(Direction.DEBIT),

    /** Repair paid for. Reserved for B11. */
    REPAIR(Direction.DEBIT),

    /** An operator set a balance to an exact value. */
    ADMIN_SET(Direction.EITHER),

    /** An operator added coins. */
    ADMIN_ADD(Direction.CREDIT),

    /** An operator removed coins. */
    ADMIN_REMOVE(Direction.DEBIT);

    /**
     * Which way a reason can move a balance.
     *
     * <p>Held here so a mismatched pair - crediting with {@link #ABILITY_RANK}, say - is catchable
     * rather than merely wrong. {@link #EITHER} exists for exactly one case: setting a balance to a
     * value can go up or down depending on where it started.
     */
    public enum Direction {
        CREDIT,
        DEBIT,
        EITHER
    }

    private final Direction direction;

    BookingReason(Direction direction) {
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }

    /** Whether this reason may credit. */
    public boolean allowsCredit() {
        return direction != Direction.DEBIT;
    }

    /** Whether this reason may debit. */
    public boolean allowsDebit() {
        return direction != Direction.CREDIT;
    }

    /** Whether an operator caused this. Those entries are never pruned from the ledger (FR-038). */
    public boolean isAdministrative() {
        return this == ADMIN_SET || this == ADMIN_ADD || this == ADMIN_REMOVE;
    }
}

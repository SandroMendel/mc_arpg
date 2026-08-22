package rpg.core.currency;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The opaque {@code cost} block of B07, finally read (FR-047 to FR-050).
 *
 * <p><b>Read here, never in B07.</b> {@code ClassSourceInvariantsTest} forbids the words
 * {@code coins} and {@code price} in B07's sources, and that is not an obstacle - it is the
 * instruction. B07 passes the map through; this block interprets it. The invariant test stays green
 * without anyone touching it, and from now on it proves that the interpretation happens in the right
 * place (research.md R6).
 *
 * <p><b>Exactly one key is allowed: {@code coins}.</b> Anything else - {@code shards}, say - is a
 * price nobody can charge, and therefore a startup error rather than a silent skip (FR-050). The
 * block is a map so it can grow later, not so it can be ambiguous now.
 */
public record CostSpec(long coins) {

    /** The only key this project knows. */
    public static final String COINS = "coins";

    public static final CostSpec FREE = new CostSpec(0L);

    public CostSpec {
        if (coins < 0L) {
            throw new IllegalArgumentException("a cost must not be negative, but was " + coins);
        }
    }

    /**
     * Reads a configured cost block.
     *
     * @param costBlock the map exactly as B07 passed it through; empty means free
     * @param where what to name in the message when something is wrong - the class and tier, or the
     *     ability. An operator editing six ladders has to be told which line is at fault.
     * @throws IllegalArgumentException on an unknown key or a value that is not a number
     */
    public static CostSpec parse(Map<String, Object> costBlock, String where) {
        Objects.requireNonNull(costBlock, "costBlock");
        Objects.requireNonNull(where, "where");
        if (costBlock.isEmpty()) {
            return FREE;
        }

        long coins = 0L;
        for (Map.Entry<String, Object> entry : costBlock.entrySet()) {
            String key = String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
            if (!COINS.equals(key)) {
                throw new IllegalArgumentException(
                        where
                                + ": unknown cost key '"
                                + entry.getKey()
                                + "'. The only currency in this project is '"
                                + COINS
                                + "' - a price nobody can charge would never be paid, so the start"
                                + " refuses it rather than ignoring it");
            }
            if (!(entry.getValue() instanceof Number number)) {
                throw new IllegalArgumentException(
                        where + ": cost." + COINS + " must be a number, but was " + entry.getValue());
            }
            coins = number.longValue();
            if (coins < 0L) {
                throw new IllegalArgumentException(
                        where + ": cost." + COINS + " must not be negative, but was " + coins);
            }
        }
        return new CostSpec(coins);
    }

    /** Whether there is nothing to pay. An empty block and a block of zero mean the same thing. */
    public boolean isFree() {
        return coins == 0L;
    }
}

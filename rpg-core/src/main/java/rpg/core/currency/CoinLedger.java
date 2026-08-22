package rpg.core.currency;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reading the durable record of every booking (FR-034 to FR-036, FR-046a).
 *
 * <p><b>Read only.</b> Entries are queued by {@link DefaultCurrency} alone, inside the lock that
 * changes the balance - see {@link LedgerWriter}. Handing readers an append method would have made
 * the one rule that keeps this trustworthy a convention instead of a shape.
 *
 * <p><b>Every method here reaches the database</b>, so none of them belongs in a gameplay path. B12
 * and the operator's window call them from a command, and B14 plans rate limits for exactly that.
 *
 * <p><b>There is no unbounded query.</b> At 800 mobs this becomes the largest table in the project
 * within weeks, and a method that returned everything would be the one place that turns into an
 * outage. Paging is expressed with an offset because a window has to be able to go back a page
 * (ADR-028).
 */
public interface CoinLedger {

    /**
     * One page of the history, newest first.
     *
     * @param offset how many entries to skip; zero is the newest page
     * @param limit how many to return; must be positive and is capped by the caller's configuration
     */
    CompletableFuture<List<LedgerEntry>> historyOf(UUID characterId, int offset, int limit);

    /** How many entries exist for this character, for the page count. */
    CompletableFuture<Long> historyCount(UUID characterId);

    /** A period rather than a page, still bounded. */
    CompletableFuture<List<LedgerEntry>> historyOf(
            UUID characterId, Instant from, Instant to, int limit);
}

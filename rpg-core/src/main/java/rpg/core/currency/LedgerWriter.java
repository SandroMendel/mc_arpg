package rpg.core.currency;

/**
 * The append side of the ledger (FR-034, FR-037).
 *
 * <p><b>Separate from {@link CoinLedger} on purpose.</b> {@code CoinLedger} is what other blocks
 * read; this is what {@link DefaultCurrency} writes. Putting both on one interface would have handed
 * every reader an {@code append} method, and the one rule that keeps the ledger trustworthy - that
 * entries are produced only inside the lock that changes the balance - would have been a convention
 * instead of a shape.
 *
 * <p>An implementation must not block: this is called from a gameplay path, so the entry is queued
 * and written by the flush cycle, never on the spot (Constitution II).
 */
public interface LedgerWriter {

    /** Queues one entry. Called only by {@link DefaultCurrency}, inside its per-account lock. */
    void append(LedgerEntry entry);

    /**
     * A writer that drops everything.
     *
     * <p>For the paths that have no ledger yet and for tests about balances rather than history.
     * Deliberately not the default anywhere in production wiring - a currency that silently keeps no
     * record is exactly what FR-034 forbids.
     */
    static LedgerWriter discarding() {
        return entry -> {};
    }
}

package rpg.core.currency;

import java.util.UUID;

/**
 * What an operator may do to a balance (FR-039 to FR-045).
 *
 * <p><b>Every method demands an actor, and it may not be blank.</b> An intervention nobody can be
 * traced to is exactly the misbooking this block exists to make findable - and it would be the one
 * misbooking that was made on purpose.
 *
 * <p><b>Online and offline are not the caller's problem</b> (FR-042). If the character is logged in,
 * the change happens in the authoritative cache - otherwise the next flush would write the old value
 * straight back over it. If not, it happens on the stored balance. Which path applies is decided
 * here, not by whoever is typing.
 *
 * <p><b>Reaching the database here is allowed.</b> Constitution II forbids database access <em>per
 * game event</em>, and an operator command is not one. B14 plans rate limits for exactly this kind
 * of command (ADR-028).
 *
 * <p><b>Every intervention is written down twice</b>: into the ledger with its own reason (FR-040)
 * and into B02's audit log (FR-041). Not a duplicate without purpose - the ledger answers "what
 * happened to this account", the audit log answers "what has this operator been doing".
 */
public interface CurrencyAdmin {

    /**
     * Sets the balance to exactly this value.
     *
     * <p>The one operation whose direction is not known in advance: it credits or debits depending
     * on where the balance started.
     */
    BookingResult set(UUID characterId, long amount, String actor);

    /** Adds coins. */
    BookingResult add(UUID characterId, long amount, String actor);

    /**
     * Removes coins.
     *
     * <p>Refused if it would go below zero. <b>An operator does not get to create a negative balance
     * either</b> (FR-003) - the promise is about the number, not about who is asking.
     */
    BookingResult remove(UUID characterId, long amount, String actor);
}

package rpg.core.currency;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One recorded booking (FR-034, FR-035).
 *
 * <p><b>Append-only.</b> Nothing ever edits an entry; a correction is a new booking with its own
 * reason. An editable history is not a history.
 *
 * <p><b>Why the balance before and after are stored rather than derived.</b> They are derivable
 * while the chain is unbroken - and after a crash that cost one autosave interval, it is not. Stored
 * as facts, every entry stays readable on its own even when a neighbour is missing. These are not
 * "computed values" in the sense Constitution IV warns about; they are the state at the moment of
 * the booking, which is precisely what a record of that moment is for.
 *
 * <p><b>Why the direction is a field and not the sign of the amount.</b> Same reason
 * {@link BookingResult#INVALID_AMOUNT} exists: one fact, one representation. A negative amount here
 * would be a second way of writing a debit, and the two would eventually disagree.
 *
 * @param id database identity; 0 until written
 * @param characterId whose balance moved
 * @param occurredAt when
 * @param amount how much; always positive
 * @param direction which way
 * @param reason where it came from - never absent (FR-005)
 * @param balanceBefore the balance before this booking
 * @param balanceAfter the balance after it
 * @param actor the operator who caused it, empty for anything that happened through play
 */
public record LedgerEntry(
        long id,
        UUID characterId,
        Instant occurredAt,
        long amount,
        Direction direction,
        BookingReason reason,
        long balanceBefore,
        long balanceAfter,
        Optional<String> actor) {

    /** Which way a booking moved the balance. */
    public enum Direction {
        CREDIT,
        DEBIT
    }

    public LedgerEntry {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive, but was " + amount);
        }
        if (balanceBefore < 0L || balanceAfter < 0L) {
            throw new IllegalArgumentException(
                    "balances must not be negative, but were "
                            + balanceBefore
                            + " and "
                            + balanceAfter);
        }
        if (actor.isPresent() && actor.get().isBlank()) {
            throw new IllegalArgumentException("actor must not be blank when present");
        }
        if (reason.isAdministrative() && actor.isEmpty()) {
            throw new IllegalArgumentException(
                    reason + " is an operator action and must name who caused it");
        }
    }

    /** An entry that has not been written yet. */
    public static LedgerEntry pending(
            UUID characterId,
            Instant occurredAt,
            long amount,
            Direction direction,
            BookingReason reason,
            long balanceBefore,
            long balanceAfter,
            Optional<String> actor) {
        return new LedgerEntry(
                0L,
                characterId,
                occurredAt,
                amount,
                direction,
                reason,
                balanceBefore,
                balanceAfter,
                actor);
    }

    /** Whether an operator caused this. Those entries are never pruned (FR-038). */
    public boolean isAdministrative() {
        return actor.isPresent();
    }
}

package rpg.core.currency;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The balances of every loaded character, and the only place they change (FR-006).
 *
 * <p><b>One lock per account, and check-and-change under it.</b> {@link ConcurrentHashMap#compute}
 * gives exactly that: the mapping function for one key runs under that key's bin lock, so two
 * bookings on the same character are serialised while bookings on different characters are not. Two
 * abilities in the same tick therefore cannot both spend the same coins.
 *
 * <p><b>Why there is no "check now, pay later" pair.</b> A reservation would have to be released,
 * which means a path where it is not - a disconnect mid-purchase, an exception between the halves -
 * and coins that are neither spent nor available. {@link #canAfford} is a question about the present
 * moment and says so; anything that means to take the coins calls {@link #debit}.
 *
 * <p><b>The ledger entry is produced inside the lock</b> (FR-037). Outside it, the balance could
 * have moved between reading it and recording it, and {@code balanceBefore}/{@code balanceAfter}
 * would disagree with the entry next to them. Producing it is cheap - it is queued, not written.
 *
 * <p><b>Nothing here touches the database.</b> A booking changes the cached value and marks the
 * character; the write-behind buffer does the rest (Constitution II).
 */
public final class DefaultCurrency implements Currency {

    private final CurrencyConfig config;
    private final CharacterBalanceRepository repository;
    private final LedgerWriter ledger;
    private final Clock clock;
    private final Logger logger;

    /**
     * Balances of loaded characters.
     *
     * <p>Absence means <b>not loaded</b>, never zero. A loaded character with no stored row is
     * present here holding zero, which is what makes {@link #balanceOf} able to tell the two apart.
     */
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    /**
     * The last value of a character whose session is closing (ADR-015 point 7).
     *
     * <p>The flush runs asynchronously and therefore normally <em>after</em> {@code release}, where
     * nothing live is left to read. Without this, the last bookings of a session would be marked and
     * then read back as nothing. B04 keeps the same map for the same reason; B06 did not at first,
     * and that was one of the two defects ADR-015 was written about.
     */
    private final Map<UUID, Long> lastKnown = new ConcurrentHashMap<>();

    public DefaultCurrency(
            CurrencyConfig config,
            CharacterBalanceRepository repository,
            LedgerWriter ledger,
            Clock clock,
            Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // --- reading -----------------------------------------------------------------------------

    @Override
    public OptionalLong balanceOf(UUID characterId) {
        Long held = balances.get(Objects.requireNonNull(characterId, "characterId"));
        return held == null ? OptionalLong.empty() : OptionalLong.of(held);
    }

    @Override
    public long balanceOrZero(UUID characterId) {
        Long held = balances.get(Objects.requireNonNull(characterId, "characterId"));
        return held == null ? 0L : held;
    }

    @Override
    public boolean canAfford(UUID characterId, long amount) {
        Long held = balances.get(Objects.requireNonNull(characterId, "characterId"));
        if (held == null) {
            // Not loaded is not "can pay". Answering yes here for a free price would have made an
            // offline character look solvent, which is the one thing this method must never do.
            return false;
        }
        return amount <= 0L || held >= amount;
    }

    // --- booking -----------------------------------------------------------------------------

    @Override
    public BookingResult credit(UUID characterId, long amount, BookingReason reason) {
        return book(characterId, amount, reason, LedgerEntry.Direction.CREDIT, Optional.empty());
    }

    @Override
    public BookingResult debit(UUID characterId, long amount, BookingReason reason) {
        return book(characterId, amount, reason, LedgerEntry.Direction.DEBIT, Optional.empty());
    }

    /**
     * The one place a balance changes.
     *
     * <p>Every path in - credit, debit, the operator's intervention, the starting balance - comes
     * through here, which is what makes "no booking without a reason" a property of the code rather
     * than a rule people remember (FR-005).
     *
     * <p><b>Public, but not part of {@link Currency}.</b> An operator's intervention is the only
     * caller that needs to name an actor, and it lives in another module because reaching an offline
     * character means reaching the database. Putting this on the public interface instead would have
     * offered every block a way to book in somebody else's name.
     *
     * @param actor the operator who caused this, empty for anything that happened through play
     */
    public BookingResult book(
            UUID characterId,
            long amount,
            BookingReason reason,
            LedgerEntry.Direction direction,
            Optional<String> actor) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(actor, "actor");

        if (amount <= 0L) {
            return BookingResult.INVALID_AMOUNT;
        }
        if (direction == LedgerEntry.Direction.CREDIT && !reason.allowsCredit()) {
            throw new IllegalArgumentException(reason + " cannot credit");
        }
        if (direction == LedgerEntry.Direction.DEBIT && !reason.allowsDebit()) {
            throw new IllegalArgumentException(reason + " cannot debit");
        }

        // Holds the outcome of the mapping function, which cannot return one alongside the value.
        BookingResult[] outcome = {BookingResult.NO_SUCH_CHARACTER};
        long[] before = {0L};
        long[] after = {0L};

        balances.computeIfPresent(
                characterId,
                (id, held) -> {
                    if (direction == LedgerEntry.Direction.CREDIT) {
                        if (held > Long.MAX_VALUE - amount) {
                            // Refused, not wrapped: a wrapped balance is a negative one, and that is
                            // the single promise this block cannot break (FR-010).
                            outcome[0] = BookingResult.WOULD_OVERFLOW;
                            return held;
                        }
                        before[0] = held;
                        after[0] = held + amount;
                        outcome[0] = BookingResult.OK;
                        return after[0];
                    }
                    if (held < amount) {
                        // Refused, never capped to zero. A silent cap is a gift nobody notices, and
                        // the player would believe they had spent what they still have (FR-004).
                        outcome[0] = BookingResult.NOT_ENOUGH;
                        return held;
                    }
                    before[0] = held;
                    after[0] = held - amount;
                    outcome[0] = BookingResult.OK;
                    return after[0];
                });

        if (outcome[0] != BookingResult.OK) {
            return outcome[0];
        }

        // Inside the change, not after it: the entry has to describe the step it belongs to.
        ledger.append(
                LedgerEntry.pending(
                        characterId,
                        clock.instant(),
                        amount,
                        direction,
                        reason,
                        before[0],
                        after[0],
                        actor));

        markDirty(characterId);
        return BookingResult.OK;
    }

    /**
     * Marks the character, and undoes nothing if that fails.
     *
     * <p>Same trade B06 makes: the balance stands in memory but is not queued for writing, and
     * {@link #onSessionClosing} marks again before releasing, so the loss is bounded by one autosave
     * interval. The other order would let a flush between marking and changing write the old value
     * and clear the mark - the same loss, without the fallback at the end of the session.
     */
    private void markDirty(UUID characterId) {
        try {
            repository.markDirty(characterId);
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "[currency] could not mark " + characterId + " for writing", failure);
        }
    }

    // --- session lifecycle -------------------------------------------------------------------

    /**
     * Takes a character's stored balance into the cache.
     *
     * <p>Called with what the session bundle carried, so the login costs no second database round
     * (ADR-015 point 3). An empty argument means the character has never been stored, and it is
     * loaded as <b>zero</b> - the starting balance is a booking, not a default (FR-011a, FR-011b).
     */
    public void onCharacterLoaded(UUID characterId, Optional<CharacterBalance> stored) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(stored, "stored");
        balances.put(characterId, stored.map(CharacterBalance::balance).orElse(0L));
    }

    /**
     * Credits the configured starting balance, once, at character creation (FR-011a).
     *
     * <p>Zero means <b>no booking at all</b> (FR-011c) - a credit of zero is a caller error by
     * FR-009, and more to the point there is nothing to record. That is also why raising this number
     * later cannot enrich anyone retroactively: there is no read path that consults it.
     *
     * @return the outcome, or {@link BookingResult#OK} when there was nothing to do
     */
    public BookingResult onCharacterCreated(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        balances.putIfAbsent(characterId, 0L);
        if (config.startingBalance() <= 0L) {
            return BookingResult.OK;
        }
        return credit(characterId, config.startingBalance(), BookingReason.STARTING_BALANCE);
    }

    /**
     * The order at the end of a session: stash, mark, release (ADR-015 point 7, FR-016).
     *
     * <p>Reversing any two of these loses the last bookings of the session.
     */
    public void onSessionClosing(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        Long held = balances.get(characterId);
        if (held == null) {
            return;
        }
        lastKnown.put(characterId, held);
        markDirty(characterId);
        balances.remove(characterId);
    }

    /**
     * What the flush should write for this character.
     *
     * <p>Reads the live value while the session is open and the stashed one afterwards, clearing it
     * on the way out so a character who logs back in does not read a stale number.
     */
    public OptionalLong liveOrLastKnown(UUID characterId) {
        Long held = balances.get(Objects.requireNonNull(characterId, "characterId"));
        if (held != null) {
            return OptionalLong.of(held);
        }
        Long stashed = lastKnown.remove(characterId);
        return stashed == null ? OptionalLong.empty() : OptionalLong.of(stashed);
    }

    /** How many characters are currently loaded. For diagnostics and tests. */
    public int loadedCount() {
        return balances.size();
    }

    /** Everything currently held, for the operator's window. Never used in a gameplay path. */
    public Map<UUID, Long> snapshot() {
        return new HashMap<>(balances);
    }
}

package rpg.persistence.currency;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.CharacterBalance;
import rpg.core.currency.CurrencyAdmin;
import rpg.core.currency.DefaultCurrency;
import rpg.core.currency.LedgerEntry;
import rpg.core.currency.LedgerWriter;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.persistence.PersistenceException;

/**
 * The operator's way into a balance (FR-039 to FR-045).
 *
 * <p>Lives in {@code rpg-persistence} rather than {@code rpg-core} for one reason: reaching an
 * <em>offline</em> character means reaching the database, and {@code rpg-core} owns no connection.
 * The rules it applies - never negative, always a reason, always an actor - are the same ones
 * {@link DefaultCurrency} applies, and they are applied by calling it rather than by repeating them.
 *
 * <p><b>Online first, always.</b> If the character is loaded, the change has to happen in the
 * authoritative cache; writing straight to the table would be overwritten by the next flush
 * (Constitution IV). That ordering is the whole subtlety of this class.
 */
public final class JdbcCurrencyAdmin implements CurrencyAdmin {

    private final DefaultCurrency currency;
    private final DataSource writePool;
    private final LedgerWriter ledger;
    private final AuditLogRepository auditLog;
    private final Clock clock;
    private final Logger logger;

    public JdbcCurrencyAdmin(
            DefaultCurrency currency,
            DataSource writePool,
            LedgerWriter ledger,
            AuditLogRepository auditLog,
            Clock clock,
            Logger logger) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.writePool = Objects.requireNonNull(writePool, "writePool");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public BookingResult set(UUID characterId, long amount, String actor) {
        requireActor(actor);
        if (amount < 0L) {
            return BookingResult.INVALID_AMOUNT;
        }
        OptionalLong current = balanceOf(characterId);
        if (current.isEmpty()) {
            return BookingResult.NO_SUCH_CHARACTER;
        }
        long difference = amount - current.getAsLong();
        if (difference == 0L) {
            // Nothing changed, so nothing is written down. A ledger entry saying "500 became 500"
            // would be noise in the one place that has to stay readable.
            return BookingResult.OK;
        }
        return difference > 0L
                ? apply(characterId, difference, BookingReason.ADMIN_SET,
                        LedgerEntry.Direction.CREDIT, actor)
                : apply(characterId, -difference, BookingReason.ADMIN_SET,
                        LedgerEntry.Direction.DEBIT, actor);
    }

    @Override
    public BookingResult add(UUID characterId, long amount, String actor) {
        requireActor(actor);
        return apply(
                characterId, amount, BookingReason.ADMIN_ADD, LedgerEntry.Direction.CREDIT, actor);
    }

    @Override
    public BookingResult remove(UUID characterId, long amount, String actor) {
        requireActor(actor);
        return apply(
                characterId, amount, BookingReason.ADMIN_REMOVE, LedgerEntry.Direction.DEBIT, actor);
    }

    /**
     * Credits a character whether or not they are online, without an operator behind it.
     *
     * <p>For the pile cap (FR-030c): when the server clears away the oldest pile to make room, its
     * owner is credited - and they may well be logged out, which is precisely why their pile was the
     * oldest. Going through {@code Currency} alone would have left those piles in place and made the
     * cap free a slot less often than it should.
     *
     * <p>No actor, because none caused it: this is the server managing its own object budget, not an
     * intervention. The ledger entry therefore looks like any other credit, which is right - the
     * player did nothing unusual.
     *
     * <p><b>Not on {@link rpg.core.currency.CurrencyAdmin}</b>, which is about operator actions.
     * This is the same machinery, used for something that is not one.
     */
    public BookingResult creditWhereverTheyAre(
            UUID characterId, long amount, BookingReason reason) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(reason, "reason");
        if (amount <= 0L) {
            return BookingResult.INVALID_AMOUNT;
        }
        if (currency.balanceOf(characterId).isPresent()) {
            return currency.credit(characterId, amount, reason);
        }
        return applyOffline(
                characterId, amount, reason, LedgerEntry.Direction.CREDIT, null);
    }

    /**
     * Applies one change, through the cache when the character is loaded and through the table when
     * it is not.
     */
    private BookingResult apply(
            UUID characterId,
            long amount,
            BookingReason reason,
            LedgerEntry.Direction direction,
            String actor) {
        Objects.requireNonNull(characterId, "characterId");
        if (amount <= 0L) {
            return BookingResult.INVALID_AMOUNT;
        }

        if (currency.balanceOf(characterId).isPresent()) {
            // Loaded, so the cache is authoritative. Going to the table instead would be undone by
            // the next flush (FR-043).
            BookingResult result =
                    currency.book(characterId, amount, reason, direction, Optional.of(actor));
            if (result.isSuccess()) {
                recordInAuditLog(characterId, reason, amount, actor);
            }
            return result;
        }
        return applyOffline(characterId, amount, reason, direction, actor);
    }

    /**
     * The offline path (FR-042).
     *
     * <p>Reaching the database here is deliberate and allowed: an operator command is not a game
     * event. The same three rules apply as online - never negative, a reason, an actor - and the
     * ledger entry is written the same way, so a later reader cannot tell which path was taken and
     * does not need to.
     */
    private BookingResult applyOffline(
            UUID characterId,
            long amount,
            BookingReason reason,
            LedgerEntry.Direction direction,
            String actor) {
        try (Connection connection = writePool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<CharacterBalance> stored =
                        JdbcCharacterBalanceRepository.read(connection, characterId);
                if (stored.isEmpty() && !characterExists(connection, characterId)) {
                    // No balance row AND no character: nothing is created on the way past (FR-044).
                    connection.rollback();
                    return BookingResult.NO_SUCH_CHARACTER;
                }

                long before = stored.map(CharacterBalance::balance).orElse(0L);
                long after;
                if (direction == LedgerEntry.Direction.CREDIT) {
                    if (before > Long.MAX_VALUE - amount) {
                        connection.rollback();
                        return BookingResult.WOULD_OVERFLOW;
                    }
                    after = before + amount;
                } else {
                    if (before < amount) {
                        // An operator does not get to create a negative balance either (FR-003).
                        connection.rollback();
                        return BookingResult.NOT_ENOUGH;
                    }
                    after = before - amount;
                }

                JdbcCharacterBalanceRepository.write(connection, characterId, after, clock);
                connection.commit();

                ledger.append(
                        LedgerEntry.pending(
                                characterId,
                                clock.instant(),
                                amount,
                                direction,
                                reason,
                                before,
                                after,
                                Optional.ofNullable(actor)));
                if (actor != null) {
                    // Only an intervention belongs in the audit log; the pile cap is not one.
                    recordInAuditLog(characterId, reason, amount, actor);
                }
                return BookingResult.OK;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException(
                    "could not apply an operator change to character " + characterId, failure);
        }
    }

    private boolean characterExists(Connection connection, UUID characterId) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "SELECT 1 FROM rpg.character WHERE character_id = ?")) {
            statement.setObject(1, characterId);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private OptionalLong balanceOf(UUID characterId) {
        OptionalLong live = currency.balanceOf(characterId);
        if (live.isPresent()) {
            return live;
        }
        try (Connection connection = writePool.getConnection()) {
            Optional<CharacterBalance> stored =
                    JdbcCharacterBalanceRepository.read(connection, characterId);
            if (stored.isPresent()) {
                return OptionalLong.of(stored.get().balance());
            }
            return characterExists(connection, characterId)
                    // A character with no row holds zero (FR-011b), which is a perfectly good
                    // starting point for a "set".
                    ? OptionalLong.of(0L)
                    : OptionalLong.empty();
        } catch (SQLException failure) {
            throw new PersistenceException(
                    "could not read the balance of character " + characterId, failure);
        }
    }

    /**
     * Records the intervention where B14 expects to find it (FR-041).
     *
     * <p>A failure here does not undo the booking: the coins have moved, and pretending otherwise
     * would be the worse inconsistency. It is logged loudly instead.
     */
    private void recordInAuditLog(
            UUID characterId, BookingReason reason, long amount, String actor) {
        try {
            auditLog.append(
                    new AuditEntry(
                            clock.instant(),
                            actor,
                            "currency_" + reason.name().toLowerCase(java.util.Locale.ROOT),
                            Optional.empty(),
                            Map.of(
                                    "character_id", characterId.toString(),
                                    "amount", String.valueOf(amount))));
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "[currency] the booking stands but could not be written to the audit log",
                    failure);
        }
    }

    private static void requireActor(String actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.isBlank()) {
            throw new IllegalArgumentException(
                    "an operator intervention has to name who caused it - that is the whole point");
        }
    }
}

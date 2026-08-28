package rpg.core.currency;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * The only way in and out of a balance (FR-007).
 *
 * <p>B07, B08, B11, B12 and B13 are built against this. Reaching past it - into
 * {@link DefaultCurrency}, into the ledger, into the pile entities - is not permitted
 * (Constitution III).
 *
 * <p><b>A change here is ADR-worthy from now on</b>, the same rule {@code CombatPipeline},
 * {@code StatEngine} and {@code AbilityRegistry} hold for themselves.
 */
public interface Currency {

    /**
     * What this character holds, or empty when nothing is loaded for them.
     *
     * <p>Answers from the session cache and touches no database (FR-056, Constitution II), so it is
     * safe in a gameplay path and safe for B13 to call while rendering.
     *
     * <p><b>Empty means "not loaded", never "zero".</b> The same distinction
     * {@code Progression.levelOf} makes in B06, and it matters more here: a bare {@code long} would
     * have conflated <em>logged out</em> with <em>broke</em>, and those are exactly the two the
     * operator's window has to tell apart. The balance of a character who is not online comes from
     * the repository, not from here.
     *
     * <p>A character who <em>is</em> loaded and has never been stored holds zero - not the
     * configured starting balance, which is booked once at creation instead (FR-011a, FR-011b).
     */
    OptionalLong balanceOf(UUID characterId);

    /**
     * Primitive form of {@link #balanceOf}; zero when nothing is loaded.
     *
     * <p>For paths that promise not to allocate per call - the same reason B06 offers
     * {@code levelOrZero} beside {@code levelOf}. Anywhere the difference between "logged out" and
     * "broke" matters, use {@link #balanceOf} instead.
     */
    long balanceOrZero(UUID characterId);

    /**
     * Can this character pay that?
     *
     * <p><b>This is not a reservation, and treating it as one is the mistake it invites.</b> The
     * answer may already be false on the next tick. It exists for display - B13 colouring a price
     * red - and for a friendlier refusal ahead of time, never as the first half of a two-step
     * purchase. Anything that actually intends to take the coins calls {@link #debit}.
     */
    boolean canAfford(UUID characterId, long amount);

    /**
     * Adds coins. The reason is mandatory (FR-005).
     *
     * <p>The amount is always positive; direction comes from the method, never from a sign
     * (FR-009).
     */
    BookingResult credit(UUID characterId, long amount, BookingReason reason);

    /**
     * Takes coins - checking and taking in one indivisible step (FR-006).
     *
     * <p><b>There is deliberately no variant that only checks and hands back a promise.</b> Two
     * abilities in the same tick would otherwise both spend the same coins: each would ask, each
     * would be told yes, and each would then take. The check and the change happen under one lock
     * per account, and nothing can happen between them.
     *
     * <p>A debit that would go below zero is <b>refused</b> and leaves the balance untouched. It is
     * never capped (FR-004).
     */
    BookingResult debit(UUID characterId, long amount, BookingReason reason);
}

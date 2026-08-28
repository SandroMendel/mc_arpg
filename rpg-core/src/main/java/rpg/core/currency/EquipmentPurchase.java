package rpg.core.currency;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.classes.ClassProgress;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAdvance;
import rpg.core.classes.TierAdvanceResult;
import rpg.core.session.CharacterClass;

/**
 * Paying for an equipment tier (FR-047, FR-048, FR-052).
 *
 * <p><b>This is what B07 has been waiting for.</b> Since ADR-017 every tier carries a {@code cost}
 * block, and B07 passes it through without reading it - deliberately, because there was no currency
 * to read it against. There is one now.
 *
 * <p><b>B07 is not modified.</b> The check sits in front of and behind
 * {@link TierAdvance#advance}, never inside it. That is why
 * {@code ClassSourceInvariantsTest} stays green untouched.
 *
 * <p><b>The order is the requirement</b> (FR-052): every other condition is settled before a single
 * coin moves. An advance that fails because the ladder is at its top, or because the level is too
 * low, must leave the balance exactly as it was - a player charged for something that did not happen
 * is the worst outcome this block can produce.
 */
public final class EquipmentPurchase {

    /** What came of trying to buy a tier. */
    public enum Outcome {
        /** Bought and advanced. */
        ADVANCED,
        /** The advance itself was refused - top of the ladder, level, unknown character. */
        REFUSED,
        /** Everything else was fine; the coins were not there (FR-048). */
        NOT_ENOUGH_COINS
    }

    /** The outcome, plus whichever detail applies. */
    public record Result(
            Outcome outcome, Optional<TierAdvanceResult> advance, Optional<CostSpec> cost) {

        public boolean isSuccess() {
            return outcome == Outcome.ADVANCED;
        }
    }

    private final TierAdvance tiers;
    private final Currency currency;
    private final Function<UUID, Optional<CharacterClass>> classOf;
    private final Function<UUID, Optional<ClassProgress>> progressOf;
    private final Logger logger;

    public EquipmentPurchase(
            TierAdvance tiers,
            Currency currency,
            Function<UUID, Optional<CharacterClass>> classOf,
            Function<UUID, Optional<ClassProgress>> progressOf,
            Logger logger) {
        this.tiers = Objects.requireNonNull(tiers, "tiers");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.progressOf = Objects.requireNonNull(progressOf, "progressOf");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** What the next tier of this ladder costs, or empty when there is no next tier. */
    public Optional<CostSpec> costOfNext(UUID characterId, LadderSlot slot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");

        Optional<CharacterClass> id = classOf.apply(characterId);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        int current =
                progressOf
                        .apply(characterId)
                        .map(progress -> progress.tierOf(slot))
                        .orElse(ClassProgress.INITIAL_TIER);
        try {
            Map<String, Object> block = tiers.costOf(id.get(), slot, current + 1);
            return Optional.of(CostSpec.parse(block, where(id.get(), slot, current + 1)));
        } catch (IllegalArgumentException noSuchTier) {
            // Already at the top: there is no next tier to price. Not an error - the caller finds
            // out from the advance itself, with the message that belongs to it.
            return Optional.empty();
        }
    }

    /**
     * Buys the next tier of one ladder.
     *
     * <p>Three steps, and the order of them is the whole point:
     *
     * <ol>
     *   <li>ask what it costs and whether the character could pay - a cheap "no" before anything
     *       happens, and the answer FR-048 asks for;
     *   <li>let B07 advance, which settles every non-monetary condition. A refusal here leaves the
     *       balance untouched (FR-052);
     *   <li>only then take the coins.
     * </ol>
     */
    public Result buyNext(UUID characterId, LadderSlot slot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");

        Optional<CostSpec> cost = costOfNext(characterId, slot);

        // Step 1. Only a pre-check - canAfford is a question about this moment, never a reservation.
        if (cost.isPresent()
                && !cost.get().isFree()
                && !currency.canAfford(characterId, cost.get().coins())) {
            return new Result(Outcome.NOT_ENOUGH_COINS, Optional.empty(), cost);
        }

        // Step 2. Everything that is not about money.
        TierAdvanceResult advance = tiers.advance(characterId, slot);
        if (!advance.advanced()) {
            return new Result(Outcome.REFUSED, Optional.of(advance), cost);
        }

        // Step 3. The tier is theirs; now it is paid for.
        if (cost.isPresent() && !cost.get().isFree()) {
            BookingResult booking =
                    currency.debit(characterId, cost.get().coins(), BookingReason.EQUIPMENT_TIER);
            if (!booking.isSuccess()) {
                // Unreachable while everything runs on the tick: the balance was sufficient one
                // statement ago and only this path spends it. If it ever does happen, the player
                // keeps a tier they did not pay for - which is the better of the two wrong answers,
                // and it is logged rather than quietly swallowed.
                logger.log(
                        Level.WARNING,
                        "[currency] "
                                + characterId
                                + " advanced "
                                + slot
                                + " but the debit of "
                                + cost.get().coins()
                                + " came back "
                                + booking
                                + " - the tier stands unpaid");
            }
        }
        return new Result(Outcome.ADVANCED, Optional.of(advance), cost);
    }

    private static String where(CharacterClass id, LadderSlot slot, int tier) {
        return "classes." + id.name().toLowerCase(java.util.Locale.ROOT) + "." + slot.name().toLowerCase(java.util.Locale.ROOT) + ".tier " + tier;
    }
}

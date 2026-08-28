package rpg.core.currency;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityRuntime;

/**
 * What a rank costs, charged (FR-051, FR-053, FR-054).
 *
 * <p><b>This is what B08 has been waiting for.</b> {@code RankResult} used to carry a paragraph
 * saying nobody paid for an advance because there was no currency anywhere; the price was never
 * guessed, and when the currency arrived the enum grew by one value.
 *
 * <p><b>B08 does not point at this class</b> - it installs it through
 * {@link AbilityRuntime.RankCost}. Two layer-1 blocks must not depend on each other in the wrong
 * direction (ADR-027).
 *
 * <p><b>The price lives in the ability configuration</b>, opaque, exactly like B07's tier costs. This
 * class reads it; nothing in B08 does (FR-053).
 *
 * <p>An ability without a {@code rank-cost} block advances for free (FR-054) - and that is the
 * shipped state until somebody sets the numbers.
 */
public final class AbilityRankCost implements AbilityRuntime.RankCost {

    private final Currency currency;
    private final Logger logger;

    public AbilityRankCost(Currency currency, Logger logger) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Charges for one rank.
     *
     * <p>Called by {@code advanceRank} <b>after</b> the unlock and maximum-rank checks, so a refusal
     * here means the coins and only the coins (FR-052).
     *
     * @return whether the advance may proceed
     */
    @Override
    public boolean charge(UUID characterId, Ability ability) {
        CostSpec cost;
        try {
            cost = CostSpec.parse(ability.rankCost(), "abilities." + ability.id() + ".rank-cost");
        } catch (IllegalArgumentException malformed) {
            // Unreachable in production: the same block was validated at startup (FR-050). If it
            // ever happens, refusing the advance is safer than charging an amount nobody can read.
            logger.log(
                    Level.SEVERE,
                    "[currency] the rank cost of " + ability.id() + " is unreadable",
                    malformed);
            return false;
        }

        if (cost.isFree()) {
            return true;
        }
        return currency.debit(characterId, cost.coins(), BookingReason.ABILITY_RANK).isSuccess();
    }
}

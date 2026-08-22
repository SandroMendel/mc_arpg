package rpg.platform.currency;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.entity.Item;

import rpg.core.currency.BookingReason;
import rpg.core.currency.CurrencyConfig;

/**
 * Knows which piles are lying in the world, so the cap can be enforced (FR-030).
 *
 * <p><b>There is no sweep and no scheduled task.</b> Stale entries are cleared out at the only
 * moment anyone cares - when a new pile is about to be created and the cap might bite. At any other
 * time an entry for a pile vanilla already removed costs a map slot and nothing else.
 *
 * <p><b>When the cap is reached the oldest pile in the world is credited and cleared away</b>
 * (FR-030a), and the distinction to an expiry is the whole point: a pile whose <em>timer</em> ran
 * out is credited to nobody, because the player had time and let it pass. A pile the <em>server</em>
 * takes away is credited, because the player could do nothing about server load. Own neglect costs;
 * server load does not (FR-030d).
 *
 * <p>Oldest first, deliberately: that is the pile most likely already forgotten.
 */
public final class CoinPileRegistry implements CoinPile.PileCap {

    /**
     * What a pile needs to be paid out when the server takes it away.
     *
     * <p><b>Known gap, and it is named rather than hidden.</b> FR-030c requires that an <em>offline</em>
     * owner is credited too. The implementation wired in today books through {@code Currency}, which
     * only knows loaded characters - so an offline owner's pile is currently left in place and the
     * next one is cashed in instead. Nothing is lost, but the cap frees a slot less often than it
     * could.
     *
     * <p>Closing it needs the repository path an operator intervention uses for an offline character
     * (T078). Until then this interface is exactly the seam that will take it, which is why the
     * signature already talks about a character rather than a session.
     */
    @FunctionalInterface
    public interface Payout {
        /**
         * @return whether the amount reached the character; false leaves the pile in place
         */
        boolean credit(UUID characterId, long amount, BookingReason reason);
    }

    private record Entry(Item pile, UUID characterId, long createdAtMillis) {}

    private final CurrencyConfig config;
    private final Payout payout;
    private final Clock clock;
    private final Logger logger;

    private final Map<UUID, Entry> piles = new ConcurrentHashMap<>();

    public CoinPileRegistry(
            CurrencyConfig config, Payout payout, Clock clock, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.payout = Objects.requireNonNull(payout, "payout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Notes a pile that now exists. */
    public void register(Item pile, UUID characterId) {
        Objects.requireNonNull(pile, "pile");
        Objects.requireNonNull(characterId, "characterId");
        piles.put(pile.getUniqueId(), new Entry(pile, characterId, clock.millis()));
    }

    /** Forgets a pile that was picked up. */
    public void forget(Item pile) {
        if (pile != null) {
            piles.remove(pile.getUniqueId());
        }
    }

    /** How many piles are believed to be lying in the world. */
    public int size() {
        return piles.size();
    }

    /**
     * Frees a slot if the cap is reached.
     *
     * <p>First drops what is already gone - vanilla despawned it, or the chunk went away. Only if
     * that is not enough does it cash in the oldest pile.
     *
     * @return whether there is room now
     */
    @Override
    public boolean makeRoom() {
        forgetWhatIsGone();
        if (piles.size() < config.maxPiles()) {
            return true;
        }
        return cashInOldest();
    }

    /**
     * Drops entries whose pile no longer exists.
     *
     * <p>Two criteria, because either alone would miss cases: {@code isValid} catches what was
     * removed, and the age catches a pile in an unloaded chunk whose entity object may still look
     * alive. Both are cheap on a set the cap keeps small.
     */
    private void forgetWhatIsGone() {
        long now = clock.millis();
        long lifetime = config.pileDespawn().toMillis();
        piles.entrySet()
                .removeIf(
                        entry ->
                                !entry.getValue().pile().isValid()
                                        || now - entry.getValue().createdAtMillis() > lifetime);
    }

    /** Credits the oldest pile's owner and removes it, so a new one can take its place. */
    private boolean cashInOldest() {
        List<Entry> byAge = new ArrayList<>(piles.values());
        byAge.sort(Comparator.comparingLong(Entry::createdAtMillis));

        for (Entry entry : byAge) {
            long amount = CoinPileTag.amountOf(entry.pile().getItemStack()).orElse(0L);
            if (amount <= 0L) {
                // Nothing to pay out - just take the slot back.
                entry.pile().remove();
                piles.remove(entry.pile().getUniqueId());
                return true;
            }
            if (!payout.credit(entry.characterId(), amount, BookingReason.PILE_CASHED_IN)) {
                // The owner could not be credited - at the far edge of the number range, say. Then
                // this pile stays where it is and the next one is tried; losing it would take coins
                // from somebody who was not even involved in the kill that triggered this.
                continue;
            }
            entry.pile().remove();
            piles.remove(entry.pile().getUniqueId());
            logger.info(
                    "[currency] pile cap of "
                            + config.maxPiles()
                            + " reached - cashed in "
                            + amount
                            + " coins from the oldest pile so a new one could appear");
            return true;
        }
        // Every pile refused to be cashed in. Vanishingly unlikely, and the honest answer is that
        // there is no room - the caller then credits directly rather than dropping.
        logger.warning(
                "[currency] pile cap of "
                        + config.maxPiles()
                        + " reached and no pile could be cashed in - crediting directly instead");
        return false;
    }
}

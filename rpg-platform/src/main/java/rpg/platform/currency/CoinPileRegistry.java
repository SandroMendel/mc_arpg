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
import org.bukkit.entity.Player;

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
     * <p><b>An offline owner is credited too</b> (FR-030c), and that is not incidental: the pile most
     * likely to be the oldest belongs to somebody who logged out, which is precisely why it is the
     * oldest. The wiring goes through {@code JdbcCurrencyAdmin.creditWhereverTheyAre}, which reaches
     * the stored balance when the character is not loaded. That is why this signature talks about a
     * character and not about a session.
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
    private final CoinPile.PilePlatform platform;

    private final Map<UUID, Entry> piles = new ConcurrentHashMap<>();

    /**
     * @param platform how a pile is shown to a player - {@code PilePlatform.vanilla(plugin)} in
     *     production, a recorder in a test. There is deliberately no convenience constructor without
     *     it: showing a pile is not optional (FR-027a), and a default would have to invent a plugin.
     */
    public CoinPileRegistry(
            CurrencyConfig config,
            Payout payout,
            Clock clock,
            Logger logger,
            CoinPile.PilePlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
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

    /**
     * Shows this character's piles to the player again.
     *
     * <p><b>Why this is needed at all.</b> {@code Player.showEntity} is state on the <em>connection</em>,
     * not on the entity: it is gone the moment the player disconnects. A pile is
     * {@code setVisibleByDefault(false)}, so after a relogin it is invisible again - while the vanilla
     * owner flag and our character check both still pass, which is why it could still be walked over
     * and collected. Invisible but collectable is the worst of both.
     *
     * <p>Called when a character enters play, which is also the only moment it can be needed: nothing
     * else takes a shown entity away for the rest of a session.
     *
     * <p>Silently does nothing for piles of other characters, including the player's own other
     * characters - a pile belongs to the character that earned it (ADR-011).
     */
    public void showPilesTo(Player player, UUID characterId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(characterId, "characterId");
        forgetWhatIsGone();
        for (Entry entry : piles.values()) {
            if (entry.characterId().equals(characterId) && entry.pile().isValid()) {
                platform.showTo(entry.pile(), player);
            }
        }
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

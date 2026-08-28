package rpg.core.currency;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * The validated contents of {@code currency.yml} (FR-058).
 *
 * <p><b>No prices here.</b> Tier costs live in {@code classes.yml}, rank costs in
 * {@code abilities.yml}, repair in B11 (ADR-027). What this record holds is how coins come into the
 * world and how long the record of them is kept - never what anything is worth.
 *
 * @param startingBalance credited once at character creation; zero means no booking at all
 * @param defaultDrop what a creature without an entry of its own drops - never zero by accident
 * @param dropsByType overrides, keyed by the Bukkit type name in upper case
 * @param pileDespawn how long a pile lies before it is gone for good
 * @param mergeRadius piles of the same character within this range become one
 * @param maxPiles ceiling on piles lying in the world at once
 * @param ledgerRetention how long bookings from play are kept; operator entries are exempt
 * @param historyPageSize entries per page in the history window
 */
public record CurrencyConfig(
        long startingBalance,
        long defaultDrop,
        Map<String, Long> dropsByType,
        Duration pileDespawn,
        double mergeRadius,
        int maxPiles,
        Duration ledgerRetention,
        int historyPageSize) {

    public CurrencyConfig {
        if (startingBalance < 0L) {
            throw new IllegalArgumentException(
                    "account.starting-balance must not be negative, but was " + startingBalance);
        }
        if (defaultDrop < 0L) {
            throw new IllegalArgumentException(
                    "drops.default must not be negative, but was " + defaultDrop);
        }
        dropsByType = Map.copyOf(Objects.requireNonNull(dropsByType, "dropsByType"));
        Objects.requireNonNull(pileDespawn, "pileDespawn");
        Objects.requireNonNull(ledgerRetention, "ledgerRetention");
        if (pileDespawn.isZero() || pileDespawn.isNegative()) {
            throw new IllegalArgumentException(
                    "drops.despawn-seconds must be positive, but was " + pileDespawn.toSeconds());
        }
        // A pile despawns because vanilla ages it out; there is no per-entity despawn setter in the
        // Paper API (research.md R1c). A pile is therefore spawned pre-aged, which caps the reachable
        // lifetime at vanilla's own. Anything above simply would not take effect, and a number that
        // does not take effect is worse than no number - so it is refused rather than ignored.
        if (pileDespawn.toSeconds() > MAX_PILE_DESPAWN_SECONDS) {
            throw new IllegalArgumentException(
                    "drops.despawn-seconds must not exceed "
                            + MAX_PILE_DESPAWN_SECONDS
                            + " (vanilla's own item lifetime), but was "
                            + pileDespawn.toSeconds()
                            + " - a pile is spawned pre-aged, so a longer value cannot take effect");
        }
        if (mergeRadius <= 0.0d) {
            throw new IllegalArgumentException(
                    "drops.merge-radius must be positive, but was " + mergeRadius);
        }
        // Above this it stops being a nearby lookup and starts being a scan (Constitution II).
        if (mergeRadius > MAX_MERGE_RADIUS) {
            throw new IllegalArgumentException(
                    "drops.merge-radius must not exceed "
                            + MAX_MERGE_RADIUS
                            + " blocks, but was "
                            + mergeRadius
                            + " - beyond that it is no longer a nearby lookup");
        }
        if (maxPiles <= 0) {
            throw new IllegalArgumentException(
                    "drops.max-piles must be positive, but was " + maxPiles);
        }
        if (ledgerRetention.isZero() || ledgerRetention.isNegative()) {
            throw new IllegalArgumentException(
                    "ledger.retention-days must be positive, but was "
                            + ledgerRetention.toDays());
        }
        if (historyPageSize <= 0 || historyPageSize > MAX_HISTORY_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "history.page-size must be between 1 and "
                            + MAX_HISTORY_PAGE_SIZE
                            + ", but was "
                            + historyPageSize
                            + " - the bottom row of the window carries the paging buttons");
        }
    }

    /** Beyond this a nearby lookup stops being one (Constitution II). */
    public static final double MAX_MERGE_RADIUS = 16.0d;

    /**
     * Vanilla ages an item out after 6000 ticks. A pile is spawned pre-aged to reach the configured
     * lifetime, so that is also the longest one can ask for (research.md R1c).
     */
    public static final long VANILLA_ITEM_LIFETIME_TICKS = 6000L;

    /** The same, in seconds - the ceiling {@code drops.despawn-seconds} is checked against. */
    public static final long MAX_PILE_DESPAWN_SECONDS = VANILLA_ITEM_LIFETIME_TICKS / 20L;

    /**
     * How aged a pile is spawned so that it has {@code despawn} left to live.
     *
     * <p>At least one tick: a pile that arrives already expired would vanish before anyone saw it.
     */
    public int spawnTicksLived() {
        long lived = VANILLA_ITEM_LIFETIME_TICKS - pileDespawn.toSeconds() * 20L;
        return (int) Math.max(1L, lived);
    }

    /** Five rows of nine; the sixth carries the paging buttons. */
    public static final int MAX_HISTORY_PAGE_SIZE = 45;

    /**
     * What this creature type drops, or the default.
     *
     * <p>A missing entry means the default, <b>not</b> zero (FR-023) - the same promise
     * {@code MobXpProvider} makes in B06, and for the same reason: a mob Mojang added last week
     * should not be silently worthless.
     */
    public long dropFor(String mobTypeKey) {
        Long own = dropsByType.get(mobTypeKey);
        return own != null ? own : defaultDrop;
    }
}

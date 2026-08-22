package rpg.platform.currency;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * What a coin pile carries, written into the stack's persistent data container.
 *
 * <p><b>Data container, not lore.</b> The same choice B07 made for {@code BoundItemTag}, and for the
 * same reason: lore is display, and display is something a client can be made to lie about.
 *
 * <p>Three values, and each is load-bearing:
 *
 * <ul>
 *   <li><b>amount</b> - what the pile is worth. Never the stack size; see below.
 *   <li><b>character</b> - who may pick it up. The <b>character</b>, not the player: a player has up
 *       to three and can switch between them mid-session, and without this check character B would
 *       collect what character A earned (ADR-011).
 *   <li><b>pile</b> - a unique id per pile, and it exists for one reason only: <b>to keep vanilla
 *       from merging two piles</b>. Vanilla merges similar stacks by adding their counts, so two
 *       piles of 500 would become one stack of two carrying 500 - the player would silently lose
 *       half, and nothing anywhere would look wrong. Two stacks are similar only if their data
 *       containers match, so a unique id makes them permanently dissimilar.
 *   <li><b>created</b> - when it appeared, which is how "the oldest pile" is decided when the cap
 *       bites (FR-030a).
 * </ul>
 */
public final class CoinPileTag {

    /** One key per value, all under one namespace. Fixed, because they outlive a restart. */
    static final NamespacedKey AMOUNT =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:coin_amount"));

    static final NamespacedKey CHARACTER =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:coin_character"));

    static final NamespacedKey PILE =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:coin_pile"));

    static final NamespacedKey CREATED =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:coin_created"));

    private CoinPileTag() {}

    /** Writes all four values. Called only while building a pile; nothing else may create one. */
    static void write(ItemMeta meta, long amount, UUID characterId, long createdAtMillis) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(characterId, "characterId");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(AMOUNT, PersistentDataType.LONG, amount);
        container.set(CHARACTER, PersistentDataType.STRING, characterId.toString());
        container.set(CREATED, PersistentDataType.LONG, createdAtMillis);
        // Unique per pile, so no two piles are ever isSimilar and vanilla never merges them.
        container.set(PILE, PersistentDataType.STRING, UUID.randomUUID().toString());
    }

    /** Replaces the amount on an existing pile - the merge path (FR-028). */
    static void writeAmount(ItemMeta meta, long amount) {
        Objects.requireNonNull(meta, "meta");
        meta.getPersistentDataContainer().set(AMOUNT, PersistentDataType.LONG, amount);
    }

    /**
     * The amount on this stack, if it is one of ours.
     *
     * <p>In the path of every pickup attempt, so it does the cheapest thing that can work: no meta
     * clone where the API allows avoiding it, no exception on an untagged item, and empty for
     * everything that is not ours - which is almost every item a player touches.
     */
    public static Optional<Long> amountOf(ItemStack stack) {
        return read(stack, AMOUNT, PersistentDataType.LONG);
    }

    /** The character entitled to this pile, if it is one of ours. */
    public static Optional<UUID> characterOf(ItemStack stack) {
        return read(stack, CHARACTER, PersistentDataType.STRING).map(CoinPileTag::parseUuid);
    }

    /** When this pile appeared, in epoch millis, if it is one of ours. */
    public static Optional<Long> createdAtOf(ItemStack stack) {
        return read(stack, CREATED, PersistentDataType.LONG);
    }

    /** Whether this stack is a coin pile at all. */
    public static boolean isCoinPile(ItemStack stack) {
        return amountOf(stack).isPresent();
    }

    private static <T> Optional<T> read(
            ItemStack stack, NamespacedKey key, PersistentDataType<?, T> type) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(meta.getPersistentDataContainer().get(key, type));
    }

    /**
     * A malformed id means the pile is not ours to hand out.
     *
     * <p>Returning null rather than throwing: this runs in a pickup attempt, and an exception in a
     * gameplay path must not leave a player in an inconsistent state (Constitution VI). An
     * unreadable pile simply cannot be claimed by anyone and expires.
     */
    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}

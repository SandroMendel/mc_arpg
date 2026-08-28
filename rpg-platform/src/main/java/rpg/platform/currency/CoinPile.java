package rpg.platform.currency;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import rpg.core.currency.CoinDropPlan;
import rpg.core.currency.CurrencyConfig;
import rpg.core.progression.WorldPoint;

/**
 * Puts a coin pile into the world, merges it with a neighbour, and clears the oldest away.
 *
 * <p><b>A pile is a plain vanilla {@code Item} entity</b> (research.md R1). That is what gives the
 * block its owner check, its despawn and its rendering without a line of runtime code of our own -
 * and above all, without a sweep. The only sweep in the project runs asynchronously and must not
 * touch the Bukkit API, so a second, synchronous one would have been needed; {@code Scheduler}
 * deliberately offers no recurring synchronous task.
 *
 * <p><b>Vanilla merging is switched off, not used.</b> It merges by adding stack counts, which with
 * the amount in the data container would silently halve a player's coins. Every pile therefore
 * carries a unique id (see {@link CoinPileTag}), and the merging FR-028 asks for happens
 * <em>before</em> a pile is created instead.
 *
 * <p><b>Despawn is set by pre-ageing.</b> There is no per-entity despawn setter in the Paper API, so
 * a pile is spawned already {@code 6000 - n*20} ticks old to have {@code n} seconds left
 * (research.md R1c).
 */
public final class CoinPile {

    /** Vanilla material, per ADR-005: no resource pack, no custom model data, no client requirement. */
    private static final Material MATERIAL = Material.GOLD_NUGGET;

    private final Plugin plugin;
    private final Server server;
    private final CurrencyConfig config;
    private final Clock clock;
    private final Logger logger;
    private final PilePlatform platform;

    public CoinPile(
            Plugin plugin, Server server, CurrencyConfig config, Clock clock, Logger logger) {
        this(plugin, server, config, clock, logger, PilePlatform.vanilla(plugin));
    }

    /** With a chosen platform seam - only a test has reason to pass anything but the vanilla one. */
    public CoinPile(
            Plugin plugin,
            Server server,
            CurrencyConfig config,
            Clock clock,
            Logger logger,
            PilePlatform platform) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    /**
     * Realises one plan: merges into a neighbouring pile of the same character, or spawns a new one.
     *
     * @return the pile that now holds the amount, or empty when the world is gone
     */
    public Optional<Item> drop(CoinDropPlan plan, PileCap cap) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cap, "cap");

        Location where = locationOf(plan.origin());
        if (where == null) {
            // The world was unloaded between the kill and this call. Rare, and dropping nothing is
            // the only honest answer - there is nowhere to put it.
            return Optional.empty();
        }

        Optional<Item> neighbour = findMergeTarget(where, plan.characterId());
        if (neighbour.isPresent()) {
            return Optional.of(mergeInto(neighbour.get(), plan.amount()));
        }

        if (!cap.makeRoom()) {
            // The cap could not be freed. Nothing is dropped and the caller credits directly - a
            // player must never lose coins to server load (FR-030).
            return Optional.empty();
        }
        return Optional.of(spawn(where, plan));
    }

    /** Whether this entity is one of our piles. */
    public static boolean isPile(Entity entity) {
        return entity instanceof Item item && CoinPileTag.isCoinPile(item.getItemStack());
    }

    /**
     * Finds a pile of the same character within the configured radius (FR-028).
     *
     * <p>Uses the server's chunk-bound nearby lookup, never a walk over every entity in the world -
     * Constitution II rules the latter out explicitly.
     */
    private Optional<Item> findMergeTarget(Location where, UUID characterId) {
        double radius = config.mergeRadius();
        for (Entity entity : where.getWorld().getNearbyEntities(where, radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (CoinPileTag.characterOf(stack).filter(characterId::equals).isPresent()) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Raises the amount on an existing pile.
     *
     * <p>An addition in the container, not a vanilla merge - which is the whole point: the value is
     * ours to add, and stack counts have nothing to do with it.
     */
    private Item mergeInto(Item pile, long amount) {
        ItemStack stack = pile.getItemStack();
        long current = CoinPileTag.amountOf(stack).orElse(0L);
        long merged = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;

        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.writeAmount(meta, merged);
        meta.displayName(displayName(merged));
        stack.setItemMeta(meta);
        pile.setItemStack(stack);
        return pile;
    }

    private Item spawn(Location where, CoinDropPlan plan) {
        ItemStack stack = new ItemStack(MATERIAL, 1);
        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.write(meta, plan.amount(), plan.characterId(), clock.millis());
        meta.displayName(displayName(plan.amount()));
        stack.setItemMeta(meta);

        Item pile = where.getWorld().dropItem(where, stack);

        // Invisible to everyone, then shown to the one person entitled to it (FR-027a). A visible
        // pile that cannot be picked up looks like a bug from the player's side.
        //
        // First, deliberately: this is a requirement, while harden() below is hardening on top of a
        // lock we enforce ourselves anyway.
        platform.hideFromEveryone(pile);
        showTo(pile, plan.holderId());

        platform.harden(pile, plan.holderId(), config.spawnTicksLived());
        return pile;
    }

    /**
     * Everything about a pile that only a real server can actually do.
     *
     * <p><b>Why this is a seam at all.</b> MockBukkit implements neither {@code Item.setOwner} nor
     * {@code Entity.setVisibleByDefault}, and it reports an unimplemented call as a <em>skipped</em>
     * test rather than a failure. Without this seam, six tests about merging, entitlement and the cap
     * would silently report as skipped and the build would still say SUCCESSFUL - which is the worst
     * outcome available: a green build that proved nothing.
     *
     * <p>So the platform-specific calls are named, and a test substitutes a recorder. What that test
     * then proves is <b>what we ask for and about whom</b>; that Paper honours it is proved on a real
     * server (quickstart.md 3.1).
     *
     * <p><b>The two halves are not equal in weight.</b> Visibility is a requirement (FR-027a). The
     * owner flag is <em>hardening</em>: it makes other clients not even try, which is cheap - but it
     * knows players, not characters, so {@link CoinPickupListener} checks the character regardless
     * (ADR-011). Presentation is never the authority (Constitution VI).
     */
    public interface PilePlatform {

        /** Hides the pile from everyone (FR-027a). */
        void hideFromEveryone(Item pile);

        /** Shows it to the one player entitled to it, if they are online. */
        void showTo(Item pile, Player player);

        /** The vanilla-side locks and the pre-ageing that stands in for a despawn setter. */
        void harden(Item pile, UUID ownerId, int spawnTicksLived);

        /** What a real server does. */
        static PilePlatform vanilla(Plugin plugin) {
            Objects.requireNonNull(plugin, "plugin");
            return new PilePlatform() {
                @Override
                public void hideFromEveryone(Item pile) {
                    pile.setVisibleByDefault(false);
                }

                @Override
                public void showTo(Item pile, Player player) {
                    player.showEntity(plugin, pile);
                }

                @Override
                public void harden(Item pile, UUID ownerId, int spawnTicksLived) {
                    pile.setOwner(ownerId);
                    pile.setCanMobPickup(false);
                    pile.setWillAge(true);
                    // Pre-aged, because there is no despawn setter (research.md R1c).
                    pile.setTicksLived(spawnTicksLived);
                }
            };
        }
    }

    /**
     * Shows a pile to its entitled player, if they are online.
     *
     * <p>Offline is an ordinary case: the pile stays invisible and expires. Crediting it would
     * contradict FR-029 - the player had the opportunity and was not there to take it.
     */
    private void showTo(Item pile, UUID holderId) {
        Player player = server.getPlayer(holderId);
        if (player != null) {
            platform.showTo(pile, player);
        }
    }

    private UUID ownerOf(CoinDropPlan plan) {
        return plan.holderId();
    }

    private Component displayName(long amount) {
        // Deliberately plain: the wording a player reads on pickup comes from messages.yml. This is
        // the label floating over the entity, and B13 may well replace it.
        return Component.text(amount + " coins");
    }

    private Location locationOf(WorldPoint point) {
        World world = server.getWorld(point.worldId());
        if (world == null) {
            logger.log(
                    Level.FINE,
                    "[currency] world " + point.worldId() + " is gone - no pile dropped");
            return null;
        }
        return new Location(world, point.x(), point.y(), point.z());
    }

    /** How the caller frees a slot when the pile cap is reached. */
    @FunctionalInterface
    public interface PileCap {
        /** @return whether there is room now */
        boolean makeRoom();
    }
}

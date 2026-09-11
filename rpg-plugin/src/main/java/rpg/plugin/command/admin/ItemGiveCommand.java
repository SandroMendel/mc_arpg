package rpg.plugin.command.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import rpg.core.item.Items;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.platform.drop.OwnedDrops;
import rpg.platform.item.ItemStackFactory;
import rpg.plugin.command.CommandMessageKeys;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/**
 * {@code /rpg item give <player> <template> [amount]} (US3, FR-015).
 *
 * <p>The command deliberately uses the same {@link ItemStackFactory} as loot. That keeps the
 * template tag, rendered lore, effects and future item changes identical on both paths.
 *
 * <p>If the inventory cannot accept the complete stack, the leftover is handed to
 * {@link OwnedDrops}. This is B11's full-inventory rule in the same form as loot: nothing is
 * discarded, banked behind the player's back or silently overwritten.
 */
public final class ItemGiveCommand {

    public static final String PERMISSION = "rpg.admin.item.give";

    /** The Bukkit factory takes an int, so this is the natural upper bound of the argument. */
    private static final long MAX_AMOUNT = Integer.MAX_VALUE;

    private final Server server;
    private final Items items;
    private final ItemStackFactory itemFactory;
    private final OwnedDrops drops;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final AdminAudit audit;
    private final Messages messages;

    public ItemGiveCommand(
            Server server,
            Items items,
            ItemStackFactory itemFactory,
            OwnedDrops drops,
            Function<UUID, Optional<UUID>> characterOf,
            AdminAudit audit,
            Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.items = Objects.requireNonNull(items, "items");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.drops = Objects.requireNonNull(drops, "drops");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The {@code item} branch that is attached below {@code /rpg}. */
    public RpgCommand definition() {
        return RpgCommand.branch(
                "item", ItemGiveMessageKeys.DESCRIPTION, null, List.of(giveDefinition()));
    }

    private RpgCommand giveDefinition() {
        Argument<OfflinePlayer> player =
                Argument.required("player", Arguments.player(server));
        Argument<String> template =
                Argument.required("template", Arguments.itemTemplate(items));
        Argument<Long> amount =
                Argument.optional("amount", Arguments.amount(1, MAX_AMOUNT));

        return RpgCommand.leaf(
                "give",
                ItemGiveMessageKeys.GIVE_DESCRIPTION,
                PERMISSION,
                List.of(player, template, amount),
                context -> give(context, player, template, amount));
    }

    private void give(
            CommandContext context,
            Argument<OfflinePlayer> playerArgument,
            Argument<String> templateArgument,
            Argument<Long> amountArgument) {
        OfflinePlayer requested = context.get(playerArgument);
        Player target = requested.getPlayer();
        Optional<UUID> characterId =
                target == null
                        ? Optional.empty()
                        : Optional.ofNullable(characterOf.apply(target.getUniqueId()))
                                .orElseGet(Optional::empty);
        if (target == null || characterId.isEmpty()) {
            tell(
                    context.sender(),
                    ItemGiveMessageKeys.TARGET_UNAVAILABLE,
                    Map.of("player", displayName(requested)));
            return;
        }

        String templateKey = context.get(templateArgument);
        long requestedAmount = context.find(amountArgument).orElse(1L);
        Optional<ItemStack> created =
                itemFactory.create(templateKey, Math.toIntExact(requestedAmount));
        if (created.isEmpty()) {
            // A reload may remove a template after Brigadier accepted the argument. The generic
            // command error is deliberately the same one used by every configuration key.
            tell(
                    context.sender(),
                    CommandMessageKeys.UNKNOWN_KEY,
                    Map.of("key", templateKey));
            return;
        }

        var where = target.getLocation();
        if (where.getWorld() == null) {
            // OwnedDrops can only preserve the item in a real world. Check before touching the
            // inventory so a malformed target cannot turn a successful grant into data loss.
            tell(
                    context.sender(),
                    ItemGiveMessageKeys.TARGET_UNAVAILABLE,
                    Map.of("player", displayName(requested)));
            return;
        }

        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(created.get());
        boolean dropped = false;
        for (ItemStack leftover : leftovers.values()) {
            if (drops.drop(leftover, where, characterId.get(), target).isEmpty()) {
                // The world was checked above; this is a defensive guard for a world transition in
                // the same tick. No audit entry is written for an unconfirmed grant.
                tell(
                        context.sender(),
                        ItemGiveMessageKeys.TARGET_UNAVAILABLE,
                        Map.of("player", displayName(requested)));
                return;
            }
            dropped = true;
        }

        audit.record(
                context.sender(),
                "item_granted",
                target.getUniqueId(),
                Map.of(
                        "template", templateKey,
                        "amount", requestedAmount,
                        "delivery", dropped ? "owned-drop" : "inventory"));

        MessageKey result = dropped ? ItemGiveMessageKeys.DROPPED : ItemGiveMessageKeys.GIVEN;
        tell(
                context.sender(),
                result,
                Map.of(
                        "player", target.getName(),
                        "template", templateKey,
                        "amount", String.valueOf(requestedAmount)));
    }

    private void tell(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        sender.sendMessage(messages.get(key, placeholders));
    }

    private static String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? player.getUniqueId().toString() : name;
    }
}

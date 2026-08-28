package rpg.plugin.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.currency.CurrencyAdmin;
import rpg.core.currency.CurrencyMessageKeys;
import rpg.core.message.Messages;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.platform.currency.CurrencyMenu;
import rpg.platform.currency.CurrencyMenuListener;

/**
 * {@code /coins} - open the window, or change a balance.
 *
 * <p><b>Provisional, and meant to be replaced</b> (ADR-028). Commands, the permission tree and tab
 * completion belong to B14; this exists because an interface with no way to call it is present and
 * unusable, and B14 is several blocks away.
 *
 * <p><b>The measure of this class is how little it contains.</b> It parses arguments, checks a
 * permission and calls. Every rule - never negative, always a reason, always an actor, online versus
 * offline - lives in {@code rpg-core} and {@code rpg-persistence} and is tested without a server.
 * Anything that grows a rule here is in the wrong module.
 *
 * <p><b>Read is clicked, write is typed.</b> An amount cannot sensibly be typed into an inventory,
 * and clicking one together out of buttons would be a number pad dressed as a UI. Reading, on the
 * other hand, is exactly what a window is for - a ledger of hundreds of rows is unreadable in chat.
 */
public final class CoinsCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION_BALANCE = "rpg.currency.balance";
    public static final String PERMISSION_ADMIN = "rpg.currency.admin";

    private final Server server;
    private final SessionRegistry sessions;
    private final Currency currency;
    private final CurrencyAdmin admin;
    private final CurrencyMenuListener menu;
    private final Messages messages;
    private final OfflineBalances offlineBalances;

    /** How the balance of a character who is not online is found, for the window. */
    @FunctionalInterface
    public interface OfflineBalances {
        long balanceOf(UUID characterId);
    }

    public CoinsCommand(
            Server server,
            SessionRegistry sessions,
            Currency currency,
            CurrencyAdmin admin,
            CurrencyMenuListener menu,
            Messages messages,
            OfflineBalances offlineBalances) {
        this.server = Objects.requireNonNull(server, "server");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.admin = Objects.requireNonNull(admin, "admin");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.offlineBalances = Objects.requireNonNull(offlineBalances, "offlineBalances");
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return openOwn(sender);
        }
        String first = args[0].toLowerCase(java.util.Locale.ROOT);
        return switch (first) {
            case "set", "add", "remove" -> intervene(sender, first, args);
            default -> openFor(sender, args[0]);
        };
    }

    /** {@code /coins} - the player's own window. No special right needed. */
    private boolean openOwn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player has characters of their own.");
            return true;
        }
        if (!player.hasPermission(PERMISSION_BALANCE)) {
            player.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_DENIED));
            return true;
        }
        openWindow(player, player.getUniqueId());
        return true;
    }

    /** {@code /coins <player>} - somebody else's window. Requires the admin right. */
    private boolean openFor(CommandSender sender, String playerName) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("The window can only be opened by a player.");
            return true;
        }
        if (!viewer.hasPermission(PERMISSION_ADMIN)) {
            viewer.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_DENIED));
            return true;
        }
        Player target = server.getPlayerExact(playerName);
        if (target == null) {
            viewer.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_UNKNOWN_CHARACTER));
            return true;
        }
        openWindow(viewer, target.getUniqueId());
        return true;
    }

    private void openWindow(Player viewer, UUID targetPlayerId) {
        Optional<PlayerSession> session = sessions.find(targetPlayerId);
        if (session.isEmpty()) {
            viewer.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_UNKNOWN_CHARACTER));
            return;
        }
        List<PlayerCharacter> characters = session.get().availableCharacters();
        Map<UUID, Long> balances = new HashMap<>();
        for (PlayerCharacter character : characters) {
            // Never a sum: three characters are three balances (FR-046b).
            balances.put(
                    character.characterId(),
                    currency.balanceOf(character.characterId())
                            .orElseGet(() -> offlineBalances.balanceOf(character.characterId())));
        }
        menu.openSelection(viewer, targetPlayerId, characters, balances);
    }

    /** {@code /coins set|add|remove <player> <class> <amount>}. */
    private boolean intervene(CommandSender sender, String verb, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_DENIED));
            return true;
        }
        if (args.length != 4) {
            // The character has to be named: a player has up to three, and an intervention without
            // it would be ambiguous - the operator would notice only when the wrong one is richer.
            sender.sendMessage("Usage: /coins " + verb + " <player> <class> <amount>");
            return true;
        }

        Optional<UUID> characterId = characterOf(args[1], args[2]);
        if (characterId.isEmpty()) {
            sender.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_UNKNOWN_CHARACTER));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException notANumber) {
            sender.sendMessage(messages.get(CurrencyMessageKeys.INVALID_AMOUNT));
            return true;
        }

        BookingResult result =
                switch (verb) {
                    case "set" -> admin.set(characterId.get(), amount, sender.getName());
                    case "add" -> admin.add(characterId.get(), amount, sender.getName());
                    default -> admin.remove(characterId.get(), amount, sender.getName());
                };

        if (!result.isSuccess()) {
            sender.sendMessage(messages.get(result.messageKey()));
            return true;
        }
        sender.sendMessage(
                messages.get(
                        CurrencyMessageKeys.ADMIN_APPLIED,
                        Map.of(
                                "character", args[2],
                                "amount",
                                String.valueOf(
                                        currency.balanceOf(characterId.get())
                                                .orElseGet(
                                                        () ->
                                                                offlineBalances.balanceOf(
                                                                        characterId.get()))))));
        return true;
    }

    /**
     * Resolves player plus class to a character.
     *
     * <p>Only for an online player: an offline one would need a lookup this provisional command has
     * no business owning. B14 will do better; until then the operator asks while the player is on.
     */
    private Optional<UUID> characterOf(String playerName, String className) {
        Player target = server.getPlayerExact(playerName);
        if (target == null) {
            return Optional.empty();
        }
        return sessions.find(target.getUniqueId()).stream()
                .flatMap(session -> session.availableCharacters().stream())
                .filter(
                        character ->
                                character.characterClass()
                                        .name()
                                        .equalsIgnoreCase(className))
                .map(PlayerCharacter::characterId)
                .findFirst();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        // The bare minimum. Real completion is B14's, together with the rest of this class.
        if (args.length == 1) {
            return List.of("set", "add", "remove");
        }
        return List.of();
    }

    /** The window's page size, so the plugin can build the menu with it. */
    public static CurrencyMenu menuFor(Messages messages, int pageSize) {
        return new CurrencyMenu(messages, pageSize);
    }
}

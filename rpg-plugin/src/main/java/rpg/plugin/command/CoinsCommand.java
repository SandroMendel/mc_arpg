package rpg.plugin.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.currency.CurrencyAdmin;
import rpg.core.currency.CurrencyMessageKeys;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.platform.ui.CurrencyMenu;
import rpg.platform.ui.CurrencyMenuListener;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.RpgCommand;

/**
 * {@code /coins} - open the window, or change a balance.
 *
 * <p><b>Provisional, and meant to be replaced</b> (ADR-028). Commands, the permission tree and tab
 * completion belong to B14; this exists because an interface with no way to call it is present and
 * unusable, and B14 is several blocks away.
 *
 * <p><b>Das FENSTER ist seit B13 umgezogen, dieses Kommando nicht</b> (FR-061a). {@code CurrencyMenu}
 * und sein Listener liegen jetzt in {@code rpg.platform.ui} — ADR-028 nennt die <em>Anzeige</em>
 * befristet und weist <em>Kommandos</em> ausdrücklich B14 zu. B13 sammelt keine Kommandos ein; es
 * legt nur das eine an, das sein eigenes Fenster braucht ({@code /char}).
 *
 * <p>Eine Eingabegeste ist Präsentation, ein Kommando mit Rechtebaum und Tab-Completion ist es
 * nicht — deshalb ist B09s Rechtsklick am Kristall mitgewandert und dieses Kommando nicht.
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
public final class CoinsCommand {

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

    /**
     * Der Knoten für den Kommandobaum (T037).
     *
     * <p><b>Drei Formen desselben Wortes, alle unverändert</b> (FR-005):
     *
     * <ul>
     *   <li>{@code /coins} — das eigene Fenster, Recht {@code rpg.currency.balance}
     *   <li>{@code /coins <spieler>} — ein fremdes Fenster, zusätzlich {@code rpg.currency.admin}
     *   <li>{@code /coins set|add|remove <spieler> <klasse> <betrag>} — der Eingriff
     * </ul>
     *
     * <p>Deshalb {@link RpgCommand#branchWithOwnForms} und nicht {@code branch}: der Knoten hat
     * Unterkommandos <em>und</em> eigene Formen. Ein Spieler namens {@code set} ist damit über
     * {@code /coins <spieler>} nicht erreichbar — dieselbe Lücke hatte die
     * {@code switch (args[0])}-Fassung, sie ist jetzt nur aufgeschrieben.
     *
     * <p><b>{@code getPlayerExact} ist weg.</b> Es fand nur <em>online</em> Spieler, also lehnte
     * {@code /coins <spieler>} jeden Abgemeldeten ab — mit derselben Meldung wie einen Tippfehler,
     * sodass der Betreiber die zwei Fälle nicht unterscheiden konnte. Der {@code PLAYER}-Typ löst
     * beides auf und nennt einen echten Tippfehler beim Namen (T017).
     */
    public RpgCommand definition(Server bukkit) {
        Argument<org.bukkit.OfflinePlayer> whose =
                Argument.optional("player", Arguments.player(bukkit));

        return RpgCommand.branchWithOwnForms(
                "coins",
                MessageKey.of("command.coins.description"),
                PERMISSION_BALANCE,
                List.of(
                        interventionBranch(bukkit, "set"),
                        interventionBranch(bukkit, "add"),
                        interventionBranch(bukkit, "remove")),
                List.of(whose),
                true,
                context -> {
                    Player viewer = context.player().orElseThrow();
                    context.find(whose)
                            .ifPresentOrElse(
                                    target -> openForeign(viewer, target),
                                    () -> openWindow(viewer, viewer.getUniqueId()));
                });
    }

    /**
     * Ein Eingriffszweig — {@code /coins set|add|remove <spieler> <klasse> <betrag>}.
     *
     * <p>Läuft <b>auch von der Konsole</b>: ein Kontostand richtigzustellen braucht keinen Körper
     * in der Welt, und ein Betreiber sitzt oft genau dort (FR-008).
     */
    private RpgCommand interventionBranch(Server bukkit, String verb) {
        Argument<org.bukkit.OfflinePlayer> who =
                Argument.required("player", Arguments.player(bukkit));
        Argument<rpg.core.session.CharacterClass> which =
                Argument.required("class", Arguments.characterClass());
        Argument<Long> howMuch = Argument.required("amount", Arguments.atLeast("amount", 0));

        return RpgCommand.leaf(
                verb,
                MessageKey.of("command.coins." + verb + ".description"),
                PERMISSION_ADMIN,
                List.of(who, which, howMuch),
                context ->
                        intervene(
                                context.sender(),
                                verb,
                                context.get(who),
                                context.get(which),
                                context.get(howMuch)));
    }

    /** Ein fremdes Fenster — verlangt zusätzlich das Admin-Recht. */
    private void openForeign(Player viewer, org.bukkit.OfflinePlayer target) {
        if (!viewer.hasPermission(PERMISSION_ADMIN)) {
            viewer.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_DENIED));
            return;
        }
        openWindow(viewer, target.getUniqueId());
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

    /**
     * {@code /coins set|add|remove <player> <class> <amount>}.
     *
     * <p><b>Was hier alles verschwunden ist</b> (T037): die Rechteprüfung (macht das Gerüst,
     * FR-003), die Prüfung auf {@code args.length != 4} samt Nutzungszeile (das Gerüst nennt das
     * fehlende Argument, FR-004), und das Zerlegen des Betrags mit {@code Long.parseLong} im
     * {@code try} (macht der {@code AMOUNT}-Typ). Was bleibt, ist der Vorgang.
     */
    private void intervene(
            CommandSender sender,
            String verb,
            org.bukkit.OfflinePlayer target,
            rpg.core.session.CharacterClass characterClass,
            long amount) {

        Optional<UUID> characterId = characterOf(target.getUniqueId(), characterClass);
        if (characterId.isEmpty()) {
            sender.sendMessage(messages.get(CurrencyMessageKeys.ADMIN_UNKNOWN_CHARACTER));
            return;
        }

        BookingResult result =
                switch (verb) {
                    case "set" -> admin.set(characterId.get(), amount, sender.getName());
                    case "add" -> admin.add(characterId.get(), amount, sender.getName());
                    default -> admin.remove(characterId.get(), amount, sender.getName());
                };

        if (!result.isSuccess()) {
            sender.sendMessage(messages.get(result.messageKey()));
            return;
        }
        sender.sendMessage(
                messages.get(
                        CurrencyMessageKeys.ADMIN_APPLIED,
                        Map.of(
                                "character", rpg.plugin.command.framework.Arguments.label(characterClass),
                                "amount",
                                String.valueOf(
                                        currency.balanceOf(characterId.get())
                                                .orElseGet(
                                                        () ->
                                                                offlineBalances.balanceOf(
                                                                        characterId.get()))))));
    }

    /**
     * Löst Spieler plus Klasse zu einem Charakter auf.
     *
     * <p><b>Die Sitzung muss geladen sein</b> — daran hat sich nichts geändert: die Charaktere
     * eines Spielers stehen in {@code SessionRegistry}, und die kennt nur Angemeldete. Der
     * {@code PLAYER}-Typ nimmt inzwischen auch Abgemeldete an; für die gibt es dann keinen
     * Charakter, und die Meldung sagt genau das ({@code ADMIN_UNKNOWN_CHARACTER}) statt „diesen
     * Spieler gibt es nicht". <b>Zwei verschiedene Lagen, zwei verschiedene Meldungen</b> — vorher
     * war beides dieselbe, und der Betreiber konnte einen Tippfehler nicht von einem
     * abgemeldeten Spieler unterscheiden.
     */
    private Optional<UUID> characterOf(UUID playerId, rpg.core.session.CharacterClass wanted) {
        return sessions.find(playerId).stream()
                .flatMap(session -> session.availableCharacters().stream())
                .filter(character -> character.characterClass() == wanted)
                .map(PlayerCharacter::characterId)
                .findFirst();
    }

    /**
     * The window's page size, so the plugin can build the menu with it.
     *
     * <p>Seit B13 braucht das Fenster zusätzlich den gemeinsamen Rahmen (T122) — geteilt wird die
     * Titelbehandlung, nicht das Aussehen: {@code buildPlain} gibt ihm keinen Rand, den es vorher
     * nicht hatte (FR-061).
     */
    public static CurrencyMenu menuFor(
            Messages messages, rpg.platform.ui.MenuFrame frame, int pageSize) {
        return new CurrencyMenu(messages, frame, pageSize);
    }
}

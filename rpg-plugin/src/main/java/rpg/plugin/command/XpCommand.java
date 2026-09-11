package rpg.plugin.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.progression.ProgressView;
import rpg.core.progression.Progression;
import rpg.core.progression.ProgressionMessageKeys;
import rpg.core.progression.XpCurve;
import rpg.core.progression.XpResult;
import rpg.core.progression.XpSource;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.RpgCommand;

/**
 * {@code /xp} - give experience, take it away, or set a level outright.
 *
 * <p><b>Provisional, like {@code /coins} before it</b> (ADR-028). Commands, the permission tree and
 * tab completion belong to B14; this exists because a correction that can only be made with a
 * database editor is not a correction anybody will make.
 *
 * <p><b>Giving and taking go different ways, and that is not an inconsistency.</b> Giving is an
 * ordinary grant with {@code XpSource.ADMIN} - the same path a kill takes. Taking cannot be: B06
 * refuses a negative amount outright (FR-015), so that no player can quietly lose what they earned.
 * The way down is {@code setProgress}, which exists for exactly this and writes <b>who did it</b>
 * into B02's audit log with the old and the new state (FR-024b).
 *
 * <p><b>The character is told.</b> Somebody who quietly loses two levels files a bug report, and the
 * report costs more than the line does.
 */
public final class XpCommand {

    public static final String PERMISSION = "rpg.progression.admin";

    private final Server server;
    private final SessionRegistry sessions;
    private final Progression progression;
    private final XpCurve curve;
    private final Messages messages;

    public XpCommand(
            Server server,
            SessionRegistry sessions,
            Progression progression,
            XpCurve curve,
            Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.curve = Objects.requireNonNull(curve, "curve");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Der Knoten für den Kommandobaum (T038).
     *
     * <p><b>{@code /xp give|take <spieler> <betrag>} und {@code /xp set <spieler> <stufe> [xp]}</b>
     * — dieselbe Syntax wie vorher (FR-005), jetzt als drei Zweige statt eines
     * {@code switch (args[0])}.
     *
     * <p><b>Was der Baum dieser Klasse abgenommen hat:</b>
     *
     * <ul>
     *   <li>Die Rechteprüfung (FR-003) — sie stand hier <em>und</em> in {@code onTabComplete}.
     *   <li>{@code args.length < 3} samt Nutzungszeile: das Gerüst nennt jetzt das fehlende
     *       Argument (FR-004). Die Nutzungszeile war die schlechtere Antwort — wer sie schon vor
     *       sich hatte und trotzdem falsch tippte, liest sie kein zweites Mal richtig.
     *   <li>Zwei {@code Long.parseLong} in {@code try}-Blöcken.
     *   <li><b>Die Regel „give und take nehmen nichts unter 1".</b> Sie stand als Sonderfall neben
     *       dem Zerlegen ({@code amount <= 0 && !"set".equals(…)}) — leicht zu übersehen, wenn ein
     *       vierter Zweig dazukäme. Jetzt ist sie der <em>Wertebereich des Arguments</em>:
     *       {@code give} und {@code take} bekommen {@code amount(1, …)}. Eine Regel, die als
     *       Bereich dasteht, kann kein Zweig vergessen.
     *   <li><b>Die Höchststufe wird gefragt statt geraten.</b> {@code set} nahm bisher jede Zahl
     *       entgegen und ließ B06 sie ablehnen; jetzt kennt das Argument den Bereich und nennt ihn.
     * </ul>
     *
     * <p><b>Die Null-UUID ist umgezogen</b> nach {@link AdminAudit#CONSOLE} — sie gehört dorthin,
     * wo ins Audit-Log geschrieben wird, und nicht zu dem einen Kommando, das sie zuerst brauchte.
     */
    public RpgCommand definition(Server bukkit) {
        return RpgCommand.branch(
                "xp",
                MessageKey.of("command.xp.description"),
                PERMISSION,
                List.of(
                        grantBranch(bukkit, "give"),
                        grantBranch(bukkit, "take"),
                        setBranch(bukkit)));
    }

    /** {@code /xp give|take <spieler> <betrag>} — der Betrag ist mindestens 1. */
    private RpgCommand grantBranch(Server bukkit, String verb) {
        Argument<org.bukkit.OfflinePlayer> who =
                Argument.required("player", Arguments.player(bukkit));
        Argument<Long> howMuch = Argument.required("amount", Arguments.atLeast("amount", 1));

        return RpgCommand.leaf(
                verb,
                MessageKey.of("command.xp." + verb + ".description"),
                PERMISSION,
                List.of(who, howMuch),
                context ->
                        withTarget(
                                context.sender(),
                                context.get(who),
                                target -> {
                                    if ("give".equals(verb)) {
                                        give(context.sender(), target, context.get(howMuch));
                                    } else {
                                        take(context.sender(), target, context.get(howMuch));
                                    }
                                }));
    }

    /** {@code /xp set <spieler> <stufe> [xp]} — die grobe Richtigstellung. */
    private RpgCommand setBranch(Server bukkit) {
        Argument<org.bukkit.OfflinePlayer> who =
                Argument.required("player", Arguments.player(bukkit));
        Argument<Integer> level = Argument.required("level", Arguments.level(progression::maxLevel));
        Argument<Long> inLevel = Argument.optional("xp", Arguments.atLeast("xp", 0));

        return RpgCommand.leaf(
                "set",
                MessageKey.of("command.xp.set.description"),
                PERMISSION,
                List.of(who, level, inLevel),
                context ->
                        withTarget(
                                context.sender(),
                                context.get(who),
                                target ->
                                        set(
                                                context.sender(),
                                                target,
                                                context.get(level),
                                                context.find(inLevel).orElse(0L))));
    }

    /**
     * Löst den Spieler zu seinem aktiven Charakter auf, oder sagt, dass es ihn nicht gibt.
     *
     * <p>An <b>einer</b> Stelle für alle drei Zweige. Vorher stand die Auflösung einmal vor dem
     * {@code switch} — was gleichwertig war, solange es ein {@code switch} gab.
     */
    private void withTarget(
            CommandSender sender,
            org.bukkit.OfflinePlayer player,
            java.util.function.Consumer<Target> then) {
        Optional<Target> target = resolve(player);
        if (target.isEmpty()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_UNKNOWN_TARGET));
            return;
        }
        then.accept(target.get());
    }

    private void give(CommandSender sender, Target target, long amount) {
        XpResult result = progression.grant(target.characterId(), amount, XpSource.ADMIN);
        if (result.rejected()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return;
        }
        report(sender, target, "+" + amount);
    }

    /**
     * Takes experience away, walking down through levels if it has to.
     *
     * <p>The walk is here rather than in B06 on purpose: it is arithmetic over the curve, which is
     * public, and B06's contract deliberately has no "subtract" - adding one would be a second way to
     * lower progress next to {@code setProgress}, and only one of them would be audited.
     */
    private void take(CommandSender sender, Target target, long amount) {
        Optional<ProgressView> current = progression.progressOf(target.characterId());
        if (current.isEmpty()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_UNKNOWN_TARGET));
            return;
        }
        int level = current.get().level();
        long inLevel = current.get().xpInLevel();
        long remaining = amount;

        while (remaining > inLevel && level > 1) {
            remaining -= inLevel;
            level--;
            // Dropping into a level puts them at its top: the size of level L is what it costs to
            // leave it, which is the threshold of L+1.
            inLevel = curve.thresholdFor(level + 1);
        }
        long left = Math.max(0L, inLevel - remaining);

        XpResult result =
                progression.setProgress(
                        AdminAudit.actorOf(sender), target.characterId(), level, left);
        if (result.rejected()) {
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return;
        }
        report(sender, target, "-" + amount);
    }

    /** {@code /xp set <player> <level> [xp-in-level]} - the blunt correction. */
    private void set(CommandSender sender, Target target, int level, long inLevel) {
        XpResult result =
                progression.setProgress(
                        AdminAudit.actorOf(sender), target.characterId(), level, inLevel);
        if (result.rejected()) {
            // Out of range, unknown character, not ready - B06 decided, and it says which.
            sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_INVALID_AMOUNT));
            return;
        }
        report(sender, target, "level " + level);
    }

    /** Tells the operator what the character now stands at, and tells the character it happened. */
    private void report(CommandSender sender, Target target, String change) {
        ProgressView now =
                progression.progressOf(target.characterId()).orElse(new ProgressView(1, 0L, 0L, false));
        Map<String, String> values =
                Map.of(
                        "player", target.player().getName(),
                        "change", change,
                        "level", String.valueOf(now.level()),
                        "xp", String.valueOf(now.xpInLevel()));
        sender.sendMessage(messages.get(ProgressionMessageKeys.ADMIN_XP_CHANGED, values));
        target.player().sendMessage(messages.get(ProgressionMessageKeys.ADMIN_XP_NOTICE, values));
    }

    /**
     * The player and the character they are currently playing.
     *
     * <p>Only an online player, like {@code /coins}: resolving an offline character needs a lookup
     * this provisional command has no business owning.
     */
    private Optional<Target> resolve(org.bukkit.OfflinePlayer who) {
        Player player = who.getPlayer();
        if (player == null) {
            // Der PLAYER-Typ nimmt auch Abgemeldete an; hier braucht es aber die geladene
            // Sitzung, und die gibt es nur fuer Angemeldete. Zwei Lagen, zwei Meldungen: ein
            // Tippfehler faellt schon im Argument auf, ein abgemeldeter Spieler erst hier.
            return Optional.empty();
        }
        return sessions
                .find(player.getUniqueId())
                .flatMap(PlayerSession::activeCharacter)
                .map(PlayerCharacter::characterId)
                .map(characterId -> new Target(player, characterId));
    }

    private record Target(Player player, UUID characterId) {}

}

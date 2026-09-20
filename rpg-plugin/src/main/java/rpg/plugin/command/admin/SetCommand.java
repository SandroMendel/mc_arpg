package rpg.plugin.command.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import rpg.core.classes.ClassSelection;
import rpg.core.classes.ClassSelectionRejection;
import rpg.core.classes.ClassSelectionResult;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.progression.ProgressView;
import rpg.core.progression.Progression;
import rpg.core.progression.XpResult;
import rpg.core.scheduler.Scheduler;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;
import rpg.plugin.command.framework.TickReturn;

/** {@code /rpg set level|xp|class <player> <value>} (US6). */
public final class SetCommand {

    /** Existing B06 permission for changes to level and experience. */
    public static final String PROGRESSION_PERMISSION = "rpg.progression.admin";

    /** B14 permission for the class-selection correction path. */
    public static final String CLASS_PERMISSION = "rpg.admin.set.class";

    private final Server server;
    private final SessionRegistry sessions;
    private final Progression progression;
    private final ClassSelection selection;
    private final TickReturn tickReturn;
    private final AdminAudit audit;
    private final Messages messages;

    public SetCommand(
            Server server,
            SessionRegistry sessions,
            Progression progression,
            ClassSelection selection,
            Scheduler scheduler,
            AdminAudit audit,
            Messages messages,
            Logger logger) {
        this(
                server,
                sessions,
                progression,
                selection,
                new TickReturn(scheduler, server, logger),
                audit,
                messages);
    }

    SetCommand(
            Server server,
            SessionRegistry sessions,
            Progression progression,
            ClassSelection selection,
            TickReturn tickReturn,
            AdminAudit audit,
            Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.tickReturn = Objects.requireNonNull(tickReturn, "tickReturn");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The {@code set} branch below {@code /rpg}. */
    public RpgCommand definition() {
        return RpgCommand.branch(
                "set",
                SetMessageKeys.DESCRIPTION,
                null,
                List.of(levelDefinition(), xpDefinition(), classDefinition()));
    }

    private RpgCommand levelDefinition() {
        Argument<OfflinePlayer> player = playerArgument();
        Argument<Integer> level = Argument.required("level", Arguments.level(progression::maxLevel));
        return RpgCommand.leaf(
                "level",
                SetMessageKeys.LEVEL_DESCRIPTION,
                PROGRESSION_PERMISSION,
                List.of(player, level),
                context -> setLevel(context, player, level));
    }

    private RpgCommand xpDefinition() {
        Argument<OfflinePlayer> player = playerArgument();
        Argument<Long> xp = Argument.required("xp", Arguments.atLeast("xp", 0L));
        return RpgCommand.leaf(
                "xp",
                SetMessageKeys.XP_DESCRIPTION,
                PROGRESSION_PERMISSION,
                List.of(player, xp),
                context -> setXp(context, player, xp));
    }

    private RpgCommand classDefinition() {
        Argument<OfflinePlayer> player = playerArgument();
        Argument<CharacterClass> characterClass =
                Argument.required("class", Arguments.characterClass());
        return RpgCommand.leaf(
                "class",
                SetMessageKeys.CLASS_DESCRIPTION,
                CLASS_PERMISSION,
                List.of(player, characterClass),
                context -> setClass(context, player, characterClass));
    }

    private Argument<OfflinePlayer> playerArgument() {
        return Argument.required("player", Arguments.player(server));
    }

    private void setLevel(
            CommandContext context,
            Argument<OfflinePlayer> playerArgument,
            Argument<Integer> levelArgument) {
        OfflinePlayer requested = context.get(playerArgument);
        Optional<ProgressTarget> target = progressTarget(requested);
        if (target.isEmpty()) {
            tell(context.sender(), SetMessageKeys.TARGET_UNAVAILABLE, Map.of());
            return;
        }

        ProgressView before = target.get().progress();
        int level = context.get(levelArgument);
        XpResult result =
                progression.setProgress(
                        AdminAudit.actorOf(context.sender()),
                        target.get().characterId(),
                        level,
                        before.xpInLevel());
        if (result.rejected()) {
            tell(context.sender(), SetMessageKeys.REJECTED, Map.of());
            return;
        }
        reportProgress(context.sender(), target.get(), before, "level", String.valueOf(level));
    }

    private void setXp(
            CommandContext context,
            Argument<OfflinePlayer> playerArgument,
            Argument<Long> xpArgument) {
        OfflinePlayer requested = context.get(playerArgument);
        Optional<ProgressTarget> target = progressTarget(requested);
        if (target.isEmpty()) {
            tell(context.sender(), SetMessageKeys.TARGET_UNAVAILABLE, Map.of());
            return;
        }

        ProgressView before = target.get().progress();
        long xp = context.get(xpArgument);
        XpResult result =
                progression.setProgress(
                        AdminAudit.actorOf(context.sender()),
                        target.get().characterId(),
                        before.level(),
                        xp);
        if (result.rejected()) {
            tell(context.sender(), SetMessageKeys.REJECTED, Map.of());
            return;
        }
        reportProgress(context.sender(), target.get(), before, "xp", String.valueOf(xp));
    }

    private void reportProgress(
            CommandSender sender,
            ProgressTarget target,
            ProgressView before,
            String property,
            String newValue) {
        ProgressView after = progression.progressOf(target.characterId()).orElse(before);
        Map<String, String> values =
                Map.of(
                        "player", displayName(target.player()),
                        "property", property,
                        "from", progressValue(before, property),
                        "to", newValue);
        tell(sender, SetMessageKeys.DONE, values);
        if (target.player() != null) {
            target.player()
                    .sendMessage(
                            messages.get(
                                    SetMessageKeys.NOTICE,
                                    Map.of(
                                            "property", property,
                                            "from", progressValue(before, property),
                                            "to", progressValue(after, property))));
        }
    }

    private void setClass(
            CommandContext context,
            Argument<OfflinePlayer> playerArgument,
            Argument<CharacterClass> classArgument) {
        OfflinePlayer requested = context.get(playerArgument);
        Optional<PlayerSession> session = sessions.find(requested.getUniqueId());
        if (session.isEmpty()) {
            tell(context.sender(), SetMessageKeys.TARGET_UNAVAILABLE, Map.of());
            return;
        }

        CharacterClass wanted = context.get(classArgument);
        CompletableFuture<ClassOutcome> pending =
                selection
                        .choose(session.get(), wanted)
                        .handle(ClassOutcome::new);
        tickReturn.deliver(
                context.sender(),
                pending,
                outcome -> finishClass(context.sender(), requested, wanted, outcome));
    }

    private void finishClass(
            CommandSender sender,
            OfflinePlayer requested,
            CharacterClass wanted,
            ClassOutcome outcome) {
        if (outcome.failure() != null) {
            tell(sender, SetMessageKeys.CLASS_FAILED, Map.of());
            return;
        }
        ClassSelectionResult result = outcome.result();
        if (!result.accepted()) {
            MessageKey reason =
                    result.rejection()
                            .map(ClassSelectionRejection::messageKey)
                            .orElse(SetMessageKeys.CLASS_FAILED);
            tell(sender, reason, Map.of());
            return;
        }

        PlayerCharacter character = result.character().orElseThrow();
        String classLabel = Arguments.label(wanted);
        audit.record(
                sender,
                "class_changed",
                requested.getUniqueId(),
                Map.of(
                        "characterId", character.characterId().toString(),
                        "toClass", classLabel,
                        "mode", result.created() ? "created" : "resumed"));
        Map<String, String> values =
                Map.of("player", displayName(requested), "class", classLabel);
        tell(sender, SetMessageKeys.CLASS_DONE, values);
        Player target = requested.getPlayer();
        if (target != null && target != sender) {
            target.sendMessage(messages.get(SetMessageKeys.CLASS_NOTICE, Map.of("class", classLabel)));
        }
    }

    private Optional<ProgressTarget> progressTarget(OfflinePlayer requested) {
        Optional<PlayerSession> session = sessions.find(requested.getUniqueId());
        if (session.isEmpty()) {
            return Optional.empty();
        }
        return session.get()
                .activeCharacter()
                .map(PlayerCharacter::characterId)
                .flatMap(
                        characterId ->
                                progression
                                        .progressOf(characterId)
                                        .map(
                                                progress ->
                                                        new ProgressTarget(
                                                                requested.getPlayer(),
                                                                characterId,
                                                                progress)));
    }

    private static String progressValue(ProgressView view, String property) {
        return "level".equals(property)
                ? String.valueOf(view.level())
                : String.valueOf(view.xpInLevel());
    }

    private void tell(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        sender.sendMessage(messages.get(key, placeholders));
    }

    private static String displayName(OfflinePlayer player) {
        if (player == null || player.getName() == null) {
            return player == null ? "unknown" : player.getUniqueId().toString();
        }
        return player.getName();
    }

    private record ProgressTarget(Player player, UUID characterId, ProgressView progress) {}

    private record ClassOutcome(ClassSelectionResult result, Throwable failure) {}
}

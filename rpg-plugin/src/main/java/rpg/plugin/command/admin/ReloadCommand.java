package rpg.plugin.command.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;

import rpg.core.config.ConfigValidationException;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/** {@code /rpg reload}: atomically reloads the registered configuration sources (US4). */
public final class ReloadCommand {

    public static final String PERMISSION = "rpg.admin.reload";

    private final Supplier<ReloadResult> reload;
    private final AdminAudit audit;
    private final Messages messages;

    public ReloadCommand(Supplier<ReloadResult> reload, AdminAudit audit, Messages messages) {
        this.reload = Objects.requireNonNull(reload, "reload");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The leaf attached below the single {@code /rpg} root. */
    public RpgCommand definition() {
        return RpgCommand.leaf(
                "reload", ReloadMessageKeys.DESCRIPTION, PERMISSION, List.of(), this::reload);
    }

    private void reload(CommandContext context) {
        ReloadResult result = reload.get();
        if (result.applied()) {
            audit.record(
                    context.sender(),
                    "config_reloaded",
                    Map.of("scope", "global"));
            tell(context.sender(), ReloadMessageKeys.DONE, Map.of());
            return;
        }

        ConfigValidationException failure = result.rejection();
        tell(
                context.sender(),
                ReloadMessageKeys.REJECTED,
                Map.of(
                        "file", failure.sourceFile().toString(),
                        "path", failure.documentPath(),
                        "expected", failure.expected(),
                        "actual", failure.actual()));
    }

    private void tell(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        sender.sendMessage(messages.get(key, placeholders));
    }
}

package rpg.plugin.command.admin;

import java.util.List;

import rpg.core.message.MessageKey;

/** Message keys for the append-only audit-log reader (US8). */
public final class AuditMessageKeys {

    public static final MessageKey DESCRIPTION = MessageKey.of("command.audit.description");
    public static final MessageKey PAGE = MessageKey.of("command.audit.page");
    public static final MessageKey ENTRY = MessageKey.of("command.audit.entry");
    public static final MessageKey EMPTY = MessageKey.of("command.audit.empty");
    public static final MessageKey MORE = MessageKey.of("command.audit.more");
    public static final MessageKey FAILED = MessageKey.of("command.audit.failed");

    private AuditMessageKeys() {}

    public static List<MessageKey> all() {
        return List.of(DESCRIPTION, PAGE, ENTRY, EMPTY, MORE, FAILED);
    }
}

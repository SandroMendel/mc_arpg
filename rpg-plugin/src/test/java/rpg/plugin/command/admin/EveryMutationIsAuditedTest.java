package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T104 / FR-028–FR-029 — mutation commands depend on AdminAudit; read commands do not. */
class EveryMutationIsAuditedTest {

    private static final Path COMMANDS =
            Path.of("src", "main", "java", "rpg", "plugin", "command", "admin");

    @Test
    @DisplayName("every mutation group has an audit seam and readers have none")
    void everyMutationUsesTheSingleAuditSeam() throws IOException {
        Map<String, String> mutations =
                Map.of(
                        "ItemGiveCommand.java", "item_granted",
                        "MobSpawnCommand.java", "mob_spawned",
                        "ReloadCommand.java", "config_reloaded",
                        "SetCommand.java", "class_changed");

        for (Map.Entry<String, String> mutation : mutations.entrySet()) {
            String source = stripComments(Files.readString(COMMANDS.resolve(mutation.getKey())));
            assertThat(source)
                    .as(mutation.getKey())
                    .contains("AdminAudit")
                    .contains("audit.record(")
                    .contains(mutation.getValue());
        }

        assertThat(stripComments(Files.readString(COMMANDS.resolve("InspectCommand.java"))))
                .doesNotContain("AdminAudit")
                .doesNotContain("audit.record(")
                .doesNotContain("append(");
        assertThat(stripComments(Files.readString(COMMANDS.resolve("AuditCommand.java"))))
                .doesNotContain("AdminAudit")
                .doesNotContain("audit.record(")
                .doesNotContain("append(");
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}

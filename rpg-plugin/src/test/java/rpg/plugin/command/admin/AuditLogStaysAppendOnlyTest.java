package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T103 / FR-031a — from B14 there is one audit-log write path: AdminAudit.record. */
class AuditLogStaysAppendOnlyTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    @Test
    @DisplayName("only AdminAudit reaches AuditLogRepository.append")
    void onlyAdminAuditWritesTheAuditLog() throws IOException {
        List<String> appenders = new ArrayList<>();
        try (var sources = Files.walk(SOURCES)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(source, StandardCharsets.UTF_8));
                if (code.contains("AuditLogRepository") && code.contains("append(")) {
                    appenders.add(source.getFileName().toString());
                }
            }
        }

        assertThat(appenders).containsExactly("AdminAudit.java");
    }

    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        String withoutLineComments = withoutBlockComments.replaceAll("(?m)//.*$", " ");
        return withoutLineComments.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}

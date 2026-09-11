package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T082/T089 — B14 deliberately has no command for setting a derived attribute. */
class NoAttributeSetterTest {

    private static final Path ADMIN_COMMANDS =
            Path.of("src", "main", "java", "rpg", "plugin", "command", "admin");

    @Test
    @DisplayName("the admin command surface contains no individual attribute setter")
    void noAttributeSetterIsExposed() throws IOException {
        List<String> sourceFiles;
        try (var files = Files.walk(ADMIN_COMMANDS)) {
            sourceFiles =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .map(path -> read(path))
                            .toList();
        }

        assertThat(sourceFiles)
                .as("B14 must not grow a setter for derived StatEngine attributes")
                .allSatisfy(
                        source ->
                                assertThat(source)
                                        .doesNotContain("Argument<Attribute>")
                                        .doesNotContain("set attribute")
                                        .doesNotContain("setAttribute"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AssertionError("could not read " + path, failure);
        }
    }
}

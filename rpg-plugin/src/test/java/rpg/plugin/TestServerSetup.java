package rpg.plugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;

import rpg.persistence.support.PostgresContainer;

/**
 * Points a mocked server's plugin folder at the test database.
 *
 * <p>Every test in this module either wants a working database or deliberately wants none, and both
 * come down to what is in {@code persistence.yml} before the plugin loads. Putting that in one place
 * keeps the difference between the two cases visible in the tests themselves.
 */
final class TestServerSetup {

    private TestServerSetup() {}

    /**
     * The folder the plugin will use, before it exists.
     *
     * <p>Writing a file here beforehand is what makes it survive {@code saveResource}, which leaves
     * an existing file alone - the same behaviour that protects an operator's edits across restarts.
     *
     * <p>MockBukkit names the folder {@code <name>-<version>} from plugin.yml, so both are read from
     * the same file the server reads rather than repeated here. A hard-coded name silently stops
     * matching at the next version bump, and the symptom would be this setup writing a config
     * nothing reads - which looks exactly like the plugin ignoring its configuration.
     */
    static Path dataFolder() throws Exception {
        PluginManagerMock plugins = MockBukkit.getMock().getPluginManager();
        return plugins.getParentTemporaryDirectory()
                .toPath()
                .resolve(descriptorValue("name") + "-" + descriptorValue("version"));
    }

    private static String descriptorValue(String key) throws Exception {
        try (InputStream in =
                TestServerSetup.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            if (in == null) {
                throw new IllegalStateException(
                        "plugin.yml is not on the test classpath - run processResources first");
            }
            String descriptor = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher =
                    Pattern.compile("(?m)^" + key + ":\\s*'?([^'\\r\\n]+?)'?\\s*$")
                            .matcher(descriptor);
            if (!matcher.find()) {
                throw new IllegalStateException("plugin.yml has no " + key);
            }
            return matcher.group(1);
        }
    }

    /** Writes a persistence configuration pointing at the running test container. */
    static void useTestDatabase() throws Exception {
        Path folder = dataFolder();
        Files.createDirectories(folder);
        Files.writeString(
                folder.resolve("persistence.yml"),
                """
                persistence:
                  host: %s
                  port: %d
                  database: vuntex_test
                  user: %s
                  password: %s
                  pool:
                    write-size: 2
                    login-size: 2
                  autosave-seconds: 45
                  buffer-capacity: 1000
                  shutdown-flush-seconds: 8
                """
                        .formatted(
                                PostgresContainer.host(),
                                PostgresContainer.port(),
                                PostgresContainer.username(),
                                PostgresContainer.password()));
    }

    /**
     * Writes a persistence configuration nothing can connect to.
     *
     * <p>Port 1 is reserved and never listening, so this is an unreachable database rather than a
     * malformed configuration - the distinction the fail-fast path is about.
     */
    static void useUnreachableDatabase() throws Exception {
        Path folder = dataFolder();
        Files.createDirectories(folder);
        Files.writeString(
                folder.resolve("persistence.yml"),
                """
                persistence:
                  host: 127.0.0.1
                  port: 1
                  database: vuntex
                  user: vuntex
                  password: irrelevant
                  pool:
                    write-size: 1
                    login-size: 1
                """);
    }
}

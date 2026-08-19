package rpg.core.config;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Test double that feeds already-parsed documents into {@link AbstractConfigLoader}.
 *
 * <p>Lets the core tests exercise validation, global reload and rollback without a file system, a
 * YAML parser or a running server (Constitution VII.1). The production {@code YamlConfigLoader} in
 * {@code rpg-platform} plugs SnakeYAML into the very same base class, so the logic covered here is
 * the logic that ships.
 */
final class InMemoryConfigLoader extends AbstractConfigLoader {

    private final Map<Path, Map<String, Object>> documents = new HashMap<>();
    private final Map<Path, RuntimeException> parseFailures = new HashMap<>();

    /** Sets (or replaces) the document served for {@code source}. */
    void put(Path source, Map<String, Object> document) {
        documents.put(source, document);
        parseFailures.remove(source);
    }

    /** Makes parsing of {@code source} fail, simulating a syntactically broken file. */
    void failParsing(Path source, RuntimeException failure) {
        parseFailures.put(source, failure);
        documents.remove(source);
    }

    @Override
    protected Map<String, Object> parse(Path source) throws ConfigValidationException {
        RuntimeException failure = parseFailures.get(source);
        if (failure != null) {
            throw new ConfigValidationException(
                    source, "<document>", "a parsable document", failure.getMessage(), failure);
        }
        Map<String, Object> document = documents.get(source);
        if (document == null) {
            throw new ConfigValidationException(
                    source, "<document>", "an existing configuration file", "missing");
        }
        return document;
    }
}

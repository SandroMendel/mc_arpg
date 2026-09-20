package rpg.platform.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import rpg.core.config.AbstractConfigLoader;
import rpg.core.config.ConfigValidationException;

/**
 * YAML-backed {@link rpg.core.config.ConfigLoader}.
 *
 * <p>YAML is the established convention in the Bukkit/Paper ecosystem, so operators and moderators
 * adjusting balancing values (B16) already know the format (research.md, "Konfigurationsformat").
 *
 * <p>This class contributes only the parsing step. Schema validation, the atomic global reload and
 * the rollback to the previously valid configuration all live in {@link AbstractConfigLoader} in
 * {@code rpg-core}, where they are covered by server-free unit tests (T034/T035). That is why the
 * rollback behaviour required by T037 needs no YAML-specific code: this loader inherits it, and
 * inheriting it is what guarantees the tested behaviour is the shipped behaviour.
 *
 * <p>Parsing uses SnakeYAML's {@link SafeConstructor}: a configuration file must never be able to
 * instantiate arbitrary classes (Constitution VI).
 */
public final class YamlConfigLoader extends AbstractConfigLoader {

    private final Path baseDirectory;

    /**
     * @param baseDirectory directory relative paths are resolved against, typically the plugin's data
     *     folder
     */
    public YamlConfigLoader(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * Reads a YAML document without validating it against a schema.
     *
     * <p>For sources whose keys are open-ended and therefore cannot be described by a
     * {@link rpg.core.config.ConfigSchema} - {@code messages.yml} being the case this exists for,
     * since every block adds its own keys. Schema validation still applies to everything that has a
     * fixed shape; this is the deliberate exception, not a general bypass.
     */
    public Map<String, Object> readDocument(Path source) throws ConfigValidationException {
        return parse(source);
    }

    /**
     * Reads several YAML roots in caller-supplied order without applying a domain schema.
     *
     * <p>This is the parser-side seam available to a B16 coordinator: all files can be parsed first,
     * then {@code rpg-content} can perform version, structure, binding and cross-domain checks as
     * one staging operation. No content definition or snapshot is created here.
     *
     * @throws ConfigValidationException when the first source cannot be read or parsed
     */
    public Map<Path, Map<String, Object>> readDocuments(List<Path> sources)
            throws ConfigValidationException {
        Objects.requireNonNull(sources, "sources");
        Map<Path, Map<String, Object>> documents = new LinkedHashMap<>();
        for (Path source : sources) {
            Objects.requireNonNull(source, "source");
            if (documents.containsKey(source)) {
                throw new IllegalArgumentException("duplicate YAML source: " + source);
            }
            documents.put(source, readDocument(source));
        }
        return Collections.unmodifiableMap(documents);
    }

    @Override
    protected Map<String, Object> parse(Path source) throws ConfigValidationException {
        Path resolved = source.isAbsolute() ? source : baseDirectory.resolve(source);

        if (!Files.exists(resolved)) {
            throw new ConfigValidationException(
                    source, "<document>", "an existing configuration file", "no file at " + resolved);
        }

        try (InputStream in = Files.newInputStream(resolved)) {
            Object parsed = newYaml().load(in);
            if (parsed == null) {
                throw new ConfigValidationException(
                        source,
                        "<document>",
                        "a mapping at the document root",
                        "null (empty YAML document or explicit null)");
            }
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new ConfigValidationException(
                        source,
                        "<document>",
                        "a mapping at the document root",
                        parsed.getClass().getSimpleName());
            }
            return toStringKeyedMap(map);
        } catch (YAMLException malformed) {
            // SnakeYAML exposes duplicate-key details through this exception, but does not
            // provide a stable dotted document path. Preserve its message and cause rather than
            // introducing a second parser solely to manufacture a path.
            throw new ConfigValidationException(
                    source, "<document>", "well-formed YAML", malformed.getMessage(), malformed);
        } catch (IOException unreadable) {
            throw new ConfigValidationException(
                    source, "<document>", "a readable configuration file", unreadable.toString(), unreadable);
        }
    }

    private static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false); // a duplicated key is an operator mistake, not a merge
        return new Yaml(new SafeConstructor(options));
    }

    /**
     * Normalizes only the document root. Nested maps are intentionally left untouched: existing
     * open mappings and B16 numeric level registries may use non-string keys.
     */
    private static Map<String, Object> toStringKeyedMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}

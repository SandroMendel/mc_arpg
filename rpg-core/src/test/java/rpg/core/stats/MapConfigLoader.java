package rpg.core.stats;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import rpg.core.config.AbstractConfigLoader;
import rpg.core.config.ConfigValidationException;

/**
 * A config loader over an in-memory document.
 *
 * <p>B01 has one of these in its own test sources, but it is package-private there. Rather than
 * widening B01's test surface for B04's benefit, this is the same few lines again - which is
 * cheaper than the coupling would be.
 */
final class MapConfigLoader extends AbstractConfigLoader {

    private final Map<Path, Map<String, Object>> documents = new HashMap<>();

    void put(Path source, Map<String, Object> document) {
        documents.put(source, document);
    }

    @Override
    protected Map<String, Object> parse(Path source) throws ConfigValidationException {
        Map<String, Object> document = documents.get(source);
        if (document == null) {
            throw new ConfigValidationException(
                    source, "<document>", "an existing configuration file", "missing");
        }
        return document;
    }
}

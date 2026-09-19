package rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigValidationException;
import rpg.core.zone.WorldResolver;

class B16ContentLoaderTest {

    @Test
    void parserFailureStopsInCanonicalReadOrderBeforeSchemaBinding() {
        B16ContentLoader.Sources sources = B16ContentLoader.Sources.standard();
        List<Path> read = new ArrayList<>();

        B16ContentLoader.DocumentReader reader =
                source -> {
                    read.add(source);
                    if (source.equals(sources.abilities())) {
                        throw new ConfigValidationException(
                                source, "<document>", "well-formed YAML", "malformed");
                    }
                    return Map.of();
                };

        assertThatThrownBy(
                        () ->
                                new B16ContentLoader(reader, WorldResolver.of(Map.of()))
                                        .load(sources))
                .isInstanceOf(ConfigValidationException.class);
        assertThat(read).containsExactly(sources.classes(), sources.abilities());
    }

    @Test
    void everySourceIsParsedBeforeTheFirstSchemaValidationFailure() {
        B16ContentLoader.Sources sources = B16ContentLoader.Sources.standard();
        List<Path> read = new ArrayList<>();

        assertThatThrownBy(
                        () ->
                                new B16ContentLoader(
                                                source -> {
                                                    read.add(source);
                                                    return Map.of();
                                                },
                                                WorldResolver.of(Map.of()))
                                        .load(sources))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("classes.yml")
                .hasMessageContaining("schemaVersion");

        assertThat(read).containsExactlyElementsOf(sources.orderedPaths());
    }
}

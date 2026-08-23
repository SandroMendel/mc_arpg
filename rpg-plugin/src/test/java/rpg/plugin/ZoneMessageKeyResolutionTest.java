package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.zone.ZoneMessageKeys;

/**
 * T022 - every key B09 can ask for has a text, including one name per shipped region (FR-003a,
 * FR-003c, SC-015, SC-017).
 *
 * <p>Lives here because both resources are on this classpath. The per-zone name keys are the reason
 * this test exists at all: they are not in the plugin's fixed key list - the zone keys are only known
 * once {@code zones.yml} has been read, so {@code ZoneModule.start} checks them at runtime. This is
 * the same check, run at build time, so a missing name never gets as far as a server start.
 */
class ZoneMessageKeyResolutionTest {

    @Test
    @DisplayName("every fixed key of this block resolves")
    void fixedKeysResolve() throws Exception {
        Messages messages = messages();
        List<MessageKey> missing = new ArrayList<>();

        for (MessageKey key : ZoneMessageKeys.all(List.of())) {
            if (!messages.contains(key)) {
                missing.add(key);
            }
        }

        assertThat(missing).as("a key without a text is a blank message in front of a player").isEmpty();
    }

    @Test
    @DisplayName("every shipped region has a name behind its key (FR-003c)")
    void everyRegionHasAName() throws Exception {
        Messages messages = messages();
        List<String> missing = new ArrayList<>();

        for (String zoneKey : shippedZoneKeys()) {
            MessageKey key = ZoneMessageKeys.nameOf(zoneKey);
            if (!messages.contains(key)) {
                missing.add(key.value());
            }
        }

        assertThat(missing)
                .as("a region whose name shows up in game as a key string is a start failure")
                .isEmpty();
    }

    @Test
    @DisplayName("renaming a region touches one line in messages.yml and nothing else (SC-017)")
    void renamingIsOneLine() throws Exception {
        Messages messages = messages();

        // The visible text is reachable only through the key, so a rename cannot reach anything
        // else: no crystal, no spawn area and no later block ever sees the text.
        assertThat(messages.get(ZoneMessageKeys.nameOf("darkforest"))).isEqualTo("The Darkforest");
        assertThat(messages.get(ZoneMessageKeys.nameOf("pale-wilds"))).isEqualTo("The Pale Wilds");
    }

    @SuppressWarnings("unchecked")
    private static List<String> shippedZoneKeys() throws Exception {
        Map<String, Object> document = load("/zones.yml");
        Map<String, Object> zones = (Map<String, Object>) document.get("zones");
        return List.copyOf(zones.keySet());
    }

    private static Messages messages() throws Exception {
        return MapMessages.fromNested(load("/messages.yml"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream in = ZoneMessageKeyResolutionTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource on the classpath: " + resource).isNotNull();
            return (Map<String, Object>)
                    new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

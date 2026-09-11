package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.MessageKeyValidator;
import rpg.core.message.Messages;

/** T108/T114 — beide ausgelieferten Sprachdateien tragen jeden B14-Schlüssel. */
class B14MessageKeyCompletenessTest {

    private static final List<MessageKey> B14_KEYS =
            Stream.of(
                            rpg.plugin.command.CommandMessageKeys.all(),
                            rpg.plugin.command.admin.ItemGiveMessageKeys.all(),
                            rpg.plugin.command.admin.MobSpawnMessageKeys.all(),
                            rpg.plugin.command.admin.ReloadMessageKeys.all(),
                            rpg.plugin.command.admin.SetMessageKeys.all(),
                            rpg.plugin.command.admin.InspectMessageKeys.all(),
                            rpg.plugin.command.admin.AuditMessageKeys.all())
                    .flatMap(List::stream)
                    .distinct()
                    .toList();

    @Test
    @DisplayName("MessageKeyValidator bleibt fuer Englisch und Deutsch still")
    void bothShippedLanguagesContainEveryB14Key() {
        for (String resource : List.of("/messages.yml", "/messages_de.yml")) {
            Messages messages = read(resource);
            assertThatCode(() -> MessageKeyValidator.verifyAllPresent(messages, B14_KEYS))
                    .as(resource + " muss alle B14-Schluessel enthalten")
                    .doesNotThrowAnyException();
        }
    }

    @SuppressWarnings("unchecked")
    private static Messages read(String resource) {
        try (InputStream stream = B14MessageKeyCompletenessTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError(resource + " is not shipped");
            }
            Map<String, Object> document =
                    new Yaml()
                            .load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return MapMessages.fromNested(document);
        } catch (IOException unreadable) {
            throw new AssertionError("could not read " + resource, unreadable);
        }
    }
}

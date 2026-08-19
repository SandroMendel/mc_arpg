package rpg.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import rpg.core.message.MessageKeyValidator.MissingMessageKeysException;

/**
 * T014 / FR-023, FR-023a: texts are reached through keys, and a missing text stops the start
 * instead of reaching a player as a blank line.
 */
class MapMessagesTest {

    private static final MessageKey GREETING = MessageKey.of("server.greeting");
    private static final MessageKey KICK = MessageKey.of("server.kick.starting-up");
    private static final MessageKey ABSENT = MessageKey.of("server.absent");

    private static Messages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("server.greeting", "Welcome, {player}!");
        texts.put("server.kick.starting-up", "The server is still starting up.");
        return new MapMessages(texts);
    }

    @Test
    void aConfiguredKeyResolvesToItsText() {
        assertThat(messages().get(KICK)).isEqualTo("The server is still starting up.");
    }

    @Test
    void placeholdersAreSubstituted() {
        String text = messages().get(GREETING, Map.of("player", "Steve"));

        assertThat(text).isEqualTo("Welcome, Steve!");
    }

    @Test
    void anUnknownPlaceholderIsLeftVisibleRatherThanBlanked() {
        // The gap should be obvious in the output, not silently swallowed.
        assertThat(messages().get(GREETING, Map.of())).isEqualTo("Welcome, {player}!");
    }

    @Test
    void aReplacementContainingAPlaceholderIsNotSubstitutedAgain() {
        // A player literally named "{player}" must not be able to inject another placeholder.
        Map<String, String> texts = Map.of("a.b", "Hello {one} and {two}");
        Messages messages = new MapMessages(texts);

        String text = messages.get(MessageKey.of("a.b"), linked("one", "{two}", "two", "Steve"));

        assertThat(text).isEqualTo("Hello {two} and Steve");
    }

    @Test
    void aMissingKeyThrowsInsteadOfReturningAnEmptyString() {
        assertThatThrownBy(() -> messages().get(ABSENT))
                .isInstanceOf(MissingMessageException.class)
                .hasMessageContaining("server.absent");
    }

    @Test
    void containsReportsPresenceWithoutThrowing() {
        assertThat(messages().contains(KICK)).isTrue();
        assertThat(messages().contains(ABSENT)).isFalse();
    }

    // --- startup validation ---

    @Test
    void validationPassesWhenEveryDeclaredKeyHasAText() {
        MessageKeyValidator.verifyAllPresent(messages(), List.of(GREETING, KICK));
    }

    @Test
    void validationNamesEveryMissingKeyAtOnce() {
        MessageKey alsoAbsent = MessageKey.of("server.also-absent");

        MissingMessageKeysException thrown =
                catchThrowableOfType(
                        MissingMessageKeysException.class,
                        () ->
                                MessageKeyValidator.verifyAllPresent(
                                        messages(), List.of(KICK, ABSENT, alsoAbsent)));

        // All at once, not one per run - an operator fixing the file wants the whole list.
        assertThat(thrown.missingKeys()).containsExactly(ABSENT, alsoAbsent);
        assertThat(thrown)
                .hasMessageContaining("server.absent")
                .hasMessageContaining("server.also-absent");
    }

    // --- key shape ---

    @Test
    void aMalformedKeyIsRejectedAtConstruction() {
        assertThatThrownBy(() -> MessageKey.of("NotDotted"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageKey.of("single")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageKey.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWellFormedKeyIsAccepted() {
        assertThat(MessageKey.of("persistence.login.database-unavailable").value())
                .isEqualTo("persistence.login.database-unavailable");
    }

    private static Map<String, String> linked(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}

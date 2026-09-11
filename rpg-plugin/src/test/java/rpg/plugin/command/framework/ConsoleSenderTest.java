package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;

/**
 * T030 — <b>jedes Kommando läuft von der Konsole</b> (FR-008).
 *
 * <p>Und wenn es einen Spielerbezug braucht, bricht es mit einer <em>Meldung</em> ab. Der
 * Unterschied ist der ganze Punkt: eine Ausnahme im Log sieht aus wie ein Fehler des Servers,
 * obwohl der Betreiber nur etwas getippt hat, was von dort nicht geht.
 */
class ConsoleSenderTest {

    private static final MessageKey ANY = MessageKey.of("command.rpg.description");

    private ServerMock server;
    private CommandTree tree;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        tree = new CommandTree(
                        new CommandErrors(messages()),
                        new RateLimits(Clock.systemUTC()),
                        messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Blatt OHNE Spielerbezug laeuft von der Konsole")
    void aleafWithoutAPlayerRunsFromTheConsole() {
        RpgCommand anywhere = RpgCommand.leaf("reload", ANY, null, List.of(), context -> {});

        assertThat(tree.mayRun(anywhere, server.getConsoleSender())).isTrue();
    }

    @Test
    @DisplayName("ein Blatt MIT Spielerbezug bricht von der Konsole ab - ohne Ausnahme")
    void aleafWithAPlayerRefusesFromTheConsole() {
        RpgCommand needsPlayer =
                RpgCommand.playerLeaf("char", ANY, null, List.of(), context -> {});

        // Kein assertThatThrownBy: eine Ausnahme waere genau das Falsche. Der Rueckgabewert
        // false ist die Zusage, und die Meldung geht an den Absender.
        assertThat(tree.mayRun(needsPlayer, server.getConsoleSender())).isFalse();
    }

    @Test
    @DisplayName("dasselbe Blatt laeuft fuer einen Spieler")
    void thesameLeafRunsForAPlayer() {
        PlayerMock player = server.addPlayer();
        RpgCommand needsPlayer =
                RpgCommand.playerLeaf("char", ANY, null, List.of(), context -> {});

        assertThat(tree.mayRun(needsPlayer, player)).isTrue();
    }

    @Test
    @DisplayName("das Recht wird VOR dem Absender geprueft")
    void thepermissionIsCheckedBeforeTheSender() {
        // Die Reihenfolge ist nicht beliebig: wer nicht darf, soll nicht erfahren, ob das
        // Kommando einen Spieler braucht. Ein Spieler ohne Recht scheitert am Recht, nicht am
        // Spielerbezug - erkennbar daran, dass ein Spieler beides erfuellen wuerde.
        PlayerMock player = server.addPlayer();
        RpgCommand guarded =
                RpgCommand.playerLeaf("secret", ANY, "rpg.admin.secret", List.of(), context -> {});

        assertThat(tree.mayRun(guarded, player)).isFalse();
    }

    @Test
    @DisplayName("die Sperrzeit greift ZULETZT - sie vermerkt, was vorher schon scheitern koennte")
    void therateLimitComesLast() {
        // Ein Vermerk fuer einen Aufruf, der danach am Recht scheitert, waere eine Sperre gegen
        // nichts: der naechste, berechtigte Aufruf traefe auf einen Zeitstempel, den ein
        // abgewiesener Versuch gesetzt hat.
        PlayerMock player = server.addPlayer();
        RpgCommand guardedAndThrottled =
                RpgCommand.leaf("secret", ANY, "rpg.admin.secret", List.of(), context -> {})
                        .throttled(Duration.ofSeconds(30));

        assertThat(tree.mayRun(guardedAndThrottled, player)).isFalse();
        assertThat(tree.mayRun(guardedAndThrottled, player))
                .as("immer noch am Recht gescheitert, nicht an einer Sperre")
                .isFalse();

        player.addAttachment(MockBukkit.createMockPlugin(), "rpg.admin.secret", true);

        assertThat(tree.mayRun(guardedAndThrottled, player))
                .as("die zwei abgewiesenen Versuche haben KEINE Sperre gesetzt")
                .isTrue();
    }

    private static MapMessages messages() {
        Map<String, Object> texts = new LinkedHashMap<>();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("needs-player", "That command needs a player.");
        error.put("denied", "You may not do that.");
        error.put("rate-limited", "Too quick - try again in {seconds}s.");
        error.put("argument-missing", "Missing {argument}.");
        error.put("argument-invalid", "Bad {argument}.");
        error.put("argument-out-of-range", "{argument} must be {min}-{max}.");
        error.put("unknown-player", "No player named {name}.");
        error.put("unknown-key", "Unknown key {key}.");
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("error", error);
        texts.put("command", command);
        return MapMessages.fromNested(texts);
    }
}

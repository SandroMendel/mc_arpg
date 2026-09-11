package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * T031 — <b>ein Tippfehler wird abgelehnt statt als leeres Profil durchgereicht.</b>
 *
 * <p>Der gefährlichste Argumenttyp. {@code getOfflinePlayer(name)} gibt <em>immer</em> etwas
 * zurück, auch für {@code "Steave"}: ein leeres Profil mit frisch erfundener UUID. Wer darauf einen
 * Kontostand setzt, hat ihn einem Spieler gegeben, den es nicht gibt — und niemand erfährt davon,
 * weder der Betreiber noch der Spieler, den er gemeint hat.
 */
class PlayerArgumentTest {

    private ServerMock server;
    private ArgumentType<OfflinePlayer> type;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        type = Arguments.player(server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein online Spieler wird gefunden")
    void anonlinePlayerResolves() throws Exception {
        PlayerMock player = server.addPlayer("Sandro");

        assertThat(type.parse("Sandro").getUniqueId()).isEqualTo(player.getUniqueId());
    }

    @Test
    @DisplayName("ein TIPPFEHLER wird abgelehnt und nicht zu einem leeren Profil")
    void atypoIsRefused() {
        server.addPlayer("Sandro");

        assertThatThrownBy(() -> type.parse("Sandroo"))
                .as("sonst bekaeme ein Spieler, den es nicht gibt, echte Muenzen")
                .isInstanceOf(ArgumentRejected.class);
    }

    @Test
    @DisplayName("die Ablehnung nennt den EINGEGEBENEN Namen")
    void therejectionNamesWhatWasTyped() {
        ArgumentRejected rejected =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        ArgumentRejected.class, () -> type.parse("Steave"));

        assertThat(rejected.key().value()).isEqualTo("command.error.unknown-player");
        assertThat(rejected.placeholders()).containsEntry("name", "Steave");
    }

    @Test
    @DisplayName("vorgeschlagen werden nur die ONLINE Anwesenden")
    void onlyOnlinePlayersAreSuggested() {
        // Der Namens-Cache eines gewachsenen Servers hat Zehntausende Eintraege, und die schickt
        // niemand je Tastendruck durch die Leitung (FR-033).
        server.addPlayer("Sandro");
        server.addPlayer("Jonas");

        assertThat(type.suggest("")).containsExactlyInAnyOrder("Sandro", "Jonas");
    }

    @Test
    @DisplayName("Vorschlaege werden am schon Getippten gefiltert")
    void suggestionsAreFiltered() {
        server.addPlayer("Sandro");
        server.addPlayer("Jonas");

        assertThat(type.suggest("San")).containsExactly("Sandro");
    }

    @Test
    @DisplayName("jeder Vorschlag besteht die Pruefung - FR-002 auch hier")
    void everySuggestionParses() throws Exception {
        server.addPlayer("Sandro");
        server.addPlayer("Jonas");

        for (String suggestion : type.suggest("")) {
            assertThat(type.parse(suggestion)).isNotNull();
        }
    }
}

package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.module.BootstrapState;

/**
 * What happens when the database cannot be reached at startup.
 *
 * <p>The wrong behaviour here is the one that looks friendlier: starting anyway, letting players in
 * and serving them empty profiles. Every one of those profiles is written back at the next autosave,
 * and the real records are gone. So the plugin refuses to start instead, and the server refuses
 * players - a refused login is recoverable, an overwritten record is not (FR-011, FR-013).
 *
 * <p>This is the counterpart to {@link FullBootstrapTest}: same plugin, same wiring, one line of
 * configuration different.
 */
class UnreachableDatabaseTest {

    private ServerMock server;
    private RpgPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        TestServerSetup.useUnreachableDatabase();
        plugin = MockBukkit.load(RpgPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theBootstrapFailsRatherThanStartingWithoutStorage() {
        assertThat(plugin.bootstrapState().phase()).isEqualTo(BootstrapState.Phase.FAILED);
    }

    @Test
    void noPlayerIsAdmitted() {
        // The single most important consequence: nobody gets a session that could later overwrite
        // their stored one.
        assertThat(plugin.bootstrapState().acceptsPlayers()).isFalse();
    }

    @Test
    void thePluginDisablesItselfInsteadOfRunningHalfInitialised() {
        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    void theFailureIsRecordedWithAReason() {
        // An operator has to be able to tell "no database" from "bad configuration" from the log
        // alone; a bare failure state would send them looking in the wrong place.
        assertThat(plugin.bootstrapState().failureReason()).isNotEmpty();
    }
}

package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;

/**
 * T107 — eine wiederholbare Messung fuer Registrierung und Vervollstaendigung.
 *
 * <p>Gemessen wird nur die eigene Arbeit des Kommandogeruests: den Brigadier-Baum aus den
 * Deklarationen bauen und die begrenzte Vorschlagsliste fuer einen Online-Spieler erzeugen. Das
 * ist kein Lasttest und behauptet nichts ueber 150 Spieler, 800 Mobs oder Netzwerkpakete; dieser
 * Nachweis gehoert nach ADR-031 in B15.
 */
class CommandTickCostBenchmark {

    private static final long TICK_BUDGET_NANOS = Duration.ofMillis(5).toNanos();
    private static final int WARMUP_ROUNDS = 20;
    private static final int SAMPLES = 40;

    private ServerMock server;
    private ArgumentType<OfflinePlayer> players;
    private CommandTree tree;
    private RpgCommand root;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        for (int index = 0; index < 200; index++) {
            server.addPlayer("P" + String.format("%03d", index));
        }

        players = Arguments.player(server);
        MapMessages messages = new MapMessages(java.util.Map.of());
        tree = new CommandTree(new CommandErrors(messages), new RateLimits(Clock.systemUTC()), messages);
        root = commandTreeFixture();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Registrierung des B14-Baums bleibt unter dem Tick-Budget")
    void registrationStaysUnderFiveMilliseconds() {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            tree.build(root);
        }

        long[] samples = new long[SAMPLES];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            tree.build(root);
            samples[index] = System.nanoTime() - started;
        }

        assertThat(percentile(samples, 95))
                .as("p95 des Baumaufbaus in ns")
                .isLessThan(TICK_BUDGET_NANOS);
    }

    @Test
    @DisplayName("Spieler-Vervollstaendigung bleibt unter dem Tick-Budget")
    void completionStaysUnderFiveMilliseconds() {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            players.suggest("P");
        }

        long[] samples = new long[SAMPLES];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            assertThat(players.suggest("P")).hasSize(ArgumentType.SUGGESTION_LIMIT);
            samples[index] = System.nanoTime() - started;
        }

        assertThat(percentile(samples, 95))
                .as("p95 der Spieler-Vervollstaendigung in ns")
                .isLessThan(TICK_BUDGET_NANOS);
    }

    private RpgCommand commandTreeFixture() {
        MessageKey description = MessageKey.of("command.benchmark.description");
        List<RpgCommand> leaves = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            List<Argument<?>> arguments =
                    index % 2 == 0
                            ? List.of(Argument.required("player", players))
                            : List.of(Argument.optional("duration", Arguments.duration(Duration.ofDays(7))));
            leaves.add(
                    RpgCommand.leaf(
                            "command" + index,
                            description,
                            "rpg.admin.benchmark",
                            arguments,
                            context -> {}));
        }
        return RpgCommand.branch("rpg", description, null, leaves);
    }

    private static long percentile(long[] samples, int percentile) {
        long[] sorted = samples.clone();
        java.util.Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, (sorted.length * percentile + 99) / 100 - 1);
        return sorted[index];
    }
}

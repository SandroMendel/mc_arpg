package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.message.MessageKey;

/** T029 — die Sperrzeit rechnet aus Zeitstempeln, läuft ab, und die Konsole ist frei. */
class RateLimitsTest {

    private static final MessageKey ANY = MessageKey.of("command.rpg.description");

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private ServerMock server;
    private PlayerMock player;
    private RateLimits limits;
    private RpgCommand throttled;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        limits = new RateLimits(frozenClock());
        throttled =
                RpgCommand.leaf("top", ANY, null, List.of(), context -> {})
                        .throttled(Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der erste Aufruf geht durch")
    void thefirstCallPasses() {
        assertThat(limits.check(player, throttled)).isEmpty();
    }

    @Test
    @DisplayName("der zweite sofort danach wird gesperrt - MIT Restzeit")
    void thesecondIsBlockedWithRemainingTime() {
        limits.check(player, throttled);

        // FR-032 verlangt die Restzeit ausdruecklich: eine Sperre, die nur schweigt, ist von
        // einem defekten Kommando nicht zu unterscheiden.
        assertThat(limits.check(player, throttled))
                .isPresent()
                .get()
                .satisfies(rest -> assertThat(rest).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(3)));
    }

    @Test
    @DisplayName("nach Ablauf geht es wieder - ohne dass etwas aufgeraeumt haette")
    void afterthewindowItPassesAgain() {
        limits.check(player, throttled);

        now.addAndGet(Duration.ofSeconds(3).toMillis());

        // Lazy heisst: es laeuft KEIN wiederkehrender Task, der den Eintrag entfernt haette.
        // Die Zeit allein macht ihn wirkungslos.
        assertThat(limits.check(player, throttled)).isEmpty();
    }

    @Test
    @DisplayName("die Sperre gilt je KOMMANDO, nicht je Spieler")
    void thelimitIsPerCommand() {
        RpgCommand other =
                RpgCommand.leaf("stats", ANY, null, List.of(), context -> {})
                        .throttled(Duration.ofSeconds(3));

        limits.check(player, throttled);

        assertThat(limits.check(player, other))
                .as("wer /top benutzt hat, darf trotzdem /stats")
                .isEmpty();
    }

    @Test
    @DisplayName("die Sperre gilt je SPIELER, nicht serverweit")
    void thelimitIsPerSender() {
        PlayerMock second = server.addPlayer();

        limits.check(player, throttled);

        assertThat(limits.check(second, throttled))
                .as("sonst sperrte ein Spieler alle anderen aus")
                .isEmpty();
    }

    @Test
    @DisplayName("die KONSOLE wird nie gesperrt")
    void theconsoleIsNeverBlocked() {
        // Ein Betreiber, der ein Skript ueber die Konsole laufen laesst, ist der Fall, fuer den es
        // eine Konsole gibt. Die Grenze schuetzt vor der Menge gewoehnlicher Spieler.
        for (int i = 0; i < 20; i++) {
            assertThat(limits.check(server.getConsoleSender(), throttled)).isEmpty();
        }
    }

    @Test
    @DisplayName("ein Kommando OHNE Sperrzeit wird nie gesperrt")
    void acommandWithoutALimitIsNeverBlocked() {
        RpgCommand free = RpgCommand.leaf("char", ANY, null, List.of(), context -> {});

        for (int i = 0; i < 20; i++) {
            assertThat(limits.check(player, free)).isEmpty();
        }
    }

    @Test
    @DisplayName("das Sitzungsende vergisst den Spieler - sonst waechst die Karte unbegrenzt")
    void thesessionEndForgetsThePlayer() {
        limits.check(player, throttled);
        assertThat(limits.trackedSenders()).isEqualTo(1);

        limits.forget(player.getUniqueId());

        assertThat(limits.trackedSenders()).isZero();
    }

    private Clock frozenClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }

            @Override
            public long millis() {
                return now.get();
            }
        };
    }
}

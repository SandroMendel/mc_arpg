package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;

/**
 * No recurring work per player, character or party (FR-061, SC-012).
 *
 * <p>Principle II forbids periodic per-entity tasks outright, because they are what stops scaling at
 * 150 players. Everything time-based in this block - invite expiry, the bundling window - is a
 * timestamp evaluated when something asks. The assertion is that the scheduler's counter does not
 * move with the number of players.
 */
class NoScheduledTaskTest {

    @Test
    @DisplayName("the task count is the same at 1, 50 and 200 players")
    void taskCountIsIndependentOfPlayerCount() {
        List<Integer> counts = new ArrayList<>();
        for (int players : new int[] {1, 50, 200}) {
            counts.add(scheduledFor(players));
        }

        // SC-012. Not "small" - identical. A count that grew even slowly would mean somebody added
        // work per player, and at 200 that is 200 timers.
        assertThat(counts).containsExactly(counts.get(0), counts.get(0), counts.get(0));
        assertThat(counts.get(0)).isZero();
    }

    @Test
    @DisplayName("an idle server schedules nothing at all")
    void idleSchedulesNothing() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        for (int i = 0; i < 100; i++) {
            fixture.character();
        }

        // A hundred characters loaded and nothing happening: no cost, no tasks.
        assertThat(fixture.scheduler.scheduled).isZero();
    }

    @Test
    @DisplayName("forty parties with pending invitations still schedule nothing")
    void partiesScheduleNothing() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        Logger quiet = Logger.getLogger("quiet");
        quiet.setLevel(Level.OFF);
        PartyRegistry parties =
                new PartyRegistry(
                        fixture.sessions,
                        new DefaultEventBus(quiet),
                        fixture.clock,
                        5,
                        Duration.ofSeconds(60));

        for (int i = 0; i < 40; i++) {
            UUID leader = fixture.playerOf(fixture.character());
            UUID guest = fixture.playerOf(fixture.character());
            parties.invite(leader, guest);
            parties.accept(guest);
            // And one invitation nobody ever answers - the case a timer would exist for.
            parties.invite(leader, fixture.playerOf(fixture.character()));
        }

        assertThat(parties.partyCount()).isEqualTo(40);
        assertThat(fixture.scheduler.scheduled)
                .as("FR-031: expiry is checked when asked, not by a task")
                .isZero();
    }

    private static int scheduledFor(int players) {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        List<UUID> characters = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            characters.add(fixture.character());
        }
        for (UUID character : characters) {
            fixture.progression.grant(character, 12L, XpSource.MOB_KILL);
        }
        return fixture.scheduler.scheduled;
    }
}

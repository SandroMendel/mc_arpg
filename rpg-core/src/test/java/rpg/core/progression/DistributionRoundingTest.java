package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;

/**
 * Experience must never be created out of nothing by rounding (FR-047, SC-013).
 *
 * <p>Rounding down with the remainder left on the table. Rounding up would have produced up to four
 * experience per kill with a five-member party - at 800 active mobs a visible inflation that nobody
 * configured.
 */
class DistributionRoundingTest {

    @Test
    @DisplayName("for every party size the total stays within the amount plus the bonus")
    void totalNeverExceedsAmountPlusBonus() {
        long mobXp = 100L;

        for (int partySize = 1; partySize <= 5; partySize++) {
            Fixture f = new Fixture(mobXp);
            List<UUID> characters = new ArrayList<>();
            List<UUID> players = new ArrayList<>();
            for (int i = 0; i < partySize; i++) {
                UUID character = f.fixture.character();
                characters.add(character);
                players.add(f.fixture.playerOf(character));
            }
            UUID leader = players.get(0);
            for (int i = 1; i < partySize; i++) {
                f.parties.invite(leader, players.get(i));
                f.parties.accept(players.get(i));
            }
            f.everyoneInRange();

            // The whole share sits on the leader; the others contributed nothing.
            f.distributor.distribute(f.death(Map.of(leader, 1.0)), "SHEEP", f.origin);

            long total = 0L;
            for (UUID character : characters) {
                total += f.xpOf(character);
            }
            long bonusCeiling =
                    (long) Math.floor(mobXp * (1.0 + f.fixture.config.bonusFor(partySize)));

            assertThat(total)
                    .as("party of " + partySize + ": never more than the amount plus the bonus")
                    .isLessThanOrEqualTo(bonusCeiling);
            assertThat(total)
                    .as("party of " + partySize + ": and never nothing either")
                    .isPositive();
        }
    }

    @Test
    @DisplayName("an amount that does not divide evenly leaves the remainder on the table")
    void remainderIsLeftBehind() {
        // 100 experience, three members in range, +20 % bonus = 120, divided by 3 = exactly 40.
        // Then 101: 121 / 3 = 40 each, so one experience stays unclaimed rather than being invented.
        Fixture f = new Fixture(101L);
        UUID a = f.fixture.character();
        UUID b = f.fixture.character();
        UUID c = f.fixture.character();
        UUID pa = f.fixture.playerOf(a);
        f.parties.invite(pa, f.fixture.playerOf(b));
        f.parties.accept(f.fixture.playerOf(b));
        f.parties.invite(pa, f.fixture.playerOf(c));
        f.parties.accept(f.fixture.playerOf(c));
        f.everyoneInRange();

        f.distributor.distribute(f.death(Map.of(pa, 1.0)), "SHEEP", f.origin);

        long total = f.xpOf(a) + f.xpOf(b) + f.xpOf(c);
        assertThat(f.xpOf(a)).isEqualTo(40L);
        assertThat(total).isEqualTo(120L);
        assertThat(total).isLessThan(121L);
    }

    @Test
    @DisplayName("a share too small to round up to one credits nothing rather than a free point")
    void tinyShareCreditsNothing() {
        Fixture f = new Fixture(10L);
        UUID a = f.fixture.character();
        UUID b = f.fixture.character();

        // 1 % of 10 experience is 0.1 - floor is 0. Rounding up would hand out a point per kill for
        // grazing a mob once.
        f.distributor.distribute(
                f.death(Map.of(f.fixture.playerOf(a), 0.99, f.fixture.playerOf(b), 0.01)),
                "SHEEP",
                f.origin);

        assertThat(f.xpOf(a)).isEqualTo(9L);
        assertThat(f.xpOf(b)).isZero();
    }

    /** A distributor with a curve high enough that nobody levels up during the arithmetic. */
    private static final class Fixture {

        final ProgressionFixture fixture;
        final PartyRegistry parties;
        final XpDistributor distributor;
        final UUID mob = UUID.randomUUID();
        final WorldPoint origin = new WorldPoint(UUID.randomUUID(), 0, 64, 0);

        Fixture(long mobXp) {
            Map<Integer, Long> curve = new LinkedHashMap<>();
            curve.put(2, 100_000L);
            fixture = new ProgressionFixture(ProgressionFixture.config(curve));
            Logger quiet = Logger.getLogger("quiet");
            quiet.setLevel(Level.OFF);
            parties =
                    new PartyRegistry(
                            fixture.sessions,
                            fixture.eventBus,
                            fixture.clock,
                            5,
                            Duration.ofSeconds(60));
            distributor =
                    new XpDistributor(
                            fixture.progression, parties, fixture.stats, fixture.config, quiet);
            fixture.progression.setMobXpProvider(key -> OptionalLong.of(mobXp));
        }

        void everyoneInRange() {
            fixture.progression.setProximityCheck(
                    (o, candidates, count, range, out) -> {
                        System.arraycopy(candidates, 0, out, 0, count);
                        return count;
                    });
        }

        CombatDeathEvent death(Map<UUID, Double> shares) {
            UUID top =
                    shares.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);
            return new CombatDeathEvent(
                    mob, null, top, DeathCause.COMBAT, new DamageShare(shares, top, 100.0), false);
        }

        long xpOf(UUID character) {
            return fixture.progression.progressOf(character).orElseThrow().xpInLevel();
        }
    }
}

package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T071-T076: who contributed what, bounded in count and age (FR-031 to FR-036, SC-007, SC-008). */
class AttributionWindowTest {

    private CombatFixture.TestClock clock;

    private AttributionWindow window(int capacity, Duration timeout) {
        clock = new CombatFixture.TestClock();
        return new AttributionWindow(clock, capacity, timeout);
    }

    @Test
    @DisplayName("three attackers at 60/30/10 produce exactly those shares")
    void sharesAreProportional() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        window.record(target, a, 60.0);
        window.record(target, b, 30.0);
        window.record(target, c, 10.0);

        DamageShare share = window.shareOf(target);
        assertThat(share.shareOf(a)).isEqualTo(0.6, within(1e-12));
        assertThat(share.shareOf(b)).isEqualTo(0.3, within(1e-12));
        assertThat(share.shareOf(c)).isEqualTo(0.1, within(1e-12));
        assertThat(share.topContributorId()).contains(a);
        assertThat(share.totalDamage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("repeated hits from the same attacker accumulate into one slot")
    void hitsAccumulate() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        for (int i = 0; i < 50; i++) {
            window.record(target, attacker, 2.0);
        }

        assertThat(window.attackerCount(target)).isEqualTo(1);
        assertThat(window.shareOf(target).totalDamage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("100 attackers on a 16-slot window stay at 16; the smallest is evicted")
    void boundedByCount() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();

        UUID big = UUID.randomUUID();
        window.record(target, big, 10_000.0);
        for (int i = 0; i < 100; i++) {
            window.record(target, UUID.randomUUID(), 1.0 + i);
        }

        assertThat(window.attackerCount(target)).isEqualTo(16);
        // The largest contribution is never the one evicted.
        assertThat(window.shareOf(target).topContributorId()).contains(big);
    }

    @Test
    @DisplayName("a contribution older than the timeout stops counting")
    void boundedByAge() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();
        UUID early = UUID.randomUUID();
        UUID late = UUID.randomUUID();

        window.record(target, early, 100.0);
        clock.advance(Duration.ofSeconds(31));
        window.record(target, late, 10.0);

        DamageShare share = window.shareOf(target);
        assertThat(share.shareOf(early)).isEqualTo(0.0);
        assertThat(share.shareOf(late)).isEqualTo(1.0);
        assertThat(share.topContributorId()).contains(late);
    }

    @Test
    @DisplayName("an attacker returning after a long pause counts again, from the return")
    void returningAttacker() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        window.record(target, attacker, 500.0);
        clock.advance(Duration.ofSeconds(31));
        window.record(target, attacker, 10.0);

        // The old 500 expired; only what came after the return counts.
        assertThat(window.shareOf(target).totalDamage()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("self damage produces no contribution")
    void selfDamageIsNotRecorded() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();

        window.record(target, target, 100.0);

        assertThat(window.shareOf(target).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a target nobody touched has an empty split - and nobody gets anything")
    void emptySplit() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));

        DamageShare share = window.shareOf(UUID.randomUUID());

        assertThat(share.isEmpty()).isTrue();
        assertThat(share.topContributorId()).isEmpty();
        assertThat(share.totalDamage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("consuming the window releases it")
    void consumeReleases() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();
        window.record(target, UUID.randomUUID(), 10.0);
        assertThat(window.trackedCount()).isEqualTo(1);

        DamageShare share = window.consume(target);

        assertThat(share.isEmpty()).isFalse();
        assertThat(window.trackedCount()).isZero();
    }

    @Test
    @DisplayName("forget releases without producing a split")
    void forgetReleases() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();
        window.record(target, UUID.randomUUID(), 10.0);

        window.forget(target);

        assertThat(window.trackedCount()).isZero();
        assertThat(window.shareOf(target).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("10 000 contributions leave the window at its fixed size")
    void staysBounded() {
        AttributionWindow window = window(16, Duration.ofSeconds(30));
        UUID target = UUID.randomUUID();

        for (int i = 0; i < 10_000; i++) {
            window.record(target, UUID.randomUUID(), 1.0);
        }

        assertThat(window.attackerCount(target)).isEqualTo(16);
        assertThat(window.trackedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("through the pipeline: the death event carries the split")
    void throughThePipeline() {
        CombatFixture fixture = new CombatFixture();
        UUID heavy = fixture.player(60.0, 0.0, 100.0);
        UUID light = fixture.player(20.0, 0.0, 100.0);
        UUID target = fixture.mob(240.0, 0.0, 5.0);

        // 60 + 20 = 80 per round, three rounds = 240 - exactly the mob's health.
        //
        // 200 ms between rounds, not 50: attack speed is capped by its band at 6 per second
        // (B04 clamps the requested 100 to base x 1.5), so the gap is 166 ms. At 50 ms two thirds
        // of the swings would be discarded and nothing would die - which is the attack window
        // doing its job, not a bug.
        for (int i = 0; i < 3; i++) {
            fixture.pipeline.meleeAttack(heavy, target);
            fixture.pipeline.meleeAttack(light, target);
            fixture.clock.advanceMillis(200);
        }

        assertThat(fixture.deaths).singleElement().satisfies(death -> {
            assertThat(death.shares().shareOf(heavy)).isEqualTo(0.75, within(1e-9));
            assertThat(death.shares().shareOf(light)).isEqualTo(0.25, within(1e-9));
            assertThat(death.lootRecipient()).contains(heavy);
        });
    }
}

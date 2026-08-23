package rpg.core.ability.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.Ability;
import rpg.core.ability.EffectSpec;
import rpg.core.ability.EffectType;
import rpg.core.ability.TargetMode;
import rpg.core.ability.TargetResolver;
import rpg.core.ability.TargetSpec;
import rpg.core.classes.AbilityKind;
import rpg.core.message.MessageKey;
import rpg.core.scheduler.WorldPosition;
import rpg.core.stats.StatSnapshot;

/**
 * Eine verankerte Flaeche wirkt auf den ORT, nicht auf die Kreaturen, die beim Wirken darin standen
 * (FR-019b).
 *
 * <p><b>Der Unterschied wird erst mit der Zeit sichtbar, und genau deshalb war er so lange
 * unbemerkt.</b> Beim Wirken sieht beides gleich aus: dieselben Mobs, derselbe Schaden. Erst in der
 * zweiten Sekunde trennt es sich - wer hinauslaeuft, muss aufhoeren zu brennen, und wer hineinlaeuft,
 * muss anfangen. Vorher tat der Blitzsturm das Gegenteil von beidem.
 */
class AnchoredAreaTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-4000-8000-00000000000f");
    private static final UUID CASTER = UUID.randomUUID();
    private static final UUID STANDS_STILL = UUID.randomUUID();
    private static final UUID WALKS_OUT = UUID.randomUUID();
    private static final UUID WALKS_IN = UUID.randomUUID();
    private static final WorldPosition ANCHOR = new WorldPosition(WORLD, 100.0, 64.0, 100.0);

    /** Wer gerade im Sturm steht - der Test schiebt sie hinein und hinaus. */
    private final List<UUID> inTheArea = new ArrayList<>();

    private final List<List<UUID>> hitPerTick = new ArrayList<>();
    private MovableClock clock;
    private IntervalEffectRunner runner;
    private Ability storm;
    private EffectSpec rain;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-08-24T12:00:00Z"));
        EffectDispatcher dispatcher = new EffectDispatcher(Logger.getLogger("anchored-area-test"));
        dispatcher.register(EffectType.DAMAGE, context -> hitPerTick.add(context.targets()));
        runner = new IntervalEffectRunner(dispatcher, clock);
        runner.setTargets(
                new TargetResolver() {
                    @Override
                    public List<UUID> resolve(UUID casterId, TargetSpec spec) {
                        return List.of();
                    }

                    @Override
                    public List<UUID> resolveAt(
                            UUID casterId, WorldPosition anchor, TargetSpec spec) {
                        assertThat(anchor).as("immer derselbe Ort").isEqualTo(ANCHOR);
                        return List.copyOf(inTheArea);
                    }
                });

        rain =
                new EffectSpec(
                        EffectType.DAMAGE,
                        0.7,
                        0.1,
                        Duration.ofSeconds(6),
                        Duration.ofSeconds(1),
                        1,
                        null,
                        null,
                        rpg.core.combat.DamageType.MAGIC,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false);
        storm = storm(rain);
    }

    @Test
    @DisplayName("wer hinauslaeuft, wird nicht mehr getroffen - wer hineinlaeuft, schon")
    void thestormStaysWhereItWasCalledDown() {
        inTheArea.add(STANDS_STILL);
        inTheArea.add(WALKS_OUT);
        runner.startArea(storm, rain, CASTER, ANCHOR, 1, snapshot());

        tick();
        assertThat(hitPerTick.get(0)).containsExactly(STANDS_STILL, WALKS_OUT);

        inTheArea.remove(WALKS_OUT);
        inTheArea.add(WALKS_IN);
        tick();

        assertThat(hitPerTick.get(1))
                .as("das ist der ganze Unterschied zwischen einem Ort und einer Liste von Mobs")
                .containsExactly(STANDS_STILL, WALKS_IN);
    }

    @Test
    @DisplayName("ein leerer Sturm hoert nicht auf zu regnen")
    void anemptyAreaKeepsRaining() {
        runner.startArea(storm, rain, CASTER, ANCHOR, 1, snapshot());

        tick();
        assertThat(hitPerTick).as("niemand da, also nichts angewandt").isEmpty();

        inTheArea.add(WALKS_IN);
        tick();

        assertThat(hitPerTick).hasSize(1);
        assertThat(hitPerTick.get(0)).containsExactly(WALKS_IN);
    }

    @Test
    @DisplayName("nach der Dauer hoert er auf, auch wenn noch jemand darin steht")
    void itstopsWhenTheDurationIsOver() {
        inTheArea.add(STANDS_STILL);
        runner.startArea(storm, rain, CASTER, ANCHOR, 1, snapshot());

        for (int second = 0; second < 6; second++) {
            tick();
        }
        int duringTheStorm = hitPerTick.size();

        tick();
        tick();

        assertThat(duringTheStorm).isEqualTo(6);
        assertThat(hitPerTick).as("sechs Sekunden sind sechs Sekunden").hasSize(6);
    }

    @Test
    @DisplayName("ein zweiter Wurf desselben Magiers verlaengert, er verdoppelt nicht")
    void asecondCastRefreshesRatherThanStacks() {
        inTheArea.add(STANDS_STILL);
        runner.startArea(storm, rain, CASTER, ANCHOR, 1, snapshot());
        runner.startArea(storm, rain, CASTER, ANCHOR, 1, snapshot());

        tick();

        assertThat(hitPerTick).as("ein Sturm, nicht zwei").hasSize(1);
    }

    /** Werte spielen hier keine Rolle: der Test misst, WER getroffen wird, nicht wie hart. */
    private static StatSnapshot snapshot() {
        return new StatSnapshot(new double[rpg.core.stats.Attribute.count()], 1L);
    }

    private void tick() {
        clock.advance(Duration.ofSeconds(1));
        runner.sweep();
    }

    private static Ability storm(EffectSpec effect) {
        return new Ability(
                "probe.storm",
                AbilityKind.ACTIVE,
                MessageKey.of("ability.probe.storm.name"),
                null,
                0.0,
                Duration.ZERO,
                Duration.ZERO,
                false,
                true,
                Duration.ZERO,
                1,
                null,
                false,
                false,
                false,
                false,
                java.util.Set.of(),
                1.0,
                new TargetSpec(TargetMode.GROUND_AREA, 16.0, null, 10, null, 5.0),
                List.of(effect),
                1,
                Map.of(),
                List.of("TRIDENT"));
    }

    /** Eine Uhr, die der Test vorstellt. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}

package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.ability.effect.IntervalEffectRunner;
import rpg.core.ability.effect.MeterEffect;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatSnapshot;

/**
 * T111a bis T111c und T111g - Intervall-Wirkung, Stapeln und Warriors Wut (FR-010a bis FR-010c,
 * FR-016b).
 *
 * <p><b>Die härteste Zusage steht in {@code OneSweepForAll}:</b> alle laufenden Intervall-Effekte
 * teilen sich <em>eine</em> Auswertung. Eine je Ziel wäre eine wiederkehrende Aufgabe je Entity, und
 * bei 800 Mobs wären das 800 - genau das, was Prinzip II ausschließt und weshalb Schaden über Zeit
 * beim ersten Mal abgelehnt und beim zweiten angenommen wurde.
 */
class IntervalAndMeterTest {

    private AbilityFixture fixture;
    private EffectDispatcher dispatcher;
    private IntervalEffectRunner runner;
    private final List<Double> applied = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
        Logger logger = Logger.getLogger(IntervalAndMeterTest.class.getName());
        logger.setLevel(Level.OFF);
        dispatcher = new EffectDispatcher(logger);
        dispatcher.register(EffectType.DAMAGE, context -> applied.add(context.value()));
        runner = new IntervalEffectRunner(dispatcher, fixture.clock);
    }

    @Nested
    @DisplayName("FR-010a - ein Effekt mit Intervall wirkt wiederholt")
    class Repeating {

        @Test
        @DisplayName("er wirkt einmal je Intervall, nicht einmal insgesamt")
        void itAppliesOncePerInterval() {
            start(poison(6, 1, 1, null), 5.0);

            // Vor dem ersten Intervall passiert nichts.
            assertThat(runner.sweep()).isZero();

            for (int second = 0; second < 6; second++) {
                fixture.clock.advance(Duration.ofSeconds(1));
                runner.sweep();
            }

            assertThat(applied).as("sechs Sekunden Dauer, ein Intervall je Sekunde").hasSize(6);
            assertThat(applied).allSatisfy(value -> assertThat(value).isEqualTo(5.0));
        }

        @Test
        @DisplayName("nach Ablauf der Dauer verschwindet er von selbst")
        void itEndsWithItsDuration() {
            start(poison(3, 1, 1, null), 5.0);
            for (int second = 0; second < 3; second++) {
                fixture.clock.advance(Duration.ofSeconds(1));
                runner.sweep();
            }
            assertThat(applied).hasSize(3);

            fixture.clock.advance(Duration.ofSeconds(5));
            runner.sweep();

            assertThat(applied).as("nichts mehr").hasSize(3);
            assertThat(runner.runningCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Der Weg dorthin - ein Effekt mit Intervall landet ueberhaupt im Durchlauf")
    class TheDispatcherHandsItOver {

        @Test
        @DisplayName("eine Faehigkeit mit Intervall wirkt nicht sofort, sondern startet eine Instanz")
        void aPeriodicEffectIsHandedToTheRunner() {
            dispatcher.setIntervalRunner(runner);
            UUID target = UUID.randomUUID();

            dispatcher.run(
                    withInterval(fixture.strike()),
                    fixture.character,
                    List.of(target),
                    1,
                    snapshot());

            // Der Fehler, den dieser Test festhaelt: der Runner wurde gebaut und gefegt, aber nie
            // gefuettert. Ohne die Uebergabe haette Gift einmal gewirkt statt sechs Sekunden lang -
            // und nichts haette es gemeldet, weil ein einmaliger Treffer wie ein Treffer aussieht.
            assertThat(applied).as("noch nichts - das erste Intervall ist nicht um").isEmpty();
            assertThat(runner.runningCount()).isEqualTo(1);

            fixture.clock.advance(Duration.ofSeconds(1));
            runner.sweep();

            assertThat(applied).hasSize(1);
        }

        @Test
        @DisplayName("ein Flaecheneffekt mit Intervall startet eine Instanz JE Ziel")
        void oneInstancePerTarget() {
            dispatcher.setIntervalRunner(runner);

            dispatcher.run(
                    withInterval(fixture.strike()),
                    fixture.character,
                    List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                    1,
                    snapshot());

            // Je Ziel eine, weil der Runner nach Ziel schluesselt - genau das laesst Stapeln
            // funktionieren. Eine gemeinsame Instanz koennte nicht sagen, wer wie oft vergiftet ist.
            assertThat(runner.runningCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("ohne Intervall geht alles den gewoehnlichen Weg")
        void withoutAnIntervalNothingChanges() {
            dispatcher.setIntervalRunner(runner);

            dispatcher.run(
                    fixture.strike(), fixture.character, List.of(UUID.randomUUID()), 1, snapshot());

            assertThat(applied).as("sofort, einmal").hasSize(1);
            assertThat(runner.runningCount()).isZero();
        }

        /** Dieselbe Faehigkeit, aber ihr Schadenseffekt traegt Dauer und Intervall. */
        private static Ability withInterval(Ability ability) {
            EffectSpec original = ability.effects().get(0);
            EffectSpec periodic =
                    new EffectSpec(
                            original.type(),
                            original.amount(),
                            original.perRank(),
                            Duration.ofSeconds(6),
                            Duration.ofSeconds(1),
                            1,
                            null,
                            original.attribute(),
                            original.damageType(),
                            original.statusEffect(),
                            original.buildPerHit(),
                            original.idleBefore(),
                            original.decayPerSecond(),
                            original.asFraction());
            return new Ability(
                    ability.id(),
                    ability.kind(),
                    ability.displayNameKey(),
                    ability.manaCost(),
                    ability.cooldown(),
                    ability.castTime(),
                    ability.sustained(),
                    ability.duration(),
                    ability.charges(),
                    ability.chargeWindow(),
                    ability.requiresBehindTarget(),
                    ability.openWorldOnly(),
                    ability.playerToggle(),
                    ability.interruptOnMove(),
                    ability.triggers(),
                    ability.chance(),
                    ability.target(),
                    List.of(periodic),
                    ability.maxRank(),
                    ability.rankCost(),
                    ability.items());
        }
    }

    @Nested
    @DisplayName("FR-010b - EINE Auswertung für alle")
    class OneSweepForAll {

        @Test
        @DisplayName("zweihundert laufende Effekte brauchen keine zweihundert Aufgaben")
        void twoHundredInstancesShareOneSweep() {
            for (int i = 0; i < 200; i++) {
                runner.start(
                        fixture.strike(),
                        poison(10, 1, 1, null),
                        fixture.character,
                        UUID.randomUUID(),
                        1,
                        snapshot());
            }
            assertThat(runner.runningCount()).isEqualTo(200);

            fixture.clock.advance(Duration.ofSeconds(1));
            int appliedNow = runner.sweep();

            assertThat(appliedNow).isEqualTo(200);
            // Der Beweis ist die Bauform: dieser Runner bekommt keinen Scheduler gereicht. Er KANN
            // keine Aufgabe erzeugen, weder eine noch zweihundert.
            assertThat(fixture.scheduling.scheduled)
                    .as("keine einzige geplante Aufgabe, egal wie viele Effekte laufen")
                    .isZero();
        }

        @Test
        @DisplayName("ohne laufenden Effekt ist ein Durchlauf ein leerer Scan")
        void anEmptySweepIsFree() {
            assertThat(runner.sweep()).isZero();
            assertThat(runner.runningCount()).isZero();
        }
    }

    @Nested
    @DisplayName("FR-010c - Stapeln, zweifach gedeckelt")
    class Stacking {

        @Test
        @DisplayName("zwei Stapel wirken doppelt")
        void twoStacksApplyTwice() {
            UUID target = UUID.randomUUID();
            EffectSpec spec = poison(10, 1, 3, 12.0);
            runner.start(fixture.strike(), spec, fixture.character, target, 1, snapshot());
            runner.start(fixture.strike(), spec, fixture.character, target, 1, snapshot());

            fixture.clock.advance(Duration.ofSeconds(1));
            runner.sweep();

            assertThat(runner.runningCount()).as("ein Effekt mit zwei Stapeln, nicht zwei Effekte").isEqualTo(1);
            assertThat(applied).containsExactly(10.0);
        }

        @Test
        @DisplayName("über der Höchstzahl erneuert ein Treffer die Laufzeit, erhöht aber nichts")
        void beyondTheMaximumOnlyTheDurationRefreshes() {
            UUID target = UUID.randomUUID();
            EffectSpec spec = poison(10, 1, 3, 100.0);
            for (int i = 0; i < 6; i++) {
                runner.start(fixture.strike(), spec, fixture.character, target, 1, snapshot());
            }

            fixture.clock.advance(Duration.ofSeconds(1));
            runner.sweep();

            assertThat(applied)
                    .as("drei Stapel à 5, nicht sechs - die Belohnung fürs Nachschlagen ist Dauer")
                    .containsExactly(15.0);
        }

        @Test
        @DisplayName("der Deckel begrenzt die Gesamtwirkung je Intervall")
        void theCapLimitsTheTotal() {
            UUID target = UUID.randomUUID();
            EffectSpec spec = poison(10, 1, 3, 12.0);
            for (int i = 0; i < 3; i++) {
                runner.start(fixture.strike(), spec, fixture.character, target, 1, snapshot());
            }

            fixture.clock.advance(Duration.ofSeconds(1));
            runner.sweep();

            assertThat(applied)
                    .as("drei mal fünf wären fünfzehn - der Deckel sagt zwölf")
                    .containsExactly(12.0);
        }

        @Test
        @DisplayName("ein vergessener Halter nimmt seine Effekte mit")
        void forgettingReleasesEverything() {
            UUID target = UUID.randomUUID();
            runner.start(fixture.strike(), poison(10, 1, 1, null), fixture.character, target, 1, snapshot());

            runner.forget(target);

            assertThat(runner.runningCount()).isZero();
        }
    }

    @Nested
    @DisplayName("T111g - Warriors Wut steigt, hält und fällt")
    class Meter {

        private MeterEffect meter;
        private EffectSpec spec;

        @BeforeEach
        void setUpMeter() {
            meter = new MeterEffect(fixture.stats, fixture.clock);
            // 20 je Treffer, 4 s Ruhefrist, dann 5 je Sekunde.
            spec =
                    new EffectSpec(
                            EffectType.METER, 30.0, 0.0, null, null, 1, null,
                            Attribute.PHYSICAL_DAMAGE, null, null, 20.0, Duration.ofSeconds(4), 5.0,
                            false);
        }

        @Test
        @DisplayName("er steigt je Treffer und klemmt bei 100")
        void itRisesAndClamps() {
            for (int i = 0; i < 3; i++) {
                hit();
            }
            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant()))
                    .isEqualTo(60.0);

            for (int i = 0; i < 10; i++) {
                hit();
            }
            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant()))
                    .as("100 ist das Ende")
                    .isEqualTo(EffectSpec.METER_MAXIMUM);
        }

        @Test
        @DisplayName("innerhalb der Ruhefrist fällt nichts")
        void nothingDecaysInsideTheGrace() {
            hit();
            fixture.clock.advance(Duration.ofSeconds(3));

            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant()))
                    .isEqualTo(20.0);
        }

        @Test
        @DisplayName("danach fällt er, und zwar bis null - nicht darunter")
        void afterwardsItDecaysToZero() {
            hit();
            hit();
            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant())).isEqualTo(40.0);

            // 4 s Frist plus 4 s Zerfall à 5 = 20 weg.
            fixture.clock.advance(Duration.ofSeconds(8));
            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant()))
                    .isCloseTo(20.0, within(0.001));

            fixture.clock.advance(Duration.ofMinutes(5));
            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant()))
                    .as("nie negativ")
                    .isZero();
        }

        @Test
        @DisplayName("er wird nirgends gespeichert - ein vergessener Charakter beginnt bei null")
        void itIsNotStored() {
            hit();
            meter.forget(fixture.character);

            assertThat(meter.valueAt(fixture.character, spec, fixture.clock.instant())).isZero();
            assertThat(meter.trackedCount()).isZero();
        }

        private void hit() {
            meter.apply(
                    new rpg.core.ability.effect.EffectContext(
                            fixture.strike(),
                            spec,
                            fixture.character,
                            List.of(fixture.character),
                            1,
                            snapshot()));
        }
    }

    // --- helpers ---

    private void start(EffectSpec spec, double expectedPerTick) {
        runner.start(fixture.strike(), spec, fixture.character, UUID.randomUUID(), 1, snapshot());
    }

    /** Ein Gift: Dauer in Sekunden, Intervall in Sekunden, Stapel, Deckel. */
    private static EffectSpec poison(int seconds, int intervalSeconds, int maxStacks, Double cap) {
        return new EffectSpec(
                EffectType.DAMAGE,
                5.0,
                0.0,
                Duration.ofSeconds(seconds),
                Duration.ofSeconds(intervalSeconds),
                maxStacks,
                cap,
                null,
                rpg.core.combat.DamageType.PHYSICAL,
                null,
                null,
                null,
                null,
                false);
    }

    private static StatSnapshot snapshot() {
        return new StatSnapshot(new double[Attribute.count()], 1L);
    }
}

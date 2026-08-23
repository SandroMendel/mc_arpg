package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T087 bis T089, T097a bis T097i - US4: Wirkzeit, haltende Fähigkeiten und Ladungen (ADR-025).
 *
 * <p><b>Die zweiphasige Abbruchregel ist der Kern.</b> Ein Abbruch in der Vorbereitung erstattet und
 * startet keinen Cooldown; das Beenden einer bereits laufenden Wirkung behält beides. Ohne diese
 * Trennung wäre Sofort-Abbrechen ein kostenloses Werkzeug: ein Wirbel liesse sich beliebig oft für
 * Sekundenbruchteile zünden.
 */
class CastAndSustainTest {

    private AbilityFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
        fixture.resolvedTargets = List.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("US4.5 und FR-044 - Wirkzeit null")
    class NoCastTime {

        @Test
        @DisplayName("US4.5: ohne Wirkzeit wirkt sie sofort und erzeugt keinen Zustand")
        void zeroCastTimeTakesEffectAtOnce() {
            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.TRIGGERED);
            assertThat(fixture.applications).hasSize(1);
            assertThat(fixture.runtime.running(fixture.character))
                    .as("kein Cast-Zustand, wo es nichts zu warten gibt")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("US4.1 bis US4.4 - die Wirkzeit")
    class Casting {

        @Test
        @DisplayName("US4.1: die Wirkung tritt erst beim Abschluss ein, der Cooldown erst dann")
        void theEffectFollowsTheCast() throws Exception {
            AbilityFixture casting = AbilityFixture.withCastTime();
            casting.resolvedTargets = List.of(UUID.randomUUID());

            assertThat(casting.runtime.trigger(casting.character, "probe.slow"))
                    .isEqualTo(AbilityResult.CASTING);
            assertThat(casting.applications).as("noch nichts gewirkt").isEmpty();
            assertThat(casting.registry.remainingCooldown(casting.character, "probe.slow"))
                    .as("und noch kein Cooldown")
                    .isEmpty();

            casting.scheduling.runPending();

            assertThat(casting.applications).hasSize(1);
            assertThat(casting.registry.remainingCooldown(casting.character, "probe.slow"))
                    .isPresent();
        }

        @Test
        @DisplayName("US4.4: eine zweite Auslösung während eines Casts wird abgewiesen")
        void aSecondTriggerDuringACastIsRefused() throws Exception {
            AbilityFixture casting = AbilityFixture.withCastTime();
            casting.runtime.trigger(casting.character, "probe.slow");

            assertThat(casting.runtime.trigger(casting.character, "probe.strike"))
                    .isEqualTo(AbilityResult.ALREADY_CASTING);
        }

        @Test
        @DisplayName("US4.2 und FR-045d: ein Abbruch in der Vorbereitung erstattet und startet nichts")
        void interruptingAWindUpCostsNothing() throws Exception {
            AbilityFixture casting = AbilityFixture.withCastTime();
            double before = casting.stats.mana;

            casting.runtime.trigger(casting.character, "probe.slow");
            assertThat(casting.stats.mana).as("beim Beginn gebucht").isLessThan(before);

            casting.runtime.end(casting.character, EndCause.DAMAGE_TAKEN);

            assertThat(casting.stats.mana).as("vollständig erstattet").isEqualTo(before);
            assertThat(casting.registry.remainingCooldown(casting.character, "probe.slow"))
                    .as("kein Cooldown - sie hat nicht stattgefunden")
                    .isEmpty();
            assertThat(casting.applications).isEmpty();
        }

        @Test
        @DisplayName("ein abgebrochener Cast wirkt auch dann nicht, wenn die Aufgabe noch feuert")
        void aCancelledCastDoesNotTakeEffectLater() throws Exception {
            AbilityFixture casting = AbilityFixture.withCastTime();
            casting.runtime.trigger(casting.character, "probe.slow");
            casting.runtime.end(casting.character, EndCause.SLOT_CHANGED);

            casting.scheduling.runPending();

            assertThat(casting.applications)
                    .as("der Zustand ist weg, und die Aufgabe findet nichts vor")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("FR-045a bis FR-045e - haltende Fähigkeiten")
    class Sustained {

        @Test
        @DisplayName("eine haltende Fähigkeit wirkt sofort und läuft weiter")
        void aSustainedAbilityRunsOn() throws Exception {
            AbilityFixture holding = AbilityFixture.withSustained();
            holding.resolvedTargets = List.of(UUID.randomUUID());

            assertThat(holding.runtime.trigger(holding.character, "probe.whirl"))
                    .isEqualTo(AbilityResult.SUSTAINING);
            assertThat(holding.applications).as("gewirkt hat sie sofort").hasSize(1);
            assertThat(holding.runtime.running(holding.character))
                    .hasValueSatisfying(
                            running ->
                                    assertThat(running.phase())
                                            .isEqualTo(RunningAbility.Phase.RUNNING));
            assertThat(holding.registry.remainingCooldown(holding.character, "probe.whirl"))
                    .as("der Cooldown beginnt erst am Ende")
                    .isEmpty();
        }

        @Test
        @DisplayName("FR-045e: das vorzeitige Beenden BEHÄLT die Kosten und startet den Cooldown")
        void endingARunningAbilityKeepsTheCost() throws Exception {
            AbilityFixture holding = AbilityFixture.withSustained();
            holding.runtime.trigger(holding.character, "probe.whirl");
            double afterTrigger = holding.stats.mana;

            holding.runtime.end(holding.character, EndCause.PLAYER);

            assertThat(holding.stats.mana)
                    .as("sonst waere Sofort-Abbrechen ein kostenloses Werkzeug")
                    .isEqualTo(afterTrigger);
            assertThat(holding.registry.remainingCooldown(holding.character, "probe.whirl"))
                    .isPresent();
            assertThat(holding.runtime.running(holding.character)).isEmpty();
        }

        @Test
        @DisplayName("FR-045b: eine zweite haltende Fähigkeit wird abgewiesen")
        void onlyOneSustainedAtATime() throws Exception {
            AbilityFixture holding = AbilityFixture.withSustained();
            holding.runtime.trigger(holding.character, "probe.whirl");

            assertThat(holding.runtime.trigger(holding.character, "probe.strike"))
                    .isEqualTo(AbilityResult.ALREADY_SUSTAINING);
        }

        @Test
        @DisplayName("läuft die Dauer ab, endet sie von selbst und der Cooldown beginnt")
        void itEndsOnItsOwn() throws Exception {
            AbilityFixture holding = AbilityFixture.withSustained();
            holding.runtime.trigger(holding.character, "probe.whirl");

            holding.scheduling.runPending();

            assertThat(holding.runtime.running(holding.character)).isEmpty();
            assertThat(holding.registry.remainingCooldown(holding.character, "probe.whirl"))
                    .isPresent();
        }

        @Test
        @DisplayName("FR-045f: ab der Wirkung gibt es keinen Weg zurueck in die Vorbereitung")
        void thereIsNoWayBack() throws Exception {
            AbilityFixture holding = AbilityFixture.withSustained();
            holding.runtime.trigger(holding.character, "probe.whirl");

            assertThat(holding.runtime.running(holding.character))
                    .hasValueSatisfying(running -> assertThat(running.isWindingUp()).isFalse());
        }
    }

    @Nested
    @DisplayName("FR-045i bis FR-045k - Ladungen")
    class Charges {

        @Test
        @DisplayName("zwei Ladungen: der Cooldown beginnt erst nach der zweiten")
        void theCooldownStartsAfterTheLastCharge() throws Exception {
            AbilityFixture charged = AbilityFixture.withCharges();

            assertThat(charged.runtime.chargesAvailable(charged.character, "probe.blink"))
                    .isEqualTo(2);

            charged.runtime.trigger(charged.character, "probe.blink");
            assertThat(charged.runtime.chargesAvailable(charged.character, "probe.blink"))
                    .isEqualTo(1);
            assertThat(charged.registry.remainingCooldown(charged.character, "probe.blink"))
                    .as("eine ist noch da - kein Cooldown")
                    .isEmpty();

            charged.clock.advance(Duration.ofSeconds(1)); // globale Sperre vorbei
            charged.runtime.trigger(charged.character, "probe.blink");

            assertThat(charged.runtime.chargesAvailable(charged.character, "probe.blink")).isZero();
            assertThat(charged.registry.remainingCooldown(charged.character, "probe.blink"))
                    .as("jetzt")
                    .isPresent();
        }

        @Test
        @DisplayName("FR-045j: ungenutzt springt der Vorrat nach dem Fenster zurueck, ohne Cooldown")
        void anUnusedPoolSpringsBack() throws Exception {
            AbilityFixture charged = AbilityFixture.withCharges();
            charged.runtime.trigger(charged.character, "probe.blink");
            assertThat(charged.runtime.chargesAvailable(charged.character, "probe.blink"))
                    .isEqualTo(1);

            // Zehn Sekunden Nachfüllfenster.
            charged.clock.advance(Duration.ofSeconds(11));

            assertThat(charged.runtime.chargesAvailable(charged.character, "probe.blink"))
                    .as("zurueck auf zwei, ohne dass ein Cooldown lief")
                    .isEqualTo(2);
            assertThat(charged.registry.remainingCooldown(charged.character, "probe.blink"))
                    .isEmpty();
        }

        @Test
        @DisplayName("ist der Vorrat leer und der Cooldown durch, geht es wieder")
        void afterTheCooldownItWorksAgain() throws Exception {
            AbilityFixture charged = AbilityFixture.withCharges();
            charged.runtime.trigger(charged.character, "probe.blink");
            charged.clock.advance(Duration.ofSeconds(1));
            charged.runtime.trigger(charged.character, "probe.blink");

            charged.clock.advance(Duration.ofSeconds(30));

            assertThat(charged.runtime.trigger(charged.character, "probe.blink"))
                    .isEqualTo(AbilityResult.TRIGGERED);
        }
    }

    @Nested
    @DisplayName("SC-009 - ein unterbrochener Cast hinterlässt nichts")
    class Interruption {

        private static final int ATTEMPTS = 1000;

        @Test
        @DisplayName("1000 unterbrochene Casts: keine Manadifferenz, kein Cooldown")
        void aThousandInterruptionsLeaveNothing() throws Exception {
            AbilityFixture casting = AbilityFixture.withCastTime();
            double before = casting.stats.mana;
            List<EndCause> causes =
                    List.of(
                            EndCause.DAMAGE_TAKEN,
                            EndCause.SLOT_CHANGED,
                            EndCause.MOVED,
                            EndCause.DIED,
                            EndCause.CHARACTER_SWITCHED,
                            EndCause.DISCONNECTED);

            for (int i = 0; i < ATTEMPTS; i++) {
                casting.runtime.trigger(casting.character, "probe.slow");
                casting.runtime.end(casting.character, causes.get(i % causes.size()));
                casting.clock.advance(Duration.ofSeconds(1));
            }

            assertThat(casting.stats.mana).isEqualTo(before);
            assertThat(casting.registry.remainingCooldown(casting.character, "probe.slow")).isEmpty();
            assertThat(casting.applications).isEmpty();
        }
    }

    @Nested
    @DisplayName("SC-005 - geplant wird nur, was laeuft")
    class Scheduling {

        @Test
        @DisplayName("eine Faehigkeit ohne Wirkzeit und ohne Dauer plant gar nichts")
        void nothingIsScheduledForAnInstantAbility() {
            for (int i = 0; i < 100; i++) {
                fixture.runtime.trigger(fixture.character, "probe.strike");
                fixture.clock.advance(Duration.ofSeconds(30));
            }

            assertThat(fixture.scheduling.scheduled)
                    .as("die Zahl der Aufgaben entspricht der Zahl der laufenden Faehigkeiten")
                    .isZero();
        }

        @Test
        @DisplayName("ein Cast plant genau eine Aufgabe, und sie verschwindet mit ihm")
        void aCastSchedulesExactlyOne() throws Exception {
            AbilityFixture casting = AbilityFixture.withCastTime();

            casting.runtime.trigger(casting.character, "probe.slow");

            assertThat(casting.scheduling.scheduled).isEqualTo(1);
            assertThat(casting.scheduling.pending()).isEqualTo(1);

            casting.runtime.end(casting.character, EndCause.DAMAGE_TAKEN);

            assertThat(casting.scheduling.cancelled).isEqualTo(1);
        }
    }
    @Nested
    @DisplayName("Die Haltung einer gehaltenen Faehigkeit - beide Enden")
    class Pose {

        @Test
        @DisplayName("sie beginnt beim Auslösen und endet, wenn die Dauer abgelaufen ist")
        void itStartsAndEndsWithTheDuration() throws Exception {
            // Genau das, was der Block des Warriors tun soll: X Sekunden halten und dann von allein
            // loslassen. Bis diese Naht existierte, gab es fuer das ZWEITE Ereignis keinen Melder -
            // der Vorrat lief ab, und der Spieler stand weiter da, als blockte er.
            AbilityFixture fixture = AbilityFixture.withSustained();
            List<String> pose = new ArrayList<>();
            fixture.runtime.setSustain(
                    new AbilityRuntime.Sustain() {
                        @Override
                        public void started(UUID characterId, Ability ability) {
                            pose.add("start:" + ability.id());
                        }

                        @Override
                        public void ended(UUID characterId, Ability ability) {
                            pose.add("end:" + ability.id());
                        }
                    });

            assertThat(fixture.runtime.trigger(fixture.character, "probe.whirl"))
                    .isEqualTo(AbilityResult.SUSTAINING);
            assertThat(pose).containsExactly("start:probe.whirl");

            fixture.runtime.expire(fixture.character);

            assertThat(pose).containsExactly("start:probe.whirl", "end:probe.whirl");
        }

        @Test
        @DisplayName("ein zweiter Rechtsklick beendet sie vorzeitig - und meldet es genauso")
        void aSecondClickEndsItEarly() throws Exception {
            AbilityFixture fixture = AbilityFixture.withSustained();
            List<String> pose = new ArrayList<>();
            fixture.runtime.setSustain(
                    new AbilityRuntime.Sustain() {
                        @Override
                        public void started(UUID characterId, Ability ability) {
                            pose.add("start");
                        }

                        @Override
                        public void ended(UUID characterId, Ability ability) {
                            pose.add("end");
                        }
                    });
            fixture.runtime.trigger(fixture.character, "probe.whirl");

            // Der zweite Klick. Beide Wege muessen melden, sonst bleibt die Haltung je nach Ausgang
            // stehen - und welcher Ausgang eintritt, entscheidet der Spieler.
            assertThat(fixture.runtime.end(fixture.character, EndCause.PLAYER))
                    .isEqualTo(AbilityResult.ENDED);

            assertThat(pose).containsExactly("start", "end");
        }
    }


}

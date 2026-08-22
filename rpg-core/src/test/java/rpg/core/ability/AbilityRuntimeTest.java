package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T038, T039 - US1: eine Fähigkeit auslösen, und was passiert, wenn sie abgewiesen wird.
 *
 * <p><b>Der zweite Teil jeder Zusage ist der wichtigere.</b> Dass eine Auslösung abgewiesen wird, ist
 * leicht; dass sie dabei <b>nichts</b> verbraucht — kein Mana, keinen Cooldown, keine globale Sperre —
 * ist die Eigenschaft, an der ein Spieler merkt, ob das System fair ist (FR-024, FR-025).
 */
class AbilityRuntimeTest {

    private AbilityFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
        fixture.resolvedTargets = List.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("US1.1 bis US1.4 - auslösen und abgewiesen werden")
    class Triggering {

        @Test
        @DisplayName("US1.1: die Auslösung wirkt, kostet Mana und startet den Cooldown")
        void aTriggerWorks() {
            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.TRIGGERED);
            assertThat(fixture.stats.mana).as("25 von 100").isEqualTo(75.0);
            assertThat(fixture.applications).hasSize(1);
            assertThat(fixture.registry.remainingCooldown(fixture.character, "probe.strike"))
                    .isPresent();
        }

        @Test
        @DisplayName("US1.2: unmittelbar danach wird abgewiesen - und die Restzeit ist ablesbar")
        void aSecondTriggerIsRefused() {
            fixture.runtime.trigger(fixture.character, "probe.strike");
            double manaAfterFirst = fixture.stats.mana;

            // Die globale Sperre greift zuerst, weil sie kürzer ist als der Cooldown - beides weist ab.
            fixture.clock.advance(Duration.ofSeconds(1));
            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.ON_COOLDOWN);
            assertThat(fixture.stats.mana).as("eine Abweisung kostet nichts").isEqualTo(manaAfterFirst);
            assertThat(fixture.registry.remainingCooldown(fixture.character, "probe.strike"))
                    .hasValueSatisfying(left -> assertThat(left).isLessThanOrEqualTo(Duration.ofSeconds(8)));
        }

        @Test
        @DisplayName("US1.3: zu wenig Mana weist ab, ohne Mana zu verbrauchen oder Cooldown zu starten")
        void tooLittleManaCostsNothing() {
            fixture.stats.mana = 10.0;

            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.NOT_ENOUGH_MANA);
            assertThat(fixture.stats.mana).isEqualTo(10.0);
            assertThat(fixture.registry.remainingCooldown(fixture.character, "probe.strike")).isEmpty();
            assertThat(fixture.registry.remainingGlobalLock(fixture.character))
                    .as("auch die globale Sperre bleibt unberührt")
                    .isEmpty();
            assertThat(fixture.applications).isEmpty();
        }

        @Test
        @DisplayName("US1.4: die globale Sperre weist eine ZWEITE Fähigkeit ab, ohne deren Cooldown zu starten")
        void theGlobalLockRefusesAnotherAbility() {
            fixture.runtime.trigger(fixture.character, "probe.strike");
            double manaAfterFirst = fixture.stats.mana;

            // Dieselbe Fähigkeit läge auf Cooldown; geprüft wird deshalb eine andere.
            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.dash");

            assertThat(result).isEqualTo(AbilityResult.GLOBAL_LOCK);
            assertThat(fixture.stats.mana).isEqualTo(manaAfterFirst);
            assertThat(fixture.registry.remainingCooldown(fixture.character, "probe.dash"))
                    .as("die abgewiesene Fähigkeit geht NICHT auf Cooldown")
                    .isEmpty();
        }

        @Test
        @DisplayName("nach Ablauf der globalen Sperre und des Cooldowns geht es wieder")
        void afterBothLocksItWorksAgain() {
            fixture.runtime.trigger(fixture.character, "probe.strike");
            fixture.stats.mana = 100.0;

            fixture.clock.advance(Duration.ofSeconds(10));

            assertThat(fixture.runtime.trigger(fixture.character, "probe.strike"))
                    .isEqualTo(AbilityResult.TRIGGERED);
        }

        @Test
        @DisplayName("US1.6: ohne aktiven Charakter wird abgewiesen, bevor irgendetwas berührt wird")
        void withoutACharacterNothingHappens() {
            fixture.characterClass = null;

            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.NO_CHARACTER);
            assertThat(fixture.stats.mana).isEqualTo(100.0);
            assertThat(fixture.applications).isEmpty();
        }

        @Test
        @DisplayName("eine noch nicht freigeschaltete Fähigkeit wird abgewiesen")
        void aLockedAbilityIsRefused() {
            fixture.unlocked.clear();

            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.NOT_UNLOCKED);
            assertThat(fixture.stats.mana).isEqualTo(100.0);
        }

        @Test
        @DisplayName("jede Ablehnung trägt einen Message-Schlüssel, jeder Erfolg keinen")
        void everyRejectionCarriesAKey() {
            for (AbilityResult result : AbilityResult.values()) {
                if (result.isSuccess()) {
                    assertThat(result.messageKey()).as("%s", result).isNull();
                } else {
                    assertThat(result.messageKey()).as("%s", result).isNotNull();
                }
            }
        }
    }

    @Nested
    @DisplayName("SC-003 - die drei Regeln werden serverseitig durchgesetzt")
    class ServerSideEnforcement {

        private static final int ATTEMPTS = 1000;

        @Test
        @DisplayName("1000 Versuche ohne Mana: null Durchbrüche, null Verbrauch")
        void noManaNeverGetsThrough() {
            fixture.stats.mana = 0.0;
            int through = 0;
            for (int i = 0; i < ATTEMPTS; i++) {
                if (fixture.runtime.trigger(fixture.character, "probe.strike").isSuccess()) {
                    through++;
                }
                fixture.clock.advance(Duration.ofSeconds(30));
            }

            assertThat(through).isZero();
            assertThat(fixture.stats.mana).isZero();
            assertThat(fixture.applications).isEmpty();
        }

        @Test
        @DisplayName("1000 Versuche auf Cooldown: null Durchbrüche")
        void aCooldownNeverGetsThrough() {
            fixture.runtime.trigger(fixture.character, "probe.strike");
            fixture.stats.mana = 100.0;
            fixture.clock.advance(Duration.ofSeconds(1)); // globale Sperre vorbei, Cooldown läuft

            int through = 0;
            for (int i = 0; i < ATTEMPTS; i++) {
                if (fixture.runtime.trigger(fixture.character, "probe.strike").isSuccess()) {
                    through++;
                }
            }

            assertThat(through).isZero();
            assertThat(fixture.stats.mana).as("nichts verbraucht").isEqualTo(100.0);
        }

        @Test
        @DisplayName("1000 Versuche in der globalen Sperre: null Durchbrüche")
        void theGlobalLockNeverGetsThrough() {
            fixture.runtime.trigger(fixture.character, "probe.strike");
            fixture.stats.mana = 100.0;

            int through = 0;
            for (int i = 0; i < ATTEMPTS; i++) {
                if (fixture.runtime.trigger(fixture.character, "probe.dash").isSuccess()) {
                    through++;
                }
            }

            assertThat(through).isZero();
            assertThat(fixture.stats.mana).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("Cooldown-Arithmetik")
    class Cooldowns {

        @Test
        @DisplayName("die Cooldown-Reduktion verkürzt, und bei 40 % ist Schluss (ADR-008)")
        void reductionIsCappedAtFortyPercent() {
            Ability strike = fixture.strike();

            fixture.stats.cooldownReduction = 0.0;
            assertThat(fixture.runtime.effectiveCooldown(fixture.character, strike))
                    .isEqualTo(Duration.ofMillis(9000));

            fixture.stats.cooldownReduction = 0.20;
            assertThat(fixture.runtime.effectiveCooldown(fixture.character, strike))
                    .isEqualTo(Duration.ofMillis(7200));

            // Über dem Cap wird gekappt, nicht durchgereicht - eine Konfigurationspanne darf keinen
            // Cooldown von null erzeugen.
            fixture.stats.cooldownReduction = 0.90;
            assertThat(fixture.runtime.effectiveCooldown(fixture.character, strike))
                    .isEqualTo(Duration.ofMillis(5400));
        }

        @Test
        @DisplayName("nichts wird heruntergezählt - der Cooldown ist ein Vergleich zweier Zeitstempel")
        void nothingCountsDown() {
            fixture.runtime.trigger(fixture.character, "probe.strike");

            // Ohne die Uhr zu bewegen bleibt die Restzeit gleich, egal wie oft gefragt wird.
            Duration first =
                    fixture.registry.remainingCooldown(fixture.character, "probe.strike").orElseThrow();
            for (int i = 0; i < 100; i++) {
                fixture.registry.remainingCooldown(fixture.character, "probe.strike");
            }
            Duration afterAsking =
                    fixture.registry.remainingCooldown(fixture.character, "probe.strike").orElseThrow();

            assertThat(afterAsking).isEqualTo(first);
        }

        @Test
        @DisplayName("FR-032: eine Auslösung markiert den Charakter, statt zu schreiben")
        void aTriggerOnlyMarks() {
            fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(fixture.repository.marks)
                    .as("genau eine Vormerkung, kein Datenbankzugriff je Spielereignis")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Effekte und ihre Fehlerbarriere")
    class Effects {

        @Test
        @DisplayName("die Effekte sehen die Ziele des Resolvers und den Rang des Charakters")
        void effectsSeeTargetsAndRank() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            fixture.resolvedTargets = List.of(first, second);
            fixture.registry.put(
                    fixture.registry.stateOf(fixture.character, "probe.strike").withRank(3));

            fixture.runtime.trigger(fixture.character, "probe.strike");

            AbilityFixture.Applied applied = fixture.applications.get(0);
            assertThat(applied.targets()).containsExactly(first, second);
            assertThat(applied.rank()).isEqualTo(3);
            assertThat(applied.value()).as("1.4 + 0.2 × 2").isEqualTo(1.4 + 0.4);
        }

        @Test
        @DisplayName("SC-010: eine Ausnahme im Effekt beendet weder die Auslösung noch die Sitzung")
        void anExceptionIsContained() {
            fixture.failWith = new IllegalStateException("probe");

            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result)
                    .as("die Fähigkeit hat ausgelöst; ein kaputter Effekt macht sie nicht ungültig")
                    .isEqualTo(AbilityResult.TRIGGERED);
            assertThat(fixture.stats.mana).as("bezahlt wurde trotzdem").isEqualTo(75.0);
        }

        @Test
        @DisplayName("kein Ziel gefunden kostet trotzdem - nur eine ABGEWIESENE Auslösung ist gratis")
        void findingNoTargetStillCosts() {
            fixture.resolvedTargets = List.of();

            AbilityResult result = fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(result).isEqualTo(AbilityResult.TRIGGERED);
            assertThat(fixture.stats.mana).isEqualTo(75.0);
            assertThat(fixture.registry.remainingCooldown(fixture.character, "probe.strike"))
                    .isPresent();
        }
    }
}

package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T125 und T126 - der Rang (FR-062 bis FR-065).
 *
 * <p>Eine Kurve, eine Obergrenze und eine Zuordnung. Die Kurve ist {@code amount + perRank × (r−1)}
 * und steht an <b>einer</b> Stelle: {@link EffectSpec#valueAtRank}. Ein zweiter Satz Definitionen je
 * Rang wäre die naheliegende Alternative gewesen und wäre bei fünf Rängen und achtzehn Fähigkeiten
 * neunzig Zahlenblöcke, von denen niemand mehr sagen kann, welcher stimmt.
 *
 * <p>Die Zuordnung ist die härtere Zusage: <b>der Rang gehört dem Charakter, nicht dem Konto</b>
 * (ADR-011). Sie ist bauartbedingt - der Zustand wird mit Charakter-ID und Fähigkeits-ID
 * geschlüsselt, und eine Spieler-ID kommt darin nirgends vor.
 */
class AbilityRankTest {

    private AbilityFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
    }

    @Nested
    @DisplayName("FR-063 - die Kurve")
    class TheCurve {

        @Test
        @DisplayName("Rang 1 ist der Grundwert, jeder weitere addiert den Zuwachs")
        void everyRankAddsTheGain() {
            EffectSpec spec = fixture.strike().effects().get(0);

            // amount 1.4, per-rank 0.2 aus der Fixtur.
            assertThat(spec.valueAtRank(1)).isEqualTo(1.4);
            assertThat(spec.valueAtRank(2)).isCloseTo(1.6, org.assertj.core.api.Assertions.within(1e-9));
            assertThat(spec.valueAtRank(5)).isCloseTo(2.2, org.assertj.core.api.Assertions.within(1e-9));
        }

        @Test
        @DisplayName("ein Rang unter 1 ist keine Eingabe, sondern ein Fehler")
        void rankBelowOneIsRejected() {
            EffectSpec spec = fixture.strike().effects().get(0);

            assertThatThrownBy(() -> spec.valueAtRank(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 1");
        }

        @Test
        @DisplayName("der Rang wirkt sich auf die tatsächliche Anwendung aus, nicht nur auf die Rechnung")
        void theRankReachesTheEffect() {
            fixture.resolvedTargets = List.of(UUID.randomUUID());
            fixture.runtime.trigger(fixture.character, "probe.strike");
            assertThat(fixture.applications.get(0).value()).isEqualTo(1.4);

            fixture.applications.clear();
            fixture.runtime.advanceRank(fixture.character, "probe.strike");
            fixture.clock.advance(Duration.ofSeconds(20));
            fixture.runtime.trigger(fixture.character, "probe.strike");

            assertThat(fixture.applications.get(0).rank()).isEqualTo(2);
            assertThat(fixture.applications.get(0).value())
                    .isCloseTo(1.6, org.assertj.core.api.Assertions.within(1e-9));
        }
    }

    @Nested
    @DisplayName("FR-062 - der Aufstieg und seine Decke")
    class Advancing {

        @Test
        @DisplayName("ein Aufstieg erhöht um genau eins")
        void oneStepAtATime() {
            assertThat(fixture.registry.rankOf(fixture.character, "probe.strike")).isEqualTo(1);

            assertThat(fixture.runtime.advanceRank(fixture.character, "probe.strike"))
                    .isEqualTo(RankResult.ADVANCED);

            assertThat(fixture.registry.rankOf(fixture.character, "probe.strike")).isEqualTo(2);
        }

        @Test
        @DisplayName("am Höchstrang passiert nichts mehr, und das wird gesagt")
        void theCeilingHolds() {
            for (int i = 0; i < 4; i++) {
                assertThat(fixture.runtime.advanceRank(fixture.character, "probe.strike"))
                        .isEqualTo(RankResult.ADVANCED);
            }
            assertThat(fixture.registry.rankOf(fixture.character, "probe.strike")).isEqualTo(5);

            assertThat(fixture.runtime.advanceRank(fixture.character, "probe.strike"))
                    .as("max-rank ist 5 - der sechste Versuch ändert nichts")
                    .isEqualTo(RankResult.AT_MAXIMUM);
            assertThat(fixture.registry.rankOf(fixture.character, "probe.strike")).isEqualTo(5);
        }

        @Test
        @DisplayName("was nicht freigeschaltet ist, hat keinen Rang zum Erhöhen")
        void aLockedAbilityHasNoRank() {
            fixture.unlocked.clear();

            assertThat(fixture.runtime.advanceRank(fixture.character, "probe.strike"))
                    .isEqualTo(RankResult.NOT_UNLOCKED);
        }

        @Test
        @DisplayName("jeder Aufstieg geht durch den Puffer, nicht in die Datenbank")
        void everyAdvanceGoesThroughTheBuffer() {
            fixture.runtime.advanceRank(fixture.character, "probe.strike");

            assertThat(fixture.repository.marks)
                    .as("Prinzip II: kein Datenbankzugriff je Spielereignis - nur eine Markierung")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("seit B08b bezahlt jemand dafür - und der Preis wurde nie geraten")
        void somebodyPaysForItNow() {
            // UMGEKEHRT statt gelöscht (ADR-027). Vorher stand hier die Zusicherung, dass es KEIN
            // NOT_ENOUGH_COINS gibt: eine Währung im Fähigkeitsblock zu erfinden hätte eine
            // Wirtschaft am falschen Ort angelegt, die ein späterer Block nicht mehr übernehmen
            // könnte (Workflow-Regel 5).
            //
            // Diese Überlegung war richtig, und ihr Ergebnis steht: der Preis kam aus dem Block, dem
            // Währung gehört, und dieses Enum ist dabei um genau einen Wert gewachsen. Die Zeile
            // umzudrehen statt sie zu löschen hält die Änderung im Diff sichtbar.
            assertThat(RankResult.values())
                    .extracting(Enum::name)
                    .contains("NOT_ENOUGH_COINS")
                    .as("und weiterhin keine zweite Schreibweise desselben Ausgangs")
                    .doesNotContain("TOO_EXPENSIVE");
        }

        @Test
        @DisplayName("ohne installierte Kostenprüfung kostet ein Rang weiterhin nichts")
        void withoutAnInstalledCostNothingIsCharged() {
            // Der Auslieferungszustand von B08 allein: RankCost.free(). Damit bleibt dieser Block
            // für sich genommen währungsfrei und ohne Abhängigkeit auf B08b.
            assertThat(fixture.runtime.advanceRank(fixture.character, "probe.strike"))
                    .isEqualTo(RankResult.ADVANCED);
        }
    }

    @Nested
    @DisplayName("ADR-011 - der Rang gehört dem Charakter")
    class PerCharacter {

        @Test
        @DisplayName("zwei Charaktere desselben Kontos steigen unabhängig auf")
        void twoCharactersOfOnePlayerAreIndependent() {
            UUID second = UUID.randomUUID();
            fixture.runtime.advanceRank(fixture.character, "probe.strike");
            fixture.runtime.advanceRank(fixture.character, "probe.strike");

            assertThat(fixture.registry.rankOf(fixture.character, "probe.strike")).isEqualTo(3);
            assertThat(fixture.registry.rankOf(second, "probe.strike"))
                    .as("der zweite Charakter hat davon nichts")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("der Zustand trägt keine Spieler-ID - die Zusage ist bauartbedingt")
        void thereIsNoPlayerIdInTheState() {
            AbilityState state = fixture.registry.stateOf(fixture.character, "probe.strike");

            // Der stärkste Beweis für "gehört dem Charakter" ist, dass es gar nichts anderes gibt,
            // woran es hängen könnte.
            assertThat(state.characterId()).isEqualTo(fixture.character);
            assertThat(AbilityState.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("playerId");
        }
    }
}

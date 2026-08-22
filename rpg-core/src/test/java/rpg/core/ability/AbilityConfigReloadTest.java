package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T130 und T131 - Balancing ohne Codeänderung (FR-008, SC-008, Prinzip V).
 *
 * <p>Die Zusage lautet: <b>jede Zahl dieses Blocks steht in der Konfiguration, und eine geänderte
 * Zahl gilt nach dem Neustart.</b> Geprüft wird sie, indem dieselbe Datei zweimal mit einem anderen
 * Wert gebunden wird - genau das, was ein Neustart tut, ohne dass ein Server dafür laufen muss.
 *
 * <p>Der Test geht durch alle acht Kategorien, nicht durch eine stellvertretend. Eine Kategorie, die
 * versehentlich im Code steht, fällt sonst genau dann auf, wenn ein Betreiber sie ändern will und
 * nichts passiert - und das ist der teuerste Zeitpunkt.
 *
 * <p>Die Gegenprobe steht in {@code AbilitySourceInvariantsTest}: hier steht, dass die Zahlen aus der
 * Datei kommen, dort, dass keine im Code steht. Erst beides zusammen ist die Zusage.
 */
class AbilityConfigReloadTest {

    @Nested
    @DisplayName("SC-008 - eine geänderte Zahl gilt nach dem Neustart")
    class EveryCategoryTakesEffect {

        @Test
        @DisplayName("Manakosten")
        void manaCost() throws Exception {
            assertThat(rebound(ability -> ability.put("mana-cost", 5.0)).manaCost()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("Cooldown - der Fall aus dem Steckbrief: halbieren, neu starten, es gilt")
        void cooldown() throws Exception {
            assertThat(shipped().cooldown()).isEqualTo(Duration.ofMillis(9000));

            assertThat(rebound(ability -> ability.put("cooldown-ms", 4500)).cooldown())
                    .isEqualTo(Duration.ofMillis(4500));
        }

        @Test
        @DisplayName("Wirkzeit")
        void castTime() throws Exception {
            assertThat(rebound(ability -> ability.put("cast-time-ms", 1200)).castTime())
                    .isEqualTo(Duration.ofMillis(1200));
        }

        @Test
        @DisplayName("Reichweite")
        void range() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").put("range", 12.0);

            assertThat(bind(document).target().range()).isEqualTo(12.0);
        }

        @Test
        @DisplayName("Obergrenze der Ziele")
        void maxTargets() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").put("max-targets", 3.0);

            assertThat(bind(document).target().maxTargets()).isEqualTo(3);
        }

        @Test
        @DisplayName("Rangkurve - Grundwert und Zuwachs getrennt")
        void rankCurve() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("amount", 2.0);
            effect.put("per-rank", 0.5);

            EffectSpec spec = bind(document).effects().get(0);

            assertThat(spec.valueAtRank(1)).isEqualTo(2.0);
            assertThat(spec.valueAtRank(3)).isEqualTo(3.0);
        }

        @Test
        @DisplayName("globale Sperre")
        void globalCooldown() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.runtimeOf(document).put("global-cooldown-ms", 1500);

            assertThat(AbilityConfigFixture.bind(document).globalCooldown())
                    .isEqualTo(Duration.ofMillis(1500));
        }

        @Test
        @DisplayName("Kampf-Faktoren")
        void combatFactors() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> regeneration = AbilityConfigFixture.regenerationOf(document);
            regeneration.put("health-combat-factor", 0.5);
            regeneration.put("mana-combat-factor", 0.6);

            AbilityConfig config = AbilityConfigFixture.bind(document);

            assertThat(config.healthCombatFactor()).isEqualTo(0.5);
            assertThat(config.manaCombatFactor()).isEqualTo(0.6);
        }
    }

    @Nested
    @DisplayName("SC-008 - eine kaputte Zahl verhindert den Start und sagt, welche")
    class ABadNumberStopsTheStart {

        @Test
        @DisplayName("die Meldung nennt die Fähigkeit, nicht nur das Feld")
        void theMessageNamesTheAbility() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("mana-cost", -1.0);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .as(
                            "eine Meldung ohne Fähigkeitsnamen liesse den Betreiber achtzehn"
                                    + " Definitionen durchsuchen")
                    .hasMessageContaining("probe.strike");
        }

        @Test
        @DisplayName("und sie nennt das Feld, nicht nur die Fähigkeit")
        void theMessageNamesTheField() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.runtimeOf(document).put("global-cooldown-ms", -1);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("global-cooldown-ms");
        }
    }

    // --- helpers ---

    private interface Change {
        void apply(Map<String, Object> ability);
    }

    private static Ability rebound(Change change) throws Exception {
        Map<String, Object> document = AbilityConfigFixture.valid();
        change.apply(AbilityConfigFixture.abilityOf(document, "probe.strike"));
        return bind(document);
    }

    private static Ability shipped() throws Exception {
        return bind(AbilityConfigFixture.valid());
    }

    private static Ability bind(Map<String, Object> document) throws Exception {
        return AbilityConfigFixture.bind(document).require("probe.strike");
    }

}

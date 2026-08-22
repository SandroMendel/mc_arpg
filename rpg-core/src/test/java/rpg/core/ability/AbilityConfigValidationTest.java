package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.classes.AbilityKind;

/**
 * T023 - die Zusagen V1 bis V24 und V31 bis V42 aus {@code contracts/ability-config.md}.
 *
 * <p>Jeder Fehlerfall prüft die <b>Meldung</b>. Ein Betreiber, der achtzehn Fähigkeiten über zwei
 * Dateien bearbeitet, muss erfahren, welche gemeint ist - „ungültige Konfiguration" schickt ihn durch
 * alle achtzehn. Derselbe Grundsatz wie in {@code ClassConfigValidationTest}.
 *
 * <p>Jeder Lauf geht durch {@code SchemaValidator} und dann den Binder, also genau den Weg des Laders.
 */
class AbilityConfigValidationTest {

    @Test
    @DisplayName("die gültige Konfiguration lädt: Laufzeitblock plus zwei Fähigkeiten")
    void validConfigurationLoads() throws Exception {
        AbilityConfig config = AbilityConfigFixture.bind(AbilityConfigFixture.valid());

        assertThat(config.size()).isEqualTo(2);
        assertThat(config.globalCooldown()).isEqualTo(Duration.ofMillis(750));
        assertThat(config.healthCombatFactor()).isEqualTo(0.20);
        assertThat(config.manaCombatFactor()).isEqualTo(0.35);
        assertThat(config.require("probe.strike").kind()).isEqualTo(AbilityKind.ACTIVE);
        assertThat(config.require("probe.lifesteal").kind()).isEqualTo(AbilityKind.PASSIVE);
    }

    @Nested
    @DisplayName("V1 bis V4 - Aufbau der Datei")
    class DocumentShape {

        @Test
        @DisplayName("V1: eine negative globale Sperre bricht ab")
        void negativeGlobalCooldownIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.runtimeOf(document).put("global-cooldown-ms", -1);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("global-cooldown-ms")
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("V2: ein Kampf-Faktor über 1 bricht ab und nennt den Wert")
        void combatFactorAboveOneIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.regenerationOf(document).put("mana-combat-factor", 1.5);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("mana-combat-factor")
                    .hasMessageContaining("1.5");
        }

        @Test
        @DisplayName("V4: ein fehlendes Pflichtfeld nennt Fähigkeit und Feld")
        void missingFieldNamesAbilityAndField() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").remove("display-name-key");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("abilities.probe.strike.display-name-key")
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("V5: eine unbekannte Art nennt die erlaubten Werte")
        void unknownKindListsThePermittedValues() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("kind", "TOGGLE");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("unknown AbilityKind 'TOGGLE'")
                    .hasMessageContaining("ACTIVE");
        }
    }

    @Nested
    @DisplayName("V6 bis V13 - Definition einer Fähigkeit")
    class AbilityShape {

        @Test
        @DisplayName("V6: eine aktive Fähigkeit ohne Item bricht ab")
        void activeWithoutItemIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").remove("item");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("probe.strike")
                    .hasMessageContaining("needs exactly one item");
        }

        @Test
        @DisplayName("V6: eine aktive Fähigkeit mit ZWEI Items bricht ab - das wären zwei Slots")
        void activeWithTwoItemsIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            // Mehrere Items sind erlaubt, aber nur als Marker einer passiven Fähigkeit. Bei einer
            // aktiven wäre jedes ein anklickbarer Slot, und zwei Slots für eine Fähigkeit hiesse:
            // zwei Wege, dasselbe auszulösen, und einer davon ist irgendwann falsch belegt.
            AbilityConfigFixture.abilityOf(document, "probe.strike")
                    .put("item", new java.util.ArrayList<>(java.util.List.of("IRON_AXE", "STICK")));

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("probe.strike")
                    .hasMessageContaining("needs exactly one item");
        }

        @Test
        @DisplayName("eine passive Fähigkeit DARF mehrere Marker tragen - Aufstieg & Fall trägt zwei")
        void aPassiveMayCarrySeveralMarkers() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.lifesteal")
                    .put("item", new java.util.ArrayList<>(java.util.List.of("WIND_CHARGE", "POTION")));

            assertThat(AbilityConfigFixture.bind(document).require("probe.lifesteal").items())
                    .containsExactly("WIND_CHARGE", "POTION");
        }

        @Test
        @DisplayName("eine passive Fähigkeit DARF mehrere Trigger tragen - Wut baut auf beiden auf")
        void aPassiveMayNameSeveralTriggers() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.lifesteal")
                    .put(
                            "trigger",
                            new java.util.ArrayList<>(
                                    java.util.List.of("ON_DAMAGE_DEALT", "ON_DAMAGE_TAKEN")));

            Ability built = AbilityConfigFixture.bind(document).require("probe.lifesteal");

            assertThat(built.firesOn(AbilityTrigger.ON_DAMAGE_DEALT)).isTrue();
            assertThat(built.firesOn(AbilityTrigger.ON_DAMAGE_TAKEN)).isTrue();
            assertThat(built.firesOn(AbilityTrigger.ON_KILL)).isFalse();
        }

        @Test
        @DisplayName("eine leere Trigger-Liste bricht ab - sie liest sich wie eine Entscheidung")
        void anEmptyTriggerListIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.lifesteal")
                    .put("trigger", new java.util.ArrayList<>());

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("empty list");
        }

        @Test
        @DisplayName("V6: eine passive Fähigkeit ohne Trigger bricht ab")
        void passiveWithoutTriggerIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.lifesteal").remove("trigger");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("probe.lifesteal")
                    .hasMessageContaining("needs a trigger");
        }

        @Test
        @DisplayName("V7: Mana auf einer Passiven bricht ab, statt ignoriert zu werden")
        void manaOnAPassiveIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.lifesteal").put("mana-cost", 10.0);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("passive ability costs no mana");
        }

        @Test
        @DisplayName("V7: ein Trigger auf einer Aktiven bricht ab")
        void triggerOnAnActiveIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("trigger", "ON_KILL");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("has no trigger");
        }

        @Test
        @DisplayName("V8: negative Kosten brechen ab")
        void negativeManaCostIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("mana-cost", -5.0);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("mana-cost")
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("V9: eine Wahrscheinlichkeit über 1 bricht ab")
        void chanceAboveOneIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.lifesteal").put("chance", 1.2);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("chance must lie within [0, 1]");
        }

        @Test
        @DisplayName("V10: max-rank unter 1 bricht ab")
        void maxRankBelowOneIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("max-rank", 0);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("max-rank must be at least 1");
        }

        @Test
        @DisplayName("V11: ein leerer Anzeigeschlüssel bricht ab")
        void blankDisplayKeyIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("display-name-key", "  ");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("display-name-key")
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("V13: eine Fähigkeit ohne Effekt bricht ab")
        void noEffectsIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("effects", List.of());

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("needs at least one effect");
        }
    }

    @Nested
    @DisplayName("V14 bis V19 und V37 bis V42 - Effekte")
    class Effects {

        @Test
        @DisplayName("V14: ein unbekanntes Primitive nennt Fähigkeit und Vorrat")
        void unknownPrimitiveIsNamed() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.effectOf(document, "probe.strike", 0).put("type", "MIND_CONTROL");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("unknown EffectType 'MIND_CONTROL'")
                    .hasMessageContaining("LIFESTEAL");
        }

        @Test
        @DisplayName("V15: ein negativer Rangzuwachs bricht ab - ein Aufstieg nimmt nichts weg")
        void negativePerRankIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.effectOf(document, "probe.strike", 0).put("per-rank", -0.1);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("per-rank must not be negative");
        }

        @Test
        @DisplayName("V16: BUFF ohne Attribut bricht ab")
        void buffWithoutAttributeIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("type", "BUFF");
            effect.remove("damage-type");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("BUFF needs an attribute");
        }

        @Test
        @DisplayName("V16: ein unbekanntes Attribut nennt den Pfad")
        void unknownAttributeNamesThePath() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("type", "BUFF");
            effect.remove("damage-type");
            effect.put("attribute", "critChance");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("abilities.probe.strike.effects[0].attribute")
                    .hasMessageContaining("critChance");
        }

        @Test
        @DisplayName("V17: DAMAGE ohne Schadenstyp bricht ab")
        void damageWithoutTypeIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.effectOf(document, "probe.strike", 0).remove("damage-type");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("DAMAGE needs a damage-type");
        }

        @Test
        @DisplayName("V37: ein Intervall ohne Dauer bricht ab - es endete nie")
        void intervalWithoutDurationIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.effectOf(document, "probe.strike", 0).put("interval-ms", 1000);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("an interval needs a duration");
        }

        @Test
        @DisplayName("V38: ein Intervall länger als die Dauer bricht ab - es wirkte kein einziges Mal")
        void intervalLongerThanDurationIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("duration-ms", 1000);
            effect.put("interval-ms", 3000);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("exceeds duration")
                    .hasMessageContaining("never apply once");
        }

        @Test
        @DisplayName("V39: Stapeln ohne Intervall bricht ab")
        void stackingWithoutIntervalIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.effectOf(document, "probe.strike", 0).put("max-stacks", 3);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("max-stacks above 1 needs an interval");
        }

        @Test
        @DisplayName("V40: Stapeln ohne Deckel bricht ab - die Vergiftete Klinge wüchse unbegrenzt")
        void stackingWithoutCapIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("duration-ms", 6000);
            effect.put("interval-ms", 1000);
            effect.put("max-stacks", 3);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("stacking needs a stack-cap");
        }

        @Test
        @DisplayName("V41: ein Schadenstyp auf einem Primitive ohne Filter bricht ab")
        void damageTypeWhereItMeansNothingIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("type", "HEAL");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("it means nothing here");
        }

        @Test
        @DisplayName("V42: ein METER ohne Zerfall bricht ab - ein Zähler ohne Zerfall ist keiner")
        void meterWithoutDecayIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("type", "METER");
            effect.remove("damage-type");
            effect.put("attribute", "physicalDamage");
            effect.put("build-per-hit", 6.0);
            effect.put("idle-before-ms", 4000);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("METER needs a positive decay-per-second");
        }

        @Test
        @DisplayName("ein vollständiges METER lädt")
        void aCompleteMeterLoads() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> effect = AbilityConfigFixture.effectOf(document, "probe.strike", 0);
            effect.put("type", "METER");
            effect.remove("damage-type");
            effect.put("attribute", "physicalDamage");
            effect.put("build-per-hit", 6.0);
            effect.put("idle-before-ms", 4000);
            effect.put("decay-per-second", 5.0);

            AbilityConfig config = AbilityConfigFixture.bind(document);

            EffectSpec meter = config.require("probe.strike").effects().get(0);
            assertThat(meter.type()).isEqualTo(EffectType.METER);
            assertThat(meter.decayPerSecond()).isEqualTo(5.0);
        }
    }

    @Nested
    @DisplayName("V20 bis V24 - Zielbestimmung")
    class Targeting {

        @Test
        @DisplayName("V20: ein unbekannter Modus nennt die erlaubten Werte")
        void unknownModeListsThePermittedValues() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").put("mode", "EVERYONE");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("unknown TargetMode 'EVERYONE'")
                    .hasMessageContaining("GROUND_AREA");
        }

        @Test
        @DisplayName("V21: ein Modus ohne Reichweite bricht ab")
        void missingRangeIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").remove("range");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("range must be greater than zero");
        }

        @Test
        @DisplayName("V22: CONE ohne Winkel bricht ab")
        void coneWithoutAngleIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").put("mode", "CONE");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("CONE needs an angle within (0, 180]");
        }

        @Test
        @DisplayName("V23: ein Mehrfachmodus ohne Obergrenze bricht ab - kein Vorgabewert")
        void multiTargetWithoutCapIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").remove("max-targets");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("max-targets is required")
                    .hasMessageContaining("blows the tick budget");
        }

        @Test
        @DisplayName("V24: eine Obergrenze auf SELF bricht ab")
        void capOnASingleTargetModeIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> target = AbilityConfigFixture.targetOf(document, "probe.lifesteal");
            target.put("max-targets", 5);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("max-targets must be 1");
        }

        @Test
        @DisplayName("CHAIN ohne hop-range bricht ab - es sucht um das zuletzt getroffene Ziel")
        void chainWithoutHopRangeIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").put("mode", "CHAIN");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("CHAIN needs a positive hop-range");
        }

        @Test
        @DisplayName("GROUND_AREA ohne area-radius bricht ab")
        void groundAreaWithoutRadiusIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.targetOf(document, "probe.strike").put("mode", "GROUND_AREA");

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("GROUND_AREA needs a positive area-radius");
        }
    }

    @Nested
    @DisplayName("V31 bis V36 - haltende Fähigkeiten, Ladungen, Bedingungen")
    class SustainedAndCharges {

        @Test
        @DisplayName("V31: eine haltende Fähigkeit ohne Dauer bricht ab")
        void sustainedWithoutDurationIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("sustained", true);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("sustained ability needs a positive duration");
        }

        @Test
        @DisplayName("V32: eine haltende Passive bricht ab - sie hat keinen Slot zum Beenden")
        void sustainedPassiveIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> ability = AbilityConfigFixture.abilityOf(document, "probe.lifesteal");
            ability.put("sustained", true);
            ability.put("duration-ms", 5000);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("only an active ability can be sustained");
        }

        @Test
        @DisplayName("V33: zwei Ladungen ohne Nachfüllfenster brechen ab")
        void chargesWithoutWindowAreRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("charges", 2);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("needs a positive charge-window");
        }

        @Test
        @DisplayName("V34: die Positionsbedingung ohne ON_DAMAGE_DEALT bricht ab")
        void behindTargetOutsideDamageDealtIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> ability = AbilityConfigFixture.abilityOf(document, "probe.lifesteal");
            ability.put("trigger", "ON_KILL");
            ability.put("requires-behind-target", true);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("only works with ON_DAMAGE_DEALT");
        }

        @Test
        @DisplayName("V35: eine abschaltbare Aktive bricht ab")
        void toggleOnAnActiveIsRejected() {
            Map<String, Object> document = AbilityConfigFixture.valid();
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("player-toggle", true);

            assertThatThrownBy(() -> AbilityConfigFixture.bind(document))
                    .hasMessageContaining("only a passive ability can be toggled");
        }

        @Test
        @DisplayName("eine vollständige haltende Fähigkeit mit zwei Ladungen lädt")
        void aCompleteSustainedAbilityWithChargesLoads() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            Map<String, Object> ability = AbilityConfigFixture.abilityOf(document, "probe.strike");
            ability.put("sustained", true);
            ability.put("duration-ms", 5000);
            ability.put("charges", 2);
            ability.put("charge-window-ms", 10000);

            Ability loaded = AbilityConfigFixture.bind(document).require("probe.strike");

            assertThat(loaded.sustained()).isTrue();
            assertThat(loaded.duration()).isEqualTo(Duration.ofSeconds(5));
            assertThat(loaded.charges()).isEqualTo(2);
            assertThat(loaded.chargeWindow()).isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Nested
    @DisplayName("Auskunft und Rangkurve")
    class Lookup {

        @Test
        @DisplayName("eine unbekannte ID nennt die vorhandenen")
        void unknownIdListsTheKnownOnes() throws Exception {
            AbilityConfig config = AbilityConfigFixture.bind(AbilityConfigFixture.valid());

            assertThatThrownBy(() -> config.require("probe.nonexistent"))
                    .isInstanceOf(UnknownAbilityException.class)
                    .hasMessageContaining("probe.nonexistent")
                    .hasMessageContaining("probe.strike");
            assertThat(config.find("probe.nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("der Rang skaliert linear: amount + perRank × (rang − 1)")
        void rankScalesLinearly() throws Exception {
            AbilityConfig config = AbilityConfigFixture.bind(AbilityConfigFixture.valid());
            EffectSpec damage = config.require("probe.strike").effects().get(0);

            assertThat(damage.valueAtRank(1)).isEqualTo(1.4);
            assertThat(damage.valueAtRank(3)).isEqualTo(1.4 + 0.2 * 2);
            assertThatThrownBy(() -> damage.valueAtRank(0))
                    .hasMessageContaining("rank must be at least 1");
        }
    }
}

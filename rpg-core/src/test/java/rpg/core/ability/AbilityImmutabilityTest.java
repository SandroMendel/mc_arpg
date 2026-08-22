package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T138 - {@link Ability}, {@link EffectSpec} und {@link TargetSpec} sind unveränderlich.
 *
 * <p>Sie werden einmal beim Start gebaut und danach von jedem Thread gelesen, den Paper hat: dem
 * Haupttick, den Entity-Schedulern, dem Schreib-Zyklus. Kein Schloss schützt sie, und keines sollte -
 * eine unveränderliche Definition braucht keines.
 *
 * <p><b>Die interessante Zusage ist das Kopieren, nicht das Kopiert-Sein.</b> Ein Record mit einem
 * {@code List}-Feld ist nur so unveränderlich wie die Liste, die man ihm gibt; wer die
 * übergebene Liste behält, gibt dem Aufrufer ein Schreibrecht auf den Zustand des Servers, ohne dass
 * irgendwo etwas danach aussieht.
 */
class AbilityImmutabilityTest {

    @Nested
    @DisplayName("Die Bauform")
    class TheShape {

        @Test
        @DisplayName("alle drei sind Records - also final und mit finalen Feldern")
        void allThreeAreRecords() {
            for (Class<?> type : List.of(Ability.class, EffectSpec.class, TargetSpec.class)) {
                assertThat(type.isRecord()).as("%s ist ein Record", type.getSimpleName()).isTrue();
                assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
            }
        }

        @Test
        @DisplayName("kein Feld ist ein veränderbarer Sammlungstyp, den jemand von aussen hält")
        void noComponentIsAMutableCollectionType() {
            // ArrayList als Komponententyp wäre die Bauform, die das Kopieren umgeht: der Aufrufer
            // hätte dann eine Referenz auf genau das Objekt, das im Record steckt.
            List<String> suspects = new ArrayList<>();
            for (Class<?> type : List.of(Ability.class, EffectSpec.class, TargetSpec.class)) {
                for (RecordComponent component : type.getRecordComponents()) {
                    Class<?> componentType = component.getType();
                    if (componentType == ArrayList.class
                            || componentType == java.util.HashSet.class
                            || componentType == java.util.HashMap.class) {
                        suspects.add(type.getSimpleName() + "." + component.getName());
                    }
                }
            }
            assertThat(suspects).isEmpty();
        }
    }

    @Nested
    @DisplayName("Das Kopieren - die Zusage, die man nicht sieht")
    class Copying {

        @Test
        @DisplayName("die Effektliste wird kopiert: eine spätere Änderung von aussen kommt nicht an")
        void theEffectListIsCopied() throws Exception {
            Ability ability = strike();
            int before = ability.effects().size();

            assertThatThrownBy(() -> ability.effects().clear())
                    .as("die gehaltene Liste ist selbst unveränderlich")
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(ability.effects()).hasSize(before);
        }

        @Test
        @DisplayName("die Trigger werden kopiert")
        void theTriggersAreCopied() throws Exception {
            Ability passive = passive();

            assertThatThrownBy(() -> passive.triggers().add(AbilityTrigger.ON_KILL))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("die Items werden kopiert")
        void theItemsAreCopied() throws Exception {
            Ability ability = strike();

            assertThatThrownBy(() -> ability.items().add("STICK"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("wer die übergebene Liste hinterher ändert, ändert die Fähigkeit nicht")
        void changingTheSourceListAfterwardsChangesNothing() throws Exception {
            Map<String, Object> document = AbilityConfigFixture.valid();
            List<Object> effects =
                    new ArrayList<>(
                            List.of(
                                    AbilityConfigFixture.damageEffect(),
                                    AbilityConfigFixture.damageEffect()));
            AbilityConfigFixture.abilityOf(document, "probe.strike").put("effects", effects);

            Ability ability = AbilityConfigFixture.bind(document).require("probe.strike");
            assertThat(ability.effects()).hasSize(2);

            // Der eigentliche Fall: dieselbe Liste wird nach dem Binden geleert. Ohne Kopie hätte die
            // gebaute Fähigkeit ab jetzt keine Wirkung mehr - und niemand hätte sie angefasst.
            effects.clear();

            assertThat(ability.effects()).as("die Fähigkeit behält, was sie beim Bauen hatte").hasSize(2);
        }
    }

    @Nested
    @DisplayName("Der Zustand ist das Gegenstück - er ändert sich, aber nur durch Ersetzen")
    class TheStateChangesByReplacement {

        @Test
        @DisplayName("AbilityState ist ebenfalls ein Record; jede Änderung gibt ein neues zurück")
        void everyChangeReturnsANewOne() {
            AbilityState state =
                    AbilityState.initial(java.util.UUID.randomUUID(), "probe.strike");

            AbilityState raised = state.withRank(3);

            assertThat(state.rank()).as("das alte bleibt, wie es war").isEqualTo(1);
            assertThat(raised.rank()).isEqualTo(3);
            assertThat(raised).isNotSameAs(state);
        }
    }

    // --- helpers ---

    private static Ability strike() throws Exception {
        return AbilityConfigFixture.bind(AbilityConfigFixture.valid()).require("probe.strike");
    }

    private static Ability passive() throws Exception {
        return AbilityConfigFixture.bind(AbilityConfigFixture.valid()).require("probe.lifesteal");
    }
}

package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;

/**
 * {@link UiMessageKeys#all()} muss <b>jeden</b> deklarierten Schlüssel enthalten und keinen doppelt.
 *
 * <h2>Warum der Test über Reflection geht und nicht über eine zweite Liste</h2>
 *
 * <p>Eine von Hand gepflegte Erwartungsliste hätte denselben Fehler wie die geprüfte Liste: wer eine
 * Konstante hinzufügt und {@code all()} vergisst, vergisst auch die Erwartung. Der Test liest
 * deshalb die <b>Felder der Klasse</b> — er kann nur grün bleiben, wenn {@code all()} mit der Klasse
 * mitwächst.
 *
 * <p>Der Preis eines vergessenen Schlüssels ist konkret: die Startprüfung geht {@code all()} durch,
 * also fiele der fehlende Text erst dem Spieler auf, und zwar als roher Schlüsselname mitten auf dem
 * Bildschirm (FR-019).
 */
class UiMessageKeysTest {

    @Test
    @DisplayName("all() enthaelt jede deklarierte Konstante")
    void allContainsEveryDeclaredConstant() throws Exception {
        List<MessageKey> declared = declaredConstants();
        assertThat(declared).isNotEmpty();

        assertThat(UiMessageKeys.all())
                .as("eine Konstante, die all() nicht kennt, faellt erst dem Spieler auf")
                .containsAll(declared);
    }

    @Test
    @DisplayName("all() enthaelt keinen Schluessel doppelt")
    void allHasNoDuplicates() {
        List<MessageKey> keys = UiMessageKeys.all();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("jedes Attribut aus B04 hat ein Etikett - heute zehn, aber die Zahl steht nirgends")
    void everyAttributeHasALabel() {
        // Die Zahl steht bewusst weder hier noch im Produktivcode: die Spec nannte urspruenglich
        // acht, es sind zehn, und der Wert stammte aus einem Roadmap-Ziel statt aus einer
        // Aufzaehlung. Der Test prueft die Quelle, nicht die Erinnerung an sie.
        for (Attribute attribute : Attribute.values()) {
            assertThat(UiMessageKeys.all())
                    .as("Etikett fuer " + attribute)
                    .contains(UiMessageKeys.attributeLabel(attribute));
        }
    }

    @Test
    @DisplayName("die Attributetiketten tragen Bindestriche, keine Unterstriche")
    void attributeLabelsUseHyphens() {
        // MessageKey laesst nur kleingeschriebene, mit Bindestrich getrennte Segmente zu. Derselbe
        // Stolper hat in B11 die Attributnamen und in B12 die Ranglistennamen erwischt - er wirft
        // erst zur Laufzeit, also beim Start und nicht beim Uebersetzen.
        for (Attribute attribute : Attribute.values()) {
            assertThat(UiMessageKeys.attributeLabel(attribute).value()).doesNotContain("_");
        }
    }

    @Test
    @DisplayName("jeder Schluessel dieses Blocks beginnt mit ui.")
    void everyKeyIsNamespaced() {
        // Ohne gemeinsamen Praefix laesst sich in einer Sprachdatei nicht sehen, welcher Block einen
        // Text besitzt - und ein Uebersetzer sucht ihn dann in acht Abschnitten.
        assertThat(UiMessageKeys.all()).allSatisfy(key -> assertThat(key.value()).startsWith("ui."));
    }

    private static List<MessageKey> declaredConstants() throws Exception {
        List<MessageKey> constants = new ArrayList<>();
        for (Field field : UiMessageKeys.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())
                    && field.getType() == MessageKey.class) {
                constants.add((MessageKey) field.get(null));
            }
        }
        return constants;
    }
}

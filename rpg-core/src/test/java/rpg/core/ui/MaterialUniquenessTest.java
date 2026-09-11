package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.ui.MaterialUniqueness.SlotUse;

/**
 * Die Startprüfung aus FR-032: keine zwei Fähigkeiten <b>derselben Klasse</b> auf demselben
 * Material.
 *
 * <p>Sie sichert eine Zusage über die <em>Anzeige</em>, die niemand sonst prüft: das
 * Vanilla-Cooldown-Overlay hängt am <b>Material</b> und nicht am Slot. Zwei Fähigkeiten auf einem
 * Item wären beide grau, obwohl nur eine läuft — und der Spieler drückt eine bereite Fähigkeit
 * nicht, weil sie gesperrt aussieht.
 *
 * <p>Deckt T090, T091 und T092 ab.
 *
 * <h2>Warum der Test mit {@link SlotUse} arbeitet und nicht mit {@code Ability}</h2>
 *
 * <p>{@code Ability} hat <b>dreiundzwanzig</b> Komponenten und keinen Builder — und der Konstruktor
 * erzwingt zu Recht einiges: eine aktive Fähigkeit braucht genau ein Item und keinen Trigger, eine
 * passive einen Trigger und weder Manakosten noch Zauberzeit, jede mindestens einen Effekt. Für
 * <em>diese</em> Frage zählt davon nichts.
 *
 * <p>Der erste Entwurf ließ die Prüfung auf ganzen Fähigkeiten laufen. Der Test hätte dann für
 * jeden Fall eine vollständige Fähigkeit bauen müssen — und genau das war der Beleg, dass die
 * Abhängigkeit zu groß ist. {@code MaterialUniqueness.verifyAbilities} ist die Brücke für die
 * Verdrahtung; hier steht, was die Regel wirklich braucht.
 */
class MaterialUniquenessTest {

    @Test
    @DisplayName("T090: zwei Faehigkeiten einer Klasse auf demselben Material brechen ab")
    void twoAbilitiesOnTheSameMaterialAbort() {
        assertThatThrownBy(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.WARRIOR
                                                        ? List.of(
                                                                slot("cleave", "IRON_SWORD"),
                                                                slot("bash", "IRON_SWORD"))
                                                        : List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WARRIOR")
                .hasMessageContaining("cleave")
                .hasMessageContaining("bash")
                .hasMessageContaining("IRON_SWORD");
    }

    @Test
    @DisplayName("T090: die Meldung nennt Klasse, BEIDE Faehigkeiten und das Material")
    void themessageNamesEverythingNeededToFixIt() {
        // Ohne das Material muesste der Betreiber die Datei durchsuchen; ohne die zweite Faehigkeit
        // wuesste er nicht, welche der beiden er aendern soll. Beides an dem Tag, an dem der Server
        // nicht startet.
        assertThatThrownBy(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.MAGE
                                                        ? List.of(
                                                                slot("frostbolt", "BLAZE_ROD"),
                                                                slot("ignite", "BLAZE_ROD"))
                                                        : List.of()))
                .hasMessageContaining("abilities.yml")
                .hasMessageContaining("MAGE")
                .hasMessageContaining("frostbolt")
                .hasMessageContaining("ignite")
                .hasMessageContaining("BLAZE_ROD")
                .hasMessageContaining("FR-032");
    }

    @Test
    @DisplayName("T091: der Fall, ohne den die Pruefung die Haelfte nicht saehe")
    void apassiveWithSeveralMarkersIsCoveredToo() {
        // Eine PASSIVE Faehigkeit kann MEHRERE Materialien belegen - der Magier deckt mit
        // Rise & Fall zwei Slots aus einer Faehigkeit. Eine Pruefung nur ueber item() saehe nur den
        // ersten, und die Doppelung auf dem ZWEITEN bliebe unsichtbar.
        //
        // Genau das war die Annahme im ersten Entwurf dieses Blocks, und sie war falsch. Der Test
        // legt die Doppelung deshalb bewusst auf das zweite Material.
        assertThatThrownBy(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.MAGE
                                                        ? List.of(
                                                                new SlotUse(
                                                                        "rise-and-fall",
                                                                        List.of(
                                                                                "FEATHER",
                                                                                "PHANTOM_MEMBRANE")),
                                                                slot("gust", "PHANTOM_MEMBRANE"))
                                                        : List.of()))
                .hasMessageContaining("rise-and-fall")
                .hasMessageContaining("gust")
                .hasMessageContaining("PHANTOM_MEMBRANE");
    }

    @Test
    @DisplayName("T091: eine Doppelung auf dem ERSTEN Material faellt genauso auf")
    void aclashOnTheFirstMaterialIsCaughtToo() {
        // Die Gegenprobe: die Pruefung darf nicht nur den zweiten Marker sehen.
        assertThatThrownBy(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.MAGE
                                                        ? List.of(
                                                                new SlotUse(
                                                                        "rise-and-fall",
                                                                        List.of(
                                                                                "FEATHER",
                                                                                "PHANTOM_MEMBRANE")),
                                                                slot("dash", "FEATHER"))
                                                        : List.of()))
                .hasMessageContaining("FEATHER");
    }

    @Test
    @DisplayName("T092: dasselbe Material in ZWEI Klassen ist in Ordnung")
    void thesameMaterialInTwoClassesIsFine() {
        // Ein Krieger und ein Magier sehen die Leisten des jeweils anderen nie. Eine globale
        // Pruefung wuerde Konfigurationen verbieten, die niemandem schaden - und der Betreiber
        // muesste achtzehn Faehigkeiten auf achtzehn verschiedene Materialien verteilen.
        assertThatCode(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                switch (characterClass) {
                                                    case WARRIOR ->
                                                            List.of(slot("cleave", "IRON_SWORD"));
                                                    case MAGE ->
                                                            List.of(slot("frost", "IRON_SWORD"));
                                                    case ROGUE ->
                                                            List.of(slot("stab", "IRON_SWORD"));
                                                }))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verschiedene Materialien in einer Klasse sind in Ordnung")
    void differentMaterialsInOneClassAreFine() {
        assertThatCode(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.WARRIOR
                                                        ? List.of(
                                                                slot("cleave", "IRON_SWORD"),
                                                                slot("bash", "SHIELD"),
                                                                new SlotUse(
                                                                        "endure",
                                                                        List.of(
                                                                                "IRON_CHESTPLATE",
                                                                                "TOTEM_OF_UNDYING")))
                                                        : List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("eine passive Faehigkeit ohne Marker stoert nicht")
    void apassiveWithoutAMarkerIsIgnored() {
        // Sie belegt keinen Slot, also kann sie sich auch keinen teilen. Zwei davon sind kein
        // Konflikt - und eine Pruefung, die den leeren Fall als Doppelung zaehlte, verboete jede
        // Klasse mit mehr als einer reinen Passiven.
        assertThatCode(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.ROGUE
                                                        ? List.of(
                                                                new SlotUse("evasion", List.of()),
                                                                new SlotUse("stealth", List.of()))
                                                        : List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dieselbe Faehigkeit zweimal gelistet ist kein Konflikt mit sich selbst")
    void anabilityDoesNotClashWithItself() {
        // Sonst braeche der Start an einer doppelten Zeile in der Konfiguration, die inhaltlich
        // nichts aendert - und die Meldung naennte zweimal denselben Namen.
        assertThatCode(
                        () ->
                                MaterialUniqueness.verify(
                                        characterClass ->
                                                characterClass == CharacterClass.WARRIOR
                                                        ? List.of(
                                                                slot("cleave", "IRON_SWORD"),
                                                                slot("cleave", "IRON_SWORD"))
                                                        : List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("eine leere Klasse ist in Ordnung")
    void anemptyClassIsFine() {
        assertThatCode(() -> MaterialUniqueness.verify(characterClass -> List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SlotUse haelt seine Materialien unveraenderlich")
    void slotUseIsImmutable() {
        SlotUse use = new SlotUse("cleave", new java.util.ArrayList<>(List.of("IRON_SWORD")));

        assertThatThrownBy(() -> use.materials().add("SHIELD"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(use.materials()).containsExactly("IRON_SWORD");
    }

    @Test
    @DisplayName("T101: die Abbruchmeldung steht im LOG und nicht in messages.yml")
    void theabortMessageIsNotAPlayerText() {
        // Startmeldungen gehen ins Log und nicht an einen Spieler: wenn sie faellt, spielt noch
        // niemand. Ein Message-Schluessel dafuer waere einer, den der Waechter aus T137 spaeter zu
        // Recht nicht findet - und den ein Uebersetzer uebersetzen muesste, ohne ihn je zu sehen.
        //
        // Der Test haelt fest, dass die Meldung ENGLISCH und aus dem Code kommt (Prinzip VIII: der
        // Code ist englisch) und alles nennt, was der Betreiber zum Beheben braucht.
        String message =
                org.assertj.core.api.Assertions.catchThrowable(
                                () ->
                                        MaterialUniqueness.verify(
                                                characterClass ->
                                                        characterClass == CharacterClass.WARRIOR
                                                                ? List.of(
                                                                        slot("cleave", "IRON_SWORD"),
                                                                        slot("bash", "IRON_SWORD"))
                                                                : List.of()))
                        .getMessage();

        assertThat(message)
                .as("die Datei, in der der Betreiber sucht")
                .contains("abilities.yml");
        assertThat(message).as("welche Klasse").contains("WARRIOR");
        assertThat(message).as("BEIDE Faehigkeiten").contains("cleave").contains("bash");
        assertThat(message).as("das Material").contains("IRON_SWORD");
        assertThat(message)
                .as("und WARUM es ein Problem ist - sonst liest es sich wie Schikane")
                .contains("per MATERIAL");
        assertThat(message).as("die Anforderung zum Nachschlagen").contains("FR-032");
    }

    private static SlotUse slot(String abilityId, String material) {
        return new SlotUse(abilityId, List.of(material));
    }
}

package rpg.core.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.classes.LadderSlot;
import rpg.core.item.WearCurve;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;

/**
 * Was die Charakterübersicht zeigt — für <b>einen</b> Charakter (FR-050 bis FR-053).
 *
 * <h2>Sie bildet keine Summe über mehrere Charaktere</h2>
 *
 * <p>FR-053, und das ist keine Formalie: ein Spieler mit drei Charakteren hat drei Konten, drei
 * Ausrüstungen und drei Fortschritte. Eine Summe wäre eine Zahl, die keinem davon gehört — und sie
 * sähe aus wie ein Fehler in dem Moment, in dem er den Charakter wechselt und alles kleiner wird.
 *
 * <h2>{@link #revision} ist die Ungültigkeitsmarke</h2>
 *
 * <p>FR-054 verlangt, den Inhalt zwischenzuspeichern und <b>nur bei Änderung</b> neu aufzubauen.
 * Die Marke dafür ist {@code StatSnapshot.revision()} aus B04 — sie zählt hoch, wenn sich die Werte
 * tatsächlich bewegt haben, und sie ist die einzige Antwort auf „hat sich etwas geändert", die B13
 * nicht selbst erfinden muss.
 *
 * <p><b>Ein Vergleich der Werte statt der Marke wäre die naheliegende Alternative</b> — und sie
 * wäre teurer und ungenauer: zehn Doubles zu vergleichen kostet mehr als einen {@code long}, und
 * zwei Rechenwege, die zufällig auf denselben Wert kommen, sind trotzdem eine Änderung, die den
 * Spieler interessiert.
 *
 * <h2>Sie speichert nichts und rendert nichts</h2>
 *
 * <p>Kein Text, keine Farbe, kein {@code ItemStack} — nur Zahlen und Kennungen. Wie das aussieht,
 * entscheidet {@code CharacterSheetMenu} in {@code rpg-platform}, und was ein Gegenstand
 * <em>ist</em>, entscheidet B11.
 *
 * @param characterId der <b>aktive</b> Charakter, nicht der Halter
 * @param characterClass seine Klasse aus B07
 * @param level sein Level aus B06
 * @param coins sein Coin-Stand aus B08b
 * @param attributes jedes Attribut, das B04 führt, mit seinem aktuellen Wert
 * @param revision die Marke aus {@code StatSnapshot.revision()}
 * @param equipment was auf welchem Platz steckt — die Vorlagenkennung, nicht der Gegenstand
 * @param conditions der Zustand je Platz, wie B11 ihn führt, in {@code [0,1]}
 */
public record CharacterSheet(
        UUID characterId,
        CharacterClass characterClass,
        int level,
        long coins,
        Map<Attribute, Double> attributes,
        long revision,
        Map<LadderSlot, String> equipment,
        Map<LadderSlot, Double> conditions) {

    public CharacterSheet {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(characterClass, "characterClass");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        equipment = Map.copyOf(Objects.requireNonNull(equipment, "equipment"));
        conditions = Map.copyOf(Objects.requireNonNull(conditions, "conditions"));

        // JEDES Attribut, nicht die, die gerade ungleich null sind (FR-050). Eine Uebersicht, die
        // eine Zeile weglaesst, weil ihr Wert 0 ist, laesst den Spieler raten, ob es das Attribut
        // gibt - und beim naechsten Ausruestungsstueck taucht es unvermittelt auf.
        if (attributes.size() != Attribute.values().length) {
            throw new IllegalArgumentException(
                    "die Uebersicht zeigt jedes Attribut: erwartet "
                            + Attribute.values().length
                            + ", bekommen "
                            + attributes.size());
        }
        // [0, WearCurve.FULL] und NICHT [0,1]: B11 fuehrt den Zustand auf einer PROZENTSKALA -
        // WearCurve.FULL ist 100.0. Ein erster Entwurf nahm hier einen Anteil an; damit waere jedes
        // getragene Stueck abgewiesen worden, sobald sein Zustand ueber 1 liegt - also praktisch
        // immer.
        for (Map.Entry<LadderSlot, Double> entry : conditions.entrySet()) {
            double condition = entry.getValue();
            if (condition < 0.0 || condition > WearCurve.FULL || Double.isNaN(condition)) {
                throw new IllegalArgumentException(
                        "Zustand fuer "
                                + entry.getKey()
                                + " ist "
                                + condition
                                + " - erlaubt [0,"
                                + WearCurve.FULL
                                + "]");
            }
        }
    }

    /** Der Wert eines Attributs. Nie leer — jedes ist enthalten. */
    public double valueOf(Attribute attribute) {
        return attributes.getOrDefault(attribute, 0.0);
    }

    /** Was auf diesem Platz steckt, oder leer, wenn er frei ist. */
    public Optional<String> equipmentOn(LadderSlot slot) {
        return Optional.ofNullable(equipment.get(slot));
    }

    /**
     * Der Zustand dieses Platzes.
     *
     * <p>Voll für einen leeren Platz: nichts abgenutzt ist die richtige Antwort für „da ist
     * nichts", und ein leeres {@code Optional} zwänge jede Aufrufstelle zu derselben
     * Fallunterscheidung.
     *
     * <p><b>Prozent, nicht Anteil</b> — {@link WearCurve#FULL} ist 100.0.
     */
    public double conditionOn(LadderSlot slot) {
        return conditions.getOrDefault(slot, WearCurve.FULL);
    }

    /**
     * Ob dieser Stand noch gilt.
     *
     * <p>Die eine Frage, die {@code CharacterSheetMenu} vor jedem Neuaufbau stellt (FR-054).
     */
    public boolean isCurrent(long currentRevision) {
        return revision == currentRevision;
    }

    /** Ein leerer Attributsatz, in dem trotzdem jedes Attribut steht — für Aufbau und Test. */
    public static Map<Attribute, Double> emptyAttributes() {
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            values.put(attribute, 0.0);
        }
        return values;
    }
}

package rpg.core.item;

import java.util.UUID;

import rpg.core.classes.GearConditionFactor;
import rpg.core.classes.LadderSlot;

/**
 * Was andere Blöcke über den Ausrüstungszustand fragen dürfen
 * ({@code contracts/item-api.md} §1).
 *
 * <p>Öffentlich, weil B13 den Zustand anzeigen und B12 ihn auswerten will — und weil <b>zwei Wege
 * zu derselben Zahl einer zu viel wären</b>. Wer den Faktor woanders herrechnet, rechnet ihn
 * irgendwann anders.
 *
 * <p><b>Und es ist dieselbe Zahl, die B07 benutzt.</b> Diese Schnittstelle erweitert
 * {@link GearConditionFactor}, die Naht aus dem Complexity Tracking: was hier steht, ist genau das,
 * womit {@code ClassStatContributor} seinen Stufenbeitrag multipliziert. Zwei Schnittstellen mit
 * derselben Methode wären zwei Gelegenheiten, sie auseinanderlaufen zu lassen.
 */
public interface GearConditions extends GearConditionFactor {

    /** Zustand eines Slots in {@code [0, 100]}. 100 für einen unbekannten Charakter. */
    double conditionOf(UUID characterId, LadderSlot slot);

    /**
     * Der Faktor, mit dem der Stufenbeitrag multipliziert wird (FR-047).
     *
     * <p>Gleichbedeutend mit {@link GearConditionFactor#factorFor} — der zweite Name existiert, weil
     * der Vertrag ihn so nennt und B07 die Naht so kennt.
     */
    double factorOf(UUID characterId, LadderSlot slot);

    /**
     * Derselbe Faktor, aber für einen <em>gegebenen</em> Zustand statt für einen Charakter.
     *
     * <p>Für die Anzeige: „68 % Haltbarkeit" sagt einem Spieler nichts darüber, was es ihn kostet —
     * „Werte bei 87 %" schon. Wer diese zweite Zahl selbst ausrechnete, hätte eine zweite
     * Verschleißkurve, und die wäre nach dem ersten Balancing eine falsche Anzeige zu richtigen
     * Werten. Das ist der unangenehmste Fehler dieser Art, weil ihn niemand dem Anzeigecode
     * zuordnet.
     */
    double factorForCondition(double condition);

    @Override
    default double factorFor(UUID characterId, LadderSlot slot) {
        return factorOf(characterId, slot);
    }
}

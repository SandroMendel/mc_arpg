package rpg.core.classes;

import java.util.UUID;

/**
 * Wie viel vom Stufenwert eines Slots noch ankommt (B11/FR-047).
 *
 * <p><b>Eine Naht, die B07 anbietet und B11 füllt.</b> Dieselbe Bauart, die ADR-027 für B08 gewählt
 * hat: <em>„Die Preisprüfung hängt über eine Naht (`AbilityRuntime.RankCost`) darin — B08 zeigt
 * nicht auf B08b."</em> Der ältere Block bekommt eine Schnittstelle, der jüngere füllt sie, und die
 * Abhängigkeitsrichtung bleibt B11 → B07.
 *
 * <p><b>Warum das überhaupt nötig ist, und warum kein Modifikator reicht.</b>
 * {@link ClassStatContributor} liefert die Stufenwerte als <b>Grundwerte</b>, nicht als
 * Modifikatoren — mit ausführlicher Begründung dort: B04s Modifikatorband liegt um den
 * <em>effektiven</em> Grundwert, und wären die Stufenwerte flache Modifikatoren, bliebe das Band am
 * Level-1-Grundwert hängen. Ein Band von ±30 % um 40 Leben ließe die 1385 eines Stufe-6-Kriegers nie
 * zu, und der Wert würde <em>unbemerkt</em> geklammert.
 *
 * <p>Ein Verschleiß, der als negativer {@code SourceKind.EQUIPMENT}-Beitrag käme, liefe in genau
 * diese Klammer: er läge im Band um einen Grundwert, der den vollen Stufenwert bereits enthält, und
 * würde bei 80 % Abzug still abgeschnitten. Deshalb skaliert der Verschleiß den <b>Grundbeitrag
 * selbst</b>, und deshalb steht diese Schnittstelle hier statt in B04 (research.md R1).
 *
 * <p><b>Ohne B11 gilt {@link #NONE}</b>, und B07 verhält sich bitgenau wie zuvor. Das ist die
 * Abnahmebedingung dieses Eingriffs, nicht nur eine Bequemlichkeit:
 * {@code ClassContributionIsUnchangedWithoutB11Test} prüft es, und B07s vorhandener Testbestand
 * bleibt unverändert grün.
 *
 * <p><b>Der Faktor wird als {@code double} weitergereicht, nicht als umhüllender Sink.</b> Er wird
 * im Neuberechnungspfad gelesen, und ein Lambda je Aufruf wäre eine Zuweisung dort (Prinzip II).
 */
@FunctionalInterface
public interface GearConditionFactor {

    /** Ohne B11: jede Stufe trägt voll bei. */
    GearConditionFactor NONE = (characterId, slot) -> 1.0;

    /**
     * @param characterId wessen Ausrüstung
     * @param slot welcher der beiden Leiter-Slots
     * @return Faktor in {@code [0, 1]} auf den Grundbeitrag dieses Slots; {@code 1.0} heißt
     *     unverschlissen
     */
    double factorFor(UUID characterId, LadderSlot slot);
}

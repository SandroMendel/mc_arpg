package rpg.core.item;

import java.util.Locale;
import java.util.Optional;

/**
 * Wie selten ein Gegenstand ist - und <b>sonst nichts</b> (FR-014).
 *
 * <p><b>Diese Skala traegt keinen Wert.</b> Ein epischer Trank heilt nicht mehr als ein
 * gewoehnlicher; er ist seltener. Das ist die Entscheidung aus ADR-027, und sie ist wichtiger, als
 * sie klingt: Raritaet als Werttraeger haette genau die Wertebereiche zurueckgebracht, die
 * dieselbe Entscheidung gerade abgeschafft hat. Als reine Farbe kostet die Skala fast nichts.
 *
 * <p><b>Es gibt hier deshalb absichtlich keine Methode, die eine Zahl liefert.</b> Kein
 * {@code multiplier()}, kein {@code bonus()}, kein {@code weight()}. Der einzige Weg, diese Zusage
 * zu brechen, waere, hier eine hinzuzufuegen - und {@code RarityHasNoEffectTest} sieht genau
 * danach.
 *
 * <p><b>Die Reihenfolge ist eine Ordnung, keine Staerke.</b> {@link #ordinal()} sagt, was seltener
 * ist als was; es sagt nicht, was besser ist. Ein Sortierhinweis fuer eine Anzeige, mehr nicht.
 *
 * <p><b>Der sichtbare Name und die Farbe stehen in {@code messages.yml}</b>, unter
 * {@code item.rarity.<key>.name} - wie jeder Spielertext (Prinzip V). Ein Farbcode im Java-Quelltext
 * waere ein hartcodierter Spielertext mit einem anderen Namen.
 */
public enum Rarity {

    /** Gewoehnlich - Weiss. */
    COMMON,

    /** Ungewoehnlich - Hellgruen. */
    UNCOMMON,

    /** Selten - Blau. */
    RARE,

    /** Episch - Lila. */
    EPIC,

    /** Legendaer - Orange. */
    LEGENDARY,

    /** Mythisch - Pink. */
    MYTHIC,

    /** Goettlich - Hellblau. */
    DIVINE,

    /** Speziell - Rot. Fuer Seasons und Ereignisse. */
    SPECIAL;

    /** Der Schluessel, unter dem diese Stufe in der Konfiguration steht: {@code common}, ... */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Liest eine Stufe aus der Konfiguration.
     *
     * @return leer bei einem unbekannten Namen - der Aufrufer macht daraus einen Startfehler mit
     *     Datei, Schluessel und Grund (FR-012), statt hier still auf {@code COMMON} zu fallen
     */
    public static Optional<Rarity> fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (Rarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }
}

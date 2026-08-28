package rpg.core.item;

import java.util.Collection;
import java.util.Optional;

/**
 * Was B11 nach außen anbietet (contracts/item-api.md §1).
 *
 * <p><b>Klein, und das ist Absicht.</b> B11 ist der letzte Block der Schicht 2, der von anderen
 * etwas will; nach ihm fragen nur noch B12 (Statistiken) und B13 (Anzeige). Die öffentliche Fläche
 * ist entsprechend schmal.
 *
 * <p><b>Diese Schnittstelle kennt keinen {@code ItemStack}.</b> Sie beantwortet Fragen über
 * <em>Vorlagen</em>; was ein Exemplar in der Welt ist, weiß nur die Plattformschicht
 * (Constitution III). Wer hier einen Bukkit-Typ unterbringen will, hat die Grenze gefunden.
 *
 * <p><b>Nichts wirft, und nichts antwortet mit {@code null}.</b> Eine unbekannte Kennung ist keine
 * Ausnahme, sondern eine leere Antwort — dieselbe Zusage, die {@code MobKinds} in B10 gibt, und aus
 * demselben Grund: eine Vorlage kann zwischen zwei Reloads verschwinden, und ein Exemplar davon
 * liegt dann noch in einem Inventar (FR-007).
 */
public interface Items {

    /** Die Vorlage zu dieser Kennung. Leer bei einer unbekannten — niemals {@code null}. */
    Optional<ItemTemplate> template(String templateKey);

    /** Jede bekannte Vorlagenkennung, in Konfigurationsreihenfolge. */
    Collection<String> templateKeys();

    /**
     * Was ein Händler für dieses Exemplar zahlt.
     *
     * @return leer, wenn die Vorlage unbekannt oder unverkäuflich ist (FR-016) — der Händler weist
     *     beides ab, und aus Sicht des Spielers ist es dasselbe
     */
    java.util.OptionalLong sellPriceOf(String templateKey);

    /** Die Verschleißkurve — B12 und B13 lesen sie mit (contracts/item-api.md §1). */
    WearCurve wear();
}

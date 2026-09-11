package rpg.core.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Was der Haendler <b>einer Region</b> verkauft, mit Preis (FR-058).
 *
 * <p><b>Nur der Verkauf steht hier.</b> Was er <em>ankauft</em>, steht als {@code sell-price} an der
 * Vorlage (FR-016) - sonst gaebe es sechs Orte fuer denselben Betrag, und beim naechsten Balancing
 * wuerde einer davon vergessen.
 *
 * <p><b>Je Region ein eigener Bestand</b> (Q6, ADR-039). Greenfields fuehrt anderes als die Pale
 * Wilds; Reisen lohnt sich, und niemand muss quer ueber die Karte, um einen Trank loszuwerden.
 */
public record VendorStock(Map<String, Long> prices) {

    public VendorStock {
        // LinkedHashMap statt Map.copyOf: der Bestand wird in dieser Reihenfolge angezeigt. Map.copyOf
        // verwirft sie, und das Fenster saehe nach jedem Neustart anders aus.
        prices =
                java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(Objects.requireNonNull(prices, "prices")));
        for (Map.Entry<String, Long> entry : prices.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0L) {
                throw new IllegalArgumentException(
                        entry.getKey() + ": price must not be negative, but was " + entry.getValue());
            }
        }
    }

    public static VendorStock empty() {
        return new VendorStock(Map.of());
    }

    /** Was dieser Haendler fuer die Vorlage verlangt. Leer heisst: er fuehrt sie nicht. */
    public OptionalLong priceOf(String templateKey) {
        Long price = prices.get(templateKey);
        return price == null ? OptionalLong.empty() : OptionalLong.of(price);
    }

    public boolean carries(String templateKey) {
        return prices.containsKey(templateKey);
    }

    /** Die gefuehrten Vorlagen, in Konfigurationsreihenfolge. */
    public java.util.Set<String> templateKeys() {
        return prices.keySet();
    }
}

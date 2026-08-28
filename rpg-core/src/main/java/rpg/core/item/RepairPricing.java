package rpg.core.item;

import java.util.List;
import java.util.Objects;

/**
 * Was eine Reparatur kostet (FR-053).
 *
 * <p><b>Der Preis steigt mit dem fehlenden Anteil</b>, damit eine Reparatur bei Zustand 99 nicht
 * dasselbe kostet wie eine bei Zustand 5. Sonst waere es guenstiger, die Ausrüstung erst
 * herunterkommen zu lassen - eine Regel, die den Spieler dafuer belohnt, schlecht ausgeruestet
 * herumzulaufen.
 *
 * <p><b>Und er steigt mit der Stufe.</b> Ein Stufe-6-Satz wiederherzustellen ist teurer als ein
 * Stufe-2-Satz; das haelt die Coin-Senke ueber die ganze Progression spuerbar, statt sie auf Level
 * 60 belanglos werden zu lassen.
 *
 * <p><b>Die Preise stehen bei dem, der sie verlangt</b> (ADR-027). B08b fuehrt den Kontostand, nicht
 * den Katalog.
 *
 * @param basePerTier voller Preis je Stufe bei Zustand 0; Index 0 ist Stufe 1
 */
public record RepairPricing(List<Long> basePerTier) {

    public RepairPricing {
        basePerTier = List.copyOf(Objects.requireNonNull(basePerTier, "basePerTier"));
    }

    /**
     * Prueft die Konfiguration (FR-012).
     *
     * @param ladderLength wie viele Stufen die laengste Leiter hat
     * @param where Datei und Abschnitt, fuer die Meldung
     */
    public void validate(int ladderLength, String where) {
        if (basePerTier.size() < ladderLength) {
            throw new IllegalArgumentException(
                    where
                            + ".base-per-tier has "
                            + basePerTier.size()
                            + " entries but the longest equipment ladder has "
                            + ladderLength
                            + " tiers - a character on the top tier could not be charged");
        }
        for (int i = 0; i < basePerTier.size(); i++) {
            Long price = basePerTier.get(i);
            if (price == null || price < 0L) {
                throw new IllegalArgumentException(
                        where + ".base-per-tier[" + i + "] must not be negative, but was " + price);
            }
        }
    }

    /**
     * Was die Wiederherstellung dieses Slots kostet.
     *
     * @param tier erreichte Stufe, ab 1
     * @param condition aktueller Zustand in {@code [0, 100]}
     * @return 0, wenn nichts zu reparieren ist - der Aufrufer lehnt dann ab, ohne zu buchen
     *     (FR-054)
     */
    public long priceFor(int tier, double condition) {
        double missing = (WearCurve.FULL - Math.min(Math.max(condition, 0.0), WearCurve.FULL))
                / WearCurve.FULL;
        if (missing <= 0.0) {
            return 0L;
        }
        int index = Math.min(Math.max(tier, 1), basePerTier.size()) - 1;
        long full = basePerTier.get(index);
        // Abgerundet, und der Rest bleibt liegen - dieselbe Richtung, in die B06 und B08b runden
        // (FR-047 dort). Aufrunden erzeugte bei jeder Reparatur eine Coin aus dem Nichts.
        return (long) Math.floor(full * missing);
    }
}

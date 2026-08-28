package rpg.core.item;

import java.util.Objects;

/**
 * Wie eine gekaufte Trimfarbe aussieht (FR-068).
 *
 * <p><b>Ein benanntes Paar, nicht zwei waehlbare Felder.</b> Ein Vanilla-Trim besteht aus Material
 * und Muster; der Spieler kauft beides zusammen unter einem Namen und stellt es nicht selbst
 * zusammen. Zwei getrennte Auswahlen waeren eine Kombinationstabelle, die niemand balanciert hat.
 *
 * <p>Dieselben zwei Felder traegt {@code TierAppearance} in B07 - <b>und das ist kein Zufall,
 * sondern der Grund fuer FR-069</b>: eine frei anwendbare Trimfarbe wuerde den Trim der Stufe
 * ueberschreiben, und fuer Schurke und Krieger ist der Trim das <em>einzige</em>
 * Unterscheidungsmerkmal ihrer oberen Stufen. Deshalb ist Kosmetik erst auf der Hoechststufe
 * anwendbar, wo es nichts mehr zu verwechseln gibt.
 *
 * <p>Dieses Paket haelt nur die Namen. Was Paper daraus macht, entscheidet
 * {@code rpg.platform.item} - und benutzt dafuer B07s vorhandenen Weg, statt einen zweiten zu
 * bauen (FR-079).
 *
 * @param trimMaterial Trim-Material, wie es Vanilla kennt
 * @param trimPattern Trim-Muster, wie es Vanilla kennt
 */
public record CosmeticAppearance(String trimMaterial, String trimPattern) {

    public CosmeticAppearance {
        Objects.requireNonNull(trimMaterial, "trimMaterial");
        Objects.requireNonNull(trimPattern, "trimPattern");
        if (trimMaterial.isBlank() || trimPattern.isBlank()) {
            throw new IllegalArgumentException(
                    "trim-material and trim-pattern must both be set, but were '"
                            + trimMaterial
                            + "' / '"
                            + trimPattern
                            + "'");
        }
    }
}

package rpg.platform.item;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.item.CosmeticAppearance;
import rpg.core.item.CosmeticApplication;
import rpg.platform.classes.ClassEquipmentApplier;

/**
 * Die gekaufte Trimfarbe auf dem Aussehen der Stufe (FR-070, FR-071).
 *
 * <p><b>Sie überschreibt den Trim und nichts sonst.</b> Material und Farbe bleiben, wie die Leiter
 * sie gesetzt hat — der Magier bleibt in seinem gefärbten Leder, der Krieger in seinem Netherite.
 * Nur das Trim-Paar wird getauscht, und das ist genau die eine Sache, die der Spieler gekauft hat.
 *
 * <p><b>Kein einziger Wert ändert sich</b> (FR-070, SC-014). {@link TierAppearance} trägt keine
 * Attribute — sie liegen in {@code EquipmentTier}, und dorthin führt von hier kein Weg. Das ist die
 * Zusage als Eigenschaft der Bauart und nicht als Behauptung.
 *
 * <p><b>Und nur, wenn {@code CosmeticApplication} zugestimmt hat.</b> Ob die Höchststufe erreicht ist,
 * ist dort entschieden — hier wird nicht ein zweites Mal geprüft. Eine zweite Prüfung wäre eine
 * zweite Wahrheit, und sie stünde an der Stelle, an der niemand sie vermutet.
 */
public final class CosmeticOverride implements ClassEquipmentApplier.AppearanceOverride {

    private final CosmeticApplication cosmetics;

    public CosmeticOverride(CosmeticApplication cosmetics) {
        this.cosmetics = Objects.requireNonNull(cosmetics, "cosmetics");
    }

    @Override
    public TierAppearance apply(UUID characterId, LadderSlot slot, TierAppearance appearance) {
        if (appearance == null) {
            return null;
        }
        Optional<CosmeticAppearance> bought = cosmetics.appearanceOf(characterId);
        if (bought.isEmpty()) {
            // Nichts gekauft, nichts getragen, oder eine Farbe, die die Konfiguration nicht mehr
            // kennt (FR-073). In allen drei Faellen gilt das Aussehen der Stufe - und der
            // Besitzvermerk bleibt, wo er ist.
            return appearance;
        }
        return new TierAppearance(
                appearance.material(),
                appearance.color(),
                bought.get().trimMaterial(),
                bought.get().trimPattern(),
                appearance.modelData());
    }
}

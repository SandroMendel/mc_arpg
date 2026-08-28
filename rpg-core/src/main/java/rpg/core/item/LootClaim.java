package rpg.core.item;

import java.util.Objects;
import java.util.UUID;

/**
 * Ein Posten, der fallen soll, und wem er gehört (FR-020, FR-026, FR-027).
 *
 * <p>Der Anspruch hängt am <b>Charakter</b>, nicht am Spieler (ADR-011). B05 schlüsselt seine
 * Schadensanteile nach Spielerkennung; die Übersetzung geschieht im {@link LootPlanner}, weil sonst
 * jeder spätere Leser sie noch einmal machen müsste — und einer von ihnen sie vergäße.
 *
 * @param templateKey welche Vorlage
 * @param count wie viele Stück
 * @param ownerCharacterId wem sie gehören
 */
public record LootClaim(String templateKey, int count, UUID ownerCharacterId) {

    public LootClaim {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1, but was " + count);
        }
    }
}

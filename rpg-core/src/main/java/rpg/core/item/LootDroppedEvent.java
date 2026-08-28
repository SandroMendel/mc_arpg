package rpg.core.item;

import java.util.Objects;
import java.util.UUID;

/**
 * Ein Gegenstand ist gefallen und einem Charakter zugeordnet (contracts/item-api.md §4).
 *
 * <p><b>Für B12.</b> Was ein Spieler erbeutet hat, ist eine Statistik, und Statistiken gehören
 * nicht hierher. Dieser Block sagt nur, dass es passiert ist — auf dem Kern-Ereignisbus, wie alles
 * in diesem Projekt, das ein anderer Block wissen will, ohne dass die beiden voneinander wissen
 * müssen.
 *
 * @param templateKey welche Vorlage
 * @param count wie viele Stück
 * @param ownerCharacterId wem sie gehören — der <b>Charakter</b>, nicht der Spieler (ADR-011)
 * @param kindKey welche Kreatur sie hinterlassen hat
 */
public record LootDroppedEvent(
        String templateKey, int count, UUID ownerCharacterId, String kindKey) {

    public LootDroppedEvent {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(kindKey, "kindKey");
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1, but was " + count);
        }
    }
}

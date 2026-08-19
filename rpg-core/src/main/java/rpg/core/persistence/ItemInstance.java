package rpg.core.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One concrete item owned by a character.
 *
 * <p>Stores the template id and the rolled values, and deliberately <strong>never</strong> computed
 * final values or rendered lore (ADR-004, Constitution IV). That is what lets a later balancing
 * rework change what a template means without touching a single existing player item - if the
 * computed result were stored, every item would be frozen at the moment it dropped.
 *
 * <p>The owner is a <em>character</em>, not an account (ADR-011). The three characters of one
 * account share no progress, so an item rolled by the Warrior must never turn up on the Mage.
 *
 * @param instanceId identity of this one copy
 * @param ownerCharacterId the character that owns it
 * @param templateId which template it was rolled from (content configuration, B16)
 * @param rolledValues the values rolled for this copy
 * @param revision incremented on every write
 */
public record ItemInstance(
        UUID instanceId,
        UUID ownerCharacterId,
        String templateId,
        Map<String, Object> rolledValues,
        long revision) {

    public ItemInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(templateId, "templateId");
        rolledValues = Map.copyOf(Objects.requireNonNull(rolledValues, "rolledValues"));
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
    }
}

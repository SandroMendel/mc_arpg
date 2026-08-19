package rpg.core.scheduler;

import java.util.Objects;
import java.util.UUID;

/**
 * A reference to an entity, expressed without any Paper/Bukkit type.
 *
 * <p>Holds only the entity's unique id, never the entity object itself: an entity instance is only
 * safe to touch inside the tick that owns it, while a reference can be passed around freely
 * (Constitution I.1). The platform adapter resolves it to the live entity at execution time.
 *
 * @param entityId unique id of the entity
 */
public record EntityRef(UUID entityId) {

    public EntityRef {
        Objects.requireNonNull(entityId, "entityId");
    }
}

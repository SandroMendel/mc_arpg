package rpg.content;

import java.util.Objects;

/**
 * Declares one path at which mapping keys are registry identifiers rather than fixed keys.
 *
 * <p>This is metadata only. It does not parse YAML and does not resolve or validate an identifier.
 *
 * @param path the dotted path pattern, including its identifier placeholder
 * @param keyType the kind of identifier permitted at that path
 */
public record ContentRegistryContract(String path, KeyType keyType) {

    public ContentRegistryContract {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(keyType, "keyType");
        if (path.isBlank()) {
            throw new IllegalArgumentException("registry path must not be blank");
        }
    }

    /** The identifier categories used by the current B16 registry declarations. */
    public enum KeyType {
        ABILITY_ID,
        ATTRIBUTE_ID,
        CLASS_ID,
        ITEM_TEMPLATE_ID,
        LEVEL_ID,
        MOB_KIND_ID,
        SPAWN_AREA_ID,
        VANILLA_MOB_TYPE,
        ZONE_ID
    }
}

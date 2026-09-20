package rpg.content.validation;

import java.util.Objects;

import rpg.content.ContentRegistryContract;

/**
 * Immutable candidate for one explicitly declared typed registry key.
 *
 * <p>Only callers that know the registry contract can construct a candidate. Display names,
 * source line numbers and runtime object names therefore have no implicit role in ID validation.
 *
 * @param domain document/domain owning the registry
 * @param id candidate stable identifier
 * @param keyType identifier category from {@link ContentRegistryContract}
 * @param yamlPath exact YAML path at which the key is declared
 * @param registryPath identity of the registry containing the key
 */
public record StableIdCandidate(
        String domain,
        String id,
        ContentRegistryContract.KeyType keyType,
        String yamlPath,
        String registryPath) {

    public StableIdCandidate {
        requireText(domain, "domain");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(keyType, "keyType");
        requireText(yamlPath, "yamlPath");
        requireText(registryPath, "registryPath");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

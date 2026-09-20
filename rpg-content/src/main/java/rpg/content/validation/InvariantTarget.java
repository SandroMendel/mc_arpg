package rpg.content.validation;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import rpg.content.ContentRegistryContract;

/** Immutable, server-free descriptor of a target registry entry and optional context. */
public record InvariantTarget(
        String domain,
        String id,
        String registryPath,
        ContentRegistryContract.KeyType keyType,
    String scope,
    Set<String> capabilities) {

    public InvariantTarget(
            String domain,
            String id,
            String registryPath,
            ContentRegistryContract.KeyType keyType) {
        this(domain, id, registryPath, keyType, null, Set.of());
    }

    public InvariantTarget {
        requireText(domain, "domain");
        requireText(id, "id");
        requireText(registryPath, "registryPath");
        Objects.requireNonNull(keyType, "keyType");
        if (scope != null && scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        Objects.requireNonNull(capabilities, "capabilities");
        TreeSet<String> copy = new TreeSet<>();
        for (String capability : capabilities) {
            requireText(capability, "capability");
            copy.add(capability);
        }
        capabilities = Collections.unmodifiableSet(copy);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

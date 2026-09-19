package rpg.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable collection of the typed registries owned by one content domain.
 *
 * <p>The contained {@link TypedContentRegistry} instances retain their key and value types. This
 * aggregate adds the domain and registry-path identity needed by documents and snapshots without
 * introducing untyped entry maps or another binding layer.
 */
public final class ContentRegistry {

    private final String domain;
    private final List<TypedContentRegistry<?, ?>> registries;
    private final Map<String, TypedContentRegistry<?, ?>> registriesByPath;

    public ContentRegistry(
            String domain, Collection<? extends TypedContentRegistry<?, ?>> registries) {
        this.domain = requireText(domain, "domain");
        Objects.requireNonNull(registries, "registries");

        List<TypedContentRegistry<?, ?>> ordered = new ArrayList<>(registries.size());
        Map<String, TypedContentRegistry<?, ?>> indexed = new LinkedHashMap<>();
        for (TypedContentRegistry<?, ?> registry : registries) {
            Objects.requireNonNull(registry, "registry");
            String path = registry.contract().path();
            if (indexed.putIfAbsent(path, registry) != null) {
                throw new IllegalArgumentException(
                        "duplicate registry path for domain " + domain + ": " + path);
            }
            ordered.add(registry);
        }

        this.registries = List.copyOf(ordered);
        this.registriesByPath = Collections.unmodifiableMap(indexed);
    }

    public static ContentRegistry empty(String domain) {
        return new ContentRegistry(domain, List.of());
    }

    public String domain() {
        return domain;
    }

    public List<TypedContentRegistry<?, ?>> registries() {
        return registries;
    }

    public Set<String> paths() {
        return registriesByPath.keySet();
    }

    public Optional<TypedContentRegistry<?, ?>> find(String path) {
        if (path == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registriesByPath.get(path));
    }

    public Optional<TypedContentRegistry<?, ?>> find(ContentRegistryContract contract) {
        if (contract == null) {
            return Optional.empty();
        }
        return find(contract.path()).filter(registry -> registry.contract().equals(contract));
    }

    public TypedContentRegistry<?, ?> require(String path) {
        return find(path)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unknown content registry for domain "
                                                + domain
                                                + ": "
                                                + path));
    }

    public int size() {
        return registries.size();
    }

    public Map<String, TypedContentRegistry<?, ?>> asMap() {
        return registriesByPath;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

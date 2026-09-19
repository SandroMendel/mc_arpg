package rpg.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, order-preserving view of one documented content registry.
 *
 * <p>This is deliberately a small value-level API. It does not parse documents, validate root
 * keys, or resolve references. Construction requires the immutable registry contract/path and
 * key type declared by the domain document, so an unbound generic registry cannot bypass the
 * content policy.
 *
 * <p>Values are not deep-copied. Callers must provide already-validated immutable values (the core
 * content records use immutable collection boundaries). Publication of registries into a runtime
 * snapshot belongs to T013's {@code ContentRegistry}/{@code ContentSnapshot}, which will compose
 * and own these value-level registries.
 */
public final class TypedContentRegistry<K, V> {

    private final ContentRegistryContract contract;
    private final Map<K, V> entries;

    public TypedContentRegistry(ContentRegistryContract contract, Map<K, V> entries) {
        this.contract = Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(entries, "entries");
        Map<K, V> copy = new LinkedHashMap<>();
        entries.forEach(
                (key, value) -> {
                    Objects.requireNonNull(key, "registry key");
                    Objects.requireNonNull(value, "registry value for " + key);
                    if (copy.putIfAbsent(key, value) != null) {
                        throw new IllegalArgumentException("duplicate registry key: " + key);
                    }
                });
        this.entries = Collections.unmodifiableMap(copy);
    }

    public static <K, V> TypedContentRegistry<K, V> empty(ContentRegistryContract contract) {
        return new TypedContentRegistry<>(contract, Map.of());
    }

    public ContentRegistryContract contract() {
        return contract;
    }

    public Optional<V> find(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    public V require(K key) {
        return find(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown content registry key: " + key));
    }

    public boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    public int size() {
        return entries.size();
    }

    public Set<K> keys() {
        return entries.keySet();
    }

    public List<V> values() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    public Map<K, V> asMap() {
        return entries;
    }
}

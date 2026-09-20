package rpg.core.config;

import java.nio.file.Path;
import java.util.List;

/**
 * A live view of one registered configuration value, backed by one source or a coordinated batch.
 *
 * <p>{@link #get()} always returns the currently valid configuration: after a successful
 * {@link ConfigLoader#reloadAll()} it returns the new value, after a failed one it keeps returning
 * the previous value (FR-004). Modules therefore hold the handle, not the value, so a reload reaches
 * them without any further wiring.
 *
 * @param <T> the typed configuration object
 */
public interface ConfigHandle<T> {

    /** The currently valid configuration. Never {@code null}. */
    T get();

    /** The file this configuration is loaded from. */
    Path source();

    /**
     * All files that contribute to this value.
     *
     * <p>Ordinary handles contain one file. A batch handle keeps {@link #source()} as its first
     * diagnostic path and exposes the complete source set here.
     */
    default List<Path> sources() {
        return List.of(source());
    }
}

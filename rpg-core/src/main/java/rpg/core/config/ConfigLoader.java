package rpg.core.config;

import java.nio.file.Path;

/**
 * Loads and validates configuration sources against a declared schema.
 *
 * <p>Fail-fast at start (FR-002), atomic global hot reload at runtime (FR-003) with rollback to the
 * previously valid configuration when the new one is rejected (FR-004).
 *
 * <p>See {@code contracts/config-loader.md} for the behavioural contract.
 */
public interface ConfigLoader {

    /**
     * Reads and validates {@code source} against {@code schema} once.
     *
     * @throws ConfigValidationException naming file, document path and expected value when the
     *     document violates the schema - a required field is never silently defaulted (FR-002)
     */
    <T> T loadAndValidate(Path source, ConfigSchema<T> schema) throws ConfigValidationException;

    /**
     * Like {@link #loadAndValidate}, but additionally keeps the source registered so it takes part
     * in {@link #reloadAll()}.
     *
     * <p>Extension over {@code contracts/config-loader.md}: the contract's {@code reloadAll()} has to
     * know which sources exist and callers have to observe the new value after a reload. A handle is
     * the minimal mechanism providing both without a callback registry.
     *
     * @return a handle whose {@link ConfigHandle#get()} tracks the currently valid configuration
     * @throws ConfigValidationException if the initial load already violates the schema
     */
    <T> ConfigHandle<T> register(Path source, ConfigSchema<T> schema) throws ConfigValidationException;

    /**
     * Reloads every registered source at once (global reload, clarification 2026-08-19 - there is no
     * selective per-module reload).
     *
     * <p>Atomic: if any source fails validation, no handle is updated and the previously valid
     * configuration stays active for all modules (FR-004). There is never a mixed old/new state.
     *
     * @throws ConfigValidationException describing the first offending source; the previous
     *     configuration remains active
     */
    void reloadAll() throws ConfigValidationException;
}

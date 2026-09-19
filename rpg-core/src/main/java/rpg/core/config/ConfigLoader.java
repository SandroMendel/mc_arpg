package rpg.core.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads and validates configuration sources against a declared schema.
 *
 * <p>Fail-fast at start (FR-002), atomic global hot reload at runtime (FR-003) with rollback to the
 * previously valid configuration when the new one is rejected (FR-004).
 *
 * <p>See {@code contracts/config-loader.md} for the behavioural contract.
 */
public interface ConfigLoader {

    /** A complete multi-source load that is staged together with ordinary config handles. */
    @FunctionalInterface
    interface BatchLoader<T> {

        /** Loads one complete value without publishing it. */
        T load() throws ConfigValidationException;
    }

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
     * Registers one value produced from several sources.
     *
     * <p>The returned handle is staged and published in the same generation as ordinary handles.
     * Implementations that do not support multi-source registration may reject this optional
     * extension; {@link AbstractConfigLoader} provides the production implementation.
     *
     * @param sources all source paths owned by the batch, in deterministic read order
     * @param loader parser/schema/invariant coordinator that returns a complete value
     * @return a handle for the complete staged value
     * @throws ConfigValidationException if the initial batch load is invalid
     */
    default <T> ConfigHandle<T> registerBatch(List<Path> sources, BatchLoader<T> loader)
            throws ConfigValidationException {
        throw new UnsupportedOperationException(
                "this config loader does not support multi-source registration");
    }

    /**
     * Reloads every registered source at once (global reload, clarification 2026-08-19 - there is no
     * selective per-module reload).
     *
     * <p>Atomic at the handle-generation boundary: all sources are staged first, then one committed
     * generation becomes visible. If any source fails validation, no handle is updated and the
     * previously valid configuration stays active for all modules (FR-004). A caller that needs a
     * stable view across several reads should retain the values it obtained from that generation;
     * derived module state is refreshed by the plugin after this method returns.
     *
     * @throws ConfigValidationException describing the first offending source; the previous
     *     configuration remains active
     */
    void reloadAll() throws ConfigValidationException;

    /**
     * Reloads with a synchronous post-staging hook and a compensating hook.
     *
     * <p>A transactional implementation keeps the newly staged generation private while
     * {@code afterStaging} runs. If that hook fails, the previous generation is made visible again
     * before {@code onRollback} runs. The rollback hook is intentionally explicit: it is the seam for
     * restoring derived module state that is not owned by the config loader.
     *
     * <p>The default keeps older loader implementations source-compatible. Such implementations can
     * only provide best-effort compensation because {@link #reloadAll()} has already published before
     * the hook is called; {@link AbstractConfigLoader} provides the atomic implementation used by the
     * production loader.
     *
     * @param afterStaging hook that observes the staged generation
     * @param onRollback compensation that observes the previous generation after a hook failure
     * @throws ConfigValidationException if staging fails
     */
    default void reloadAll(Runnable afterStaging, Runnable onRollback)
            throws ConfigValidationException {
        Objects.requireNonNull(afterStaging, "afterStaging");
        Objects.requireNonNull(onRollback, "onRollback");
        reloadAll();
        try {
            afterStaging.run();
        } catch (RuntimeException | Error failure) {
            try {
                onRollback.run();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }
}

package rpg.core.config;

/**
 * Marker for a {@link ConfigView} produced by the core validation boundary.
 *
 * <p>The marker carries no additional runtime behavior. It lets APIs that require the validated
 * path reject arbitrary {@code ConfigView} implementations at compile time while preserving the
 * existing {@link ConfigView} contract.
 */
public sealed interface ValidatedConfigView extends ConfigView permits MapConfigView {

    /** Exact schema instance that produced and validated this view. */
    ConfigSchema<?> validatingSchema();
}

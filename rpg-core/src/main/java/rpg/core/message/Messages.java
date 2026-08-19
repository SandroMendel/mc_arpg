package rpg.core.message;

import java.util.Map;

/**
 * Resolves a {@link MessageKey} to the text an operator configured for it.
 *
 * <p>The return type is a plain {@code String}, deliberately <strong>not</strong> an Adventure
 * {@code Component}: Adventure ships with the Paper API, and referencing it here would give
 * {@code rpg-core} a Paper dependency and break Constitution III.1. Turning the text into a
 * {@code Component} happens in {@code rpg-platform}, where Paper types are allowed.
 */
public interface Messages {

    /**
     * The configured text for {@code key}.
     *
     * @throws MissingMessageException if no text is configured - never a placeholder, never an
     *     empty string. A missing text must be loud, and {@link MessageKeyValidator} normally
     *     catches it at startup long before a player could see it.
     */
    String get(MessageKey key);

    /**
     * The configured text for {@code key} with {@code {name}} placeholders substituted.
     *
     * <p>A placeholder present in the text but absent from {@code placeholders} is left as-is
     * rather than blanked, so the gap is visible in the output instead of silently swallowed.
     */
    String get(MessageKey key, Map<String, String> placeholders);

    /** Whether a text is configured for {@code key}; used by the startup validation. */
    boolean contains(MessageKey key);
}

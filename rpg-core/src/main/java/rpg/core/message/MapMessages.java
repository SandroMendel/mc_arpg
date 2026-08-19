package rpg.core.message;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link Messages} over a flat key-to-text map, typically loaded from {@code messages.yml} through
 * the {@code ConfigLoader} from B01.
 *
 * <p>Immutable after construction: a reload produces a new instance rather than mutating this one,
 * which keeps a handler that is mid-render from seeing half of an old and half of a new text.
 */
public final class MapMessages implements Messages {

    private final Map<String, String> texts;

    public MapMessages(Map<String, String> texts) {
        Objects.requireNonNull(texts, "texts");
        this.texts = Map.copyOf(texts);
    }

    /**
     * Builds an instance from a nested document, as a YAML file naturally produces.
     *
     * <p>{@code {server: {kick: {starting-up: "..."}}}} becomes the key
     * {@code server.kick.starting-up}. Nesting exists purely for readability in the file; keys are
     * flat everywhere else.
     *
     * <p>A non-text leaf (number, boolean, list) is rejected rather than coerced: a message that is
     * accidentally a number is a mistake in the file, and turning it into {@code "42"} would hide
     * it.
     */
    public static MapMessages fromNested(Map<String, Object> document) {
        Objects.requireNonNull(document, "document");
        Map<String, String> flat = new LinkedHashMap<>();
        flattenInto(document, "", flat);
        return new MapMessages(flat);
    }

    private static void flattenInto(Map<?, ?> node, String prefix, Map<String, String> target) {
        for (Map.Entry<?, ?> entry : node.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey())
                    : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenInto(nested, key, target);
            } else if (value instanceof String text) {
                target.put(key, text);
            } else {
                throw new IllegalArgumentException(
                        "message '"
                                + key
                                + "' must be text, but was "
                                + (value == null ? "empty" : value.getClass().getSimpleName()));
            }
        }
    }

    @Override
    public String get(MessageKey key) {
        Objects.requireNonNull(key, "key");
        String text = texts.get(key.value());
        if (text == null) {
            throw new MissingMessageException(key);
        }
        return text;
    }

    @Override
    public String get(MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(placeholders, "placeholders");
        String text = get(key);
        if (placeholders.isEmpty()) {
            return text;
        }
        return substituteInOnePass(text, placeholders);
    }

    @Override
    public boolean contains(MessageKey key) {
        Objects.requireNonNull(key, "key");
        return texts.containsKey(key.value());
    }

    /** The configured keys, for diagnostics and for the startup validation. */
    public Map<String, String> asMap() {
        return new LinkedHashMap<>(texts);
    }

    /**
     * Substitutes every {@code {name}} in a single left-to-right pass over the template.
     *
     * <p>One pass, not one pass per placeholder. Iterating the placeholder map and replacing each
     * token separately looks equivalent but is not: a value substituted early is still part of the
     * text when the next token is processed, so a value that itself contains {@code {other}} would
     * be substituted again. With player names flowing into these texts that is an injection point,
     * and it is why substituted content here is never re-examined.
     */
    private static String substituteInOnePass(String text, Map<String, String> placeholders) {
        StringBuilder result = new StringBuilder(text.length() + 16);
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current != '{') {
                result.append(current);
                index++;
                continue;
            }
            int closing = text.indexOf('}', index + 1);
            if (closing < 0) {
                // unbalanced brace - copy the rest verbatim rather than guessing
                result.append(text, index, text.length());
                break;
            }
            String name = text.substring(index + 1, closing);
            String replacement = placeholders.get(name);
            if (replacement == null) {
                // unknown placeholder: keep it visible instead of blanking it
                result.append(text, index, closing + 1);
            } else {
                result.append(replacement);
            }
            index = closing + 1;
        }
        return result.toString();
    }
}

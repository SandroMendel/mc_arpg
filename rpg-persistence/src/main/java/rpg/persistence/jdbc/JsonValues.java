package rpg.persistence.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON encoding for the two JSONB columns in this schema.
 *
 * <p>Hand-written rather than pulling in a JSON library: the shipped jar carries no third-party
 * classes (ADR-010), and the payloads here are flat maps of primitives and strings. A block that
 * later needs richer documents should declare a proper library in {@code libraries:} rather than
 * grow this.
 */
final class JsonValues {

    private JsonValues() {}

    /** Encodes a flat map as a JSON object. */
    static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(encode(entry.getValue()));
        }
        return json.append('}').toString();
    }

    /**
     * Decodes a flat JSON object.
     *
     * <p>Values come back as {@code String}, {@code Double}, {@code Boolean} or {@code null}. The
     * caller knows the template and interprets accordingly; B02 never inspects these values.
     */
    static Map<String, Object> fromJson(String json) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return values;
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String pair : splitTopLevel(body)) {
            int colon = indexOfUnquoted(pair, ':');
            if (colon < 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon).trim());
            values.put(key, decode(pair.substring(colon + 1).trim()));
        }
        return values;
    }

    private static String encode(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    private static Object decode(String raw) {
        if ("null".equals(raw)) {
            return null;
        }
        if ("true".equals(raw) || "false".equals(raw)) {
            return Boolean.valueOf(raw);
        }
        if (raw.startsWith("\"")) {
            return unquote(raw);
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException notANumber) {
            return raw;
        }
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    private static String unquote(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("\"")) {
            return trimmed;
        }
        StringBuilder plain = new StringBuilder();
        for (int i = 1; i < trimmed.length() - 1; i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length() - 1) {
                char next = trimmed.charAt(++i);
                plain.append(
                        switch (next) {
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            default -> next;
                        });
            } else {
                plain.append(c);
            }
        }
        return plain.toString();
    }

    private static java.util.List<String> splitTopLevel(String body) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (c == ',' && !inString) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        if (start < body.length()) {
            parts.add(body.substring(start));
        }
        return parts;
    }

    private static int indexOfUnquoted(String text, char target) {
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (c == target && !inString) {
                return i;
            }
        }
        return -1;
    }
}

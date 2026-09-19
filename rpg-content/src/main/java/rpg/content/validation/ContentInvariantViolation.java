package rpg.content.validation;

import java.nio.file.Path;
import java.util.Objects;

import rpg.core.config.ConfigValidationException;

/** Structured failure for a named B16 domain invariant. */
public final class ContentInvariantViolation extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    private final String invariantName;
    private final String sourceDomain;
    private final String sourceId;
    private final String targetDomain;
    private final String targetId;

    public ContentInvariantViolation(
            Path sourceFile,
            String invariantName,
            String sourceDomain,
            String sourceId,
            String targetDomain,
            String targetId,
            String yamlPath,
            String expected,
            String actual) {
        super(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                safe(yamlPath, "yamlPath"),
                "invariant "
                        + safe(invariantName, "invariantName")
                        + " ("
                        + safe(sourceDomain, "sourceDomain")
                        + "/"
                        + safe(sourceId, "sourceId")
                        + " -> "
                        + safe(targetDomain, "targetDomain")
                        + "/"
                        + safe(targetId, "targetId")
                        + "): "
                        + safe(expected, "expected"),
                safe(actual, "actual"));
        this.invariantName = safe(invariantName, "invariantName");
        this.sourceDomain = safe(sourceDomain, "sourceDomain");
        this.sourceId = safe(sourceId, "sourceId");
        this.targetDomain = safe(targetDomain, "targetDomain");
        this.targetId = safe(targetId, "targetId");
    }

    public ContentInvariantViolation(
            Path sourceFile,
            String invariantName,
            InvariantReference reference,
            String expected,
            String actual) {
        this(
                sourceFile,
                invariantName,
                Objects.requireNonNull(reference, "reference").sourceDomain(),
                reference.sourceId(),
                reference.targetDomain(),
                reference.targetId(),
                reference.yamlPath(),
                expected,
                actual);
    }

    public String invariantName() {
        return invariantName;
    }

    public String sourceDomain() {
        return sourceDomain;
    }

    public String sourceId() {
        return sourceId;
    }

    public String targetDomain() {
        return targetDomain;
    }

    public String targetId() {
        return targetId;
    }

    private static String safe(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

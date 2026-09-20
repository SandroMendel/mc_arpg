package rpg.content;

import java.util.Objects;

/**
 * Immutable metadata for one cross-domain content reference.
 *
 * <p>The reference deliberately describes, but does not resolve, the relationship. T014 owns
 * cross-domain validation and can use this complete context to report both sides of a failed
 * lookup without recovering information from a parsed YAML tree.
 *
 * @param sourceDomain domain containing the reference
 * @param sourceId stable identifier of the source entry
 * @param targetDomain domain expected to contain the referenced entry
 * @param targetId stable identifier that must resolve
 * @param path exact YAML document path of the reference
 * @param requirement human-readable existence or compatibility requirement
 */
public record ContentReference(
        String sourceDomain,
        String sourceId,
        String targetDomain,
        String targetId,
        String path,
        String requirement) {

    public ContentReference {
        requireText(sourceDomain, "sourceDomain");
        requireText(sourceId, "sourceId");
        requireText(targetDomain, "targetDomain");
        requireText(targetId, "targetId");
        requireText(path, "path");
        requireText(requirement, "requirement");
    }

    /** Alias matching the validation vocabulary used by the T012 invariant primitives. */
    public String yamlPath() {
        return path;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

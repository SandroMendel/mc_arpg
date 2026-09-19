package rpg.content.validation;

import java.util.Objects;

/**
 * Immutable observation of one required cross-domain reference.
 *
 * <p>The observation deliberately contains no bound domain object. T014 can create observations
 * from whichever typed domain model it owns, while the invariant validator remains server-free
 * and independent of the current binders.
 *
 * @param sourceDomain domain containing the reference
 * @param sourceId stable identifier of the source entry
 * @param targetDomain domain containing the referenced entry
 * @param targetId stable identifier that must exist
 * @param yamlPath exact YAML path of the reference
 * @param requirement human-readable requirement represented by this observation
 * @param requiredScope optional scope that the target must belong to
 * @param requiredCapability optional capability that the target must expose
 */
public record InvariantReference(
        String sourceDomain,
        String sourceId,
        String targetDomain,
        String targetId,
        String yamlPath,
        String requirement,
        String requiredScope,
        String requiredCapability) {

    public InvariantReference(
            String sourceDomain,
            String sourceId,
            String targetDomain,
            String targetId,
            String yamlPath,
            String requirement) {
        this(sourceDomain, sourceId, targetDomain, targetId, yamlPath, requirement, null, null);
    }

    public InvariantReference {
        requireText(sourceDomain, "sourceDomain");
        requireText(sourceId, "sourceId");
        requireText(targetDomain, "targetDomain");
        requireText(targetId, "targetId");
        requireText(yamlPath, "yamlPath");
        requireText(requirement, "requirement");
        if (requiredScope != null && requiredScope.isBlank()) {
            throw new IllegalArgumentException("requiredScope must not be blank");
        }
        if (requiredCapability != null && requiredCapability.isBlank()) {
            throw new IllegalArgumentException("requiredCapability must not be blank");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

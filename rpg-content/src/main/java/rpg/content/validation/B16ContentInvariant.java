package rpg.content.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import rpg.content.ContentRegistryContract;

/**
 * Named, server-free cross-domain invariants identified by the B16 inventory.
 *
 * <p>Each executable relation owns its source domain, target domain, target key type, and target
 * registry identity. The validator accepts the old simple ID-set form and a richer descriptor
 * form for T014 scope/capability checks.
 */
public enum B16ContentInvariant {
    CLASSES_TO_ABILITIES("classes", "abilities", ContentRegistryContract.KeyType.ABILITY_ID, "abilities.<abilityId>", true, "class ability references must resolve to an ability ID"),
    MOB_HORDES_TO_ZONES("mobs", "zones", ContentRegistryContract.KeyType.ZONE_ID, "zones.<zoneId>", true, "horde zone references must resolve to a zone ID"),
    MOB_HORDES_TO_SPAWN_AREAS("mobs", "zones", ContentRegistryContract.KeyType.SPAWN_AREA_ID, "zones.<zoneId>.spawn-areas[].key", true, "horde spawn-area references must resolve within their zone"),
    MOB_HORDES_TO_KINDS("mobs", "mobs", ContentRegistryContract.KeyType.MOB_KIND_ID, "kinds.<mobKindId>", true, "horde/boss references must resolve to a mob kind with required capabilities"),
    ITEM_LOOT_TO_TEMPLATES("items", "items", ContentRegistryContract.KeyType.ITEM_TEMPLATE_ID, "templates.<itemTemplateId>", true, "loot entries must resolve to an item-template ID"),
    ITEM_LOOT_TO_ZONES("items", "zones", ContentRegistryContract.KeyType.ZONE_ID, "zones.<zoneId>", true, "zone loot registries must resolve to a zone ID"),
    ITEM_LOOT_TO_KINDS("items", "mobs", ContentRegistryContract.KeyType.MOB_KIND_ID, "kinds.<mobKindId>", true, "mob-kind loot registries must resolve to a mob-kind ID"),
    ITEM_VENDORS_TO_ZONES("items", "zones", ContentRegistryContract.KeyType.ZONE_ID, "zones.<zoneId>", true, "vendor registries must resolve to a zone ID"),
    ITEM_VENDORS_TO_TEMPLATES("items", "items", ContentRegistryContract.KeyType.ITEM_TEMPLATE_ID, "templates.<itemTemplateId>", true, "vendor entries must resolve to an item-template ID"),
    MOB_KINDS_TO_MESSAGES(null, null, null, null, false, "text/translation references remain outside B16 by decision");

    private static final Comparator<InvariantReference> REFERENCE_ORDER =
            Comparator.comparing(InvariantReference::yamlPath)
                    .thenComparing(InvariantReference::sourceDomain)
                    .thenComparing(InvariantReference::sourceId)
                    .thenComparing(InvariantReference::targetDomain)
                    .thenComparing(InvariantReference::targetId)
                    .thenComparing(InvariantReference::requirement)
                    .thenComparing(
                            reference ->
                                    reference.requiredScope() == null
                                            ? ""
                                            : reference.requiredScope())
                    .thenComparing(
                            reference ->
                                    reference.requiredCapability() == null
                                            ? ""
                                            : reference.requiredCapability());
    private static final Comparator<InvariantTarget> TARGET_ORDER =
            Comparator.comparing((InvariantTarget target) -> target.registryPath() == null ? "" : target.registryPath())
                    .thenComparing(InvariantTarget::domain)
                    .thenComparing(InvariantTarget::id)
                    .thenComparing(InvariantTarget::keyType)
                    .thenComparing(target -> target.scope() == null ? "" : target.scope())
                    .thenComparing(target -> String.join("\u001f", target.capabilities()));

    private final String sourceDomain;
    private final String targetDomain;
    private final ContentRegistryContract.KeyType targetKeyType;
    private final String targetRegistryPath;
    private final boolean requiresTargetScope;
    private final boolean inScope;
    private final String description;

    B16ContentInvariant(
            String sourceDomain,
            String targetDomain,
            ContentRegistryContract.KeyType targetKeyType,
            String targetRegistryPath,
            boolean inScope,
            String description) {
        this.sourceDomain = sourceDomain;
        this.targetDomain = targetDomain;
        this.targetKeyType = targetKeyType;
        this.targetRegistryPath = targetRegistryPath;
        this.requiresTargetScope =
                targetKeyType == ContentRegistryContract.KeyType.SPAWN_AREA_ID;
        this.inScope = inScope;
        this.description = description;
    }

    public boolean inScope() {
        return inScope;
    }

    public String description() {
        return description;
    }

    public String sourceDomain() {
        return sourceDomain;
    }

    public String targetDomain() {
        return targetDomain;
    }

    public ContentRegistryContract.KeyType targetKeyType() {
        return targetKeyType;
    }

    public String targetRegistryPath() {
        return targetRegistryPath;
    }

    /** Validates the backwards-compatible simple target-ID set. */
    public void validate(Path sourceFile, Iterable<InvariantReference> references, Set<String> targetIds)
            throws ContentInvariantViolation {
        Objects.requireNonNull(targetIds, "targetIds");
        List<InvariantTarget> targets = new ArrayList<>();
        for (String targetId : targetIds) {
            targets.add(new InvariantTarget(targetDomain, targetId, targetRegistryPath, targetKeyType));
        }
        validateTargets(sourceFile, references, targets);
    }

    /** Validates rich target descriptors, including optional registry, scope and capabilities. */
    public void validateTargets(
            Path sourceFile, Iterable<InvariantReference> references, Iterable<InvariantTarget> targets)
            throws ContentInvariantViolation {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(targets, "targets");
        ensureInScope();

        List<InvariantReference> orderedReferences = materializeReferences(references);
        List<InvariantTarget> orderedTargets = materializeTargets(targets);
        for (InvariantReference reference : orderedReferences) {
            validateReferenceContract(sourceFile, reference);
            List<InvariantTarget> idMatches = orderedTargets.stream()
                    .filter(candidate -> candidate.id().equals(reference.targetId()))
                    .toList();
            if (idMatches.isEmpty()) {
                throw violation(sourceFile, reference, "target ID must exist in the " + targetDomain + " registry", "unknown target ID '" + reference.targetId() + "'");
            }
            InvariantTarget target = idMatches.stream()
                    .filter(this::matchesRegistryIdentity)
                    .findFirst()
                    .orElse(idMatches.get(0));
            validateTargetDescriptor(sourceFile, reference, target);
        }
    }

    private void validateReferenceContract(Path sourceFile, InvariantReference reference)
            throws ContentInvariantViolation {
        if (!sourceDomain.equals(reference.sourceDomain())) {
            throw violation(sourceFile, reference, "source domain must be " + sourceDomain, "source domain was '" + reference.sourceDomain() + "'");
        }
        if (!targetDomain.equals(reference.targetDomain())) {
            throw violation(sourceFile, reference, "target domain must be " + targetDomain, "target domain was '" + reference.targetDomain() + "'");
        }
        if (requiresTargetScope && reference.requiredScope() == null) {
            throw violation(sourceFile, reference, "reference must specify the owning target scope", "target scope was not specified");
        }
    }

    private void validateTargetDescriptor(
            Path sourceFile, InvariantReference reference, InvariantTarget target)
            throws ContentInvariantViolation {
        if (!target.id().equals(reference.targetId())) {
            throw violation(sourceFile, reference, "target descriptor ID must match the reference", "target descriptor ID was '" + target.id() + "'");
        }
        if (!targetDomain.equals(target.domain())) {
            throw violation(sourceFile, reference, "target descriptor domain must be " + targetDomain, "target descriptor domain was '" + target.domain() + "'");
        }
        if (targetKeyType != target.keyType()) {
            throw violation(sourceFile, reference, "target key type must be " + targetKeyType, "target key type was " + target.keyType());
        }
        if (!registryMatches(target.registryPath())) {
            throw violation(sourceFile, reference, "target registry must match " + targetRegistryPath, "target registry was '" + target.registryPath() + "'");
        }
        if (reference.requiredScope() != null && !reference.requiredScope().equals(target.scope())) {
            throw violation(sourceFile, reference, "target must belong to scope '" + reference.requiredScope() + "'", "target scope was '" + target.scope() + "'");
        }
        if (reference.requiredCapability() != null && !target.capabilities().contains(reference.requiredCapability())) {
            throw violation(sourceFile, reference, "target must expose capability '" + reference.requiredCapability() + "'", "target capabilities were " + target.capabilities());
        }
    }

    private boolean matchesRegistryIdentity(InvariantTarget target) {
        return targetDomain.equals(target.domain())
                && targetKeyType == target.keyType()
                && targetRegistryPath.equals(target.registryPath());
    }

    private boolean registryMatches(String registryPath) {
        return registryPath.equals(targetRegistryPath);
    }

    private ContentInvariantViolation violation(
            Path sourceFile, InvariantReference reference, String expected, String actual) {
        return new ContentInvariantViolation(sourceFile, name(), reference, expected, actual);
    }

    private void ensureInScope() {
        if (!inScope) {
            throw new IllegalStateException(name() + " is explicitly out-of-scope: " + description);
        }
    }

    private static List<InvariantReference> materializeReferences(Iterable<InvariantReference> references) {
        List<InvariantReference> ordered = new ArrayList<>();
        for (InvariantReference reference : references) {
            ordered.add(Objects.requireNonNull(reference, "references entry"));
        }
        ordered.sort(REFERENCE_ORDER);
        return ordered;
    }

    private static List<InvariantTarget> materializeTargets(Iterable<InvariantTarget> targets) {
        List<InvariantTarget> ordered = new ArrayList<>();
        for (InvariantTarget target : targets) {
            ordered.add(Objects.requireNonNull(target, "targets entry"));
        }
        ordered.sort(TARGET_ORDER);
        return ordered;
    }
}

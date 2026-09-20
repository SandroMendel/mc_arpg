package rpg.content.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import rpg.content.ContentDocumentContract;
import rpg.content.ContentKeyPolicy;
import rpg.content.ContentRegistryContract;

/** Minimal, deterministic policy for typed B16 registry IDs. */
public final class StableContentIdPolicy {

    private static final Comparator<StableIdCandidate> CANDIDATE_ORDER =
            Comparator.comparing(StableIdCandidate::yamlPath)
                    .thenComparing(StableIdCandidate::domain)
                    .thenComparing(StableIdCandidate::id)
                    .thenComparing(candidate -> candidate.keyType().name())
                    .thenComparing(StableIdCandidate::registryPath);

    private StableContentIdPolicy() {}

    /** Validates one stable ID against the current B16 registry contracts. */
    public static void validate(Path sourceFile, StableIdCandidate candidate)
            throws ContentInvariantViolation {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.id().isBlank() || containsWhitespaceOrControl(candidate.id())) {
            throw invalidId(sourceFile, candidate, "stable ID must be non-blank and contain no whitespace or control character");
        }
        validateRegistryContract(sourceFile, candidate);
    }

    /** Validates IDs and rejects duplicates inside the same domain, type, and registry identity. */
    public static void validateAll(Path sourceFile, Iterable<StableIdCandidate> candidates)
            throws ContentInvariantViolation {
        validateAll(sourceFile, candidates, null);
    }

    /** Validates IDs with an optional caller-supplied allowlist for VANILLA_MOB_TYPE. */
    public static void validateAll(
            Path sourceFile,
            Iterable<StableIdCandidate> candidates,
            Set<String> allowedVanillaMobTypes)
            throws ContentInvariantViolation {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(candidates, "candidates");
        if (allowedVanillaMobTypes != null) {
            for (String allowed : allowedVanillaMobTypes) {
                Objects.requireNonNull(allowed, "allowedVanillaMobTypes entry");
            }
        }
        List<StableIdCandidate> ordered = new ArrayList<>();
        for (StableIdCandidate candidate : candidates) {
            ordered.add(Objects.requireNonNull(candidate, "candidates entry"));
        }
        ordered.sort(CANDIDATE_ORDER);
        Set<IdKey> seen = new HashSet<>();
        for (StableIdCandidate candidate : ordered) {
            validate(sourceFile, candidate);
            if (candidate.keyType() == ContentRegistryContract.KeyType.VANILLA_MOB_TYPE
                    && allowedVanillaMobTypes != null
                    && !allowedVanillaMobTypes.contains(candidate.id())) {
                throw invalidId(sourceFile, candidate, "VANILLA_MOB_TYPE must be present in the caller-supplied allowlist");
            }
            IdKey key = new IdKey(candidate.domain(), candidate.keyType(), candidate.registryPath(), candidate.id());
            if (!seen.add(key)) {
                throw new ContentInvariantViolation(
                        sourceFile,
                        "STABLE_ID_UNIQUENESS",
                        candidate.domain(),
                        candidate.id(),
                        candidate.domain(),
                        candidate.id(),
                        candidate.yamlPath(),
                        "one unique " + candidate.keyType() + " ID per registry path",
                        "duplicate ID '" + candidate.id() + "' in registry '" + candidate.registryPath() + "'");
            }
        }
    }

    private static ContentInvariantViolation invalidId(
            Path sourceFile, StableIdCandidate candidate, String expected) {
        return new ContentInvariantViolation(
                sourceFile,
                "STABLE_ID_SYNTAX",
                candidate.domain(),
                candidate.id(),
                candidate.domain(),
                candidate.id(),
                candidate.yamlPath(),
                expected,
                "invalid ID '" + candidate.id() + "'");
    }

    private static void validateRegistryContract(Path sourceFile, StableIdCandidate candidate)
            throws ContentInvariantViolation {
        ContentDocumentContract document = null;
        for (ContentDocumentContract candidateDocument : ContentKeyPolicy.defaultPolicy().documents()) {
            if (candidateDocument.documentType().equals(candidate.domain())) {
                document = candidateDocument;
                break;
            }
        }
        if (document == null) {
            throw invalidRegistry(
                    sourceFile,
                    candidate,
                    "domain must match a declared B16 document type",
                    "unknown document type '" + candidate.domain() + "'");
        }
        boolean matches =
                document.dynamicRegistries().stream()
                        .anyMatch(
                                registry ->
                                        registry.path().equals(candidate.registryPath())
                                                && registry.keyType() == candidate.keyType());
        if (!matches) {
            throw invalidRegistry(
                    sourceFile,
                    candidate,
                    "registry path and KeyType must match a declared registry of "
                            + document.documentType(),
                    "undeclared registry '"
                            + candidate.registryPath()
                            + "' with KeyType "
                            + candidate.keyType());
        }
    }

    private static ContentInvariantViolation invalidRegistry(
            Path sourceFile, StableIdCandidate candidate, String expected, String actual) {
        return new ContentInvariantViolation(
                sourceFile,
                "STABLE_ID_REGISTRY",
                candidate.domain(),
                candidate.id(),
                candidate.domain(),
                candidate.id(),
                candidate.yamlPath(),
                expected,
                actual);
    }

    private static boolean containsWhitespaceOrControl(String value) {
        return value.codePoints().anyMatch(character -> Character.isWhitespace(character)
                || Character.isSpaceChar(character)
                || Character.isISOControl(character));
    }

    private record IdKey(
            String domain,
            ContentRegistryContract.KeyType keyType,
            String registryPath,
            String id) {}
}

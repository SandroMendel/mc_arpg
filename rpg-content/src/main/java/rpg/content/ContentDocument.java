package rpg.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One immutable, validated B16 document and the reference metadata produced from that version.
 *
 * <p>The bound content is retained with its concrete type and identity. B16 binders supply the
 * already-immutable core content value; this document defensively owns every surrounding
 * collection. References are deliberately part of the document rather than a separately
 * replaceable snapshot collection, preventing document and reference generations from drifting.
 *
 * @param source relative runtime source name, for example {@code mobs.yml}
 * @param domain fixed B16 domain owned by the document
 * @param schemaVersion positive root schema version
 * @param content validated, immutable typed document content
 * @param registry immutable aggregate of the document's typed registries
 * @param references immutable cross-domain reference metadata observed in this document
 * @param <T> bound content type
 */
public record ContentDocument<T>(
        String source,
        String domain,
        int schemaVersion,
        T content,
        ContentRegistry registry,
        List<ContentReference> references) {

    public ContentDocument(
            String source,
            String domain,
            int schemaVersion,
            T content,
            ContentRegistry registry) {
        this(source, domain, schemaVersion, content, registry, List.of());
    }

    public ContentDocument(String source, String domain, int schemaVersion, T content) {
        this(
                source,
                domain,
                schemaVersion,
                content,
                ContentRegistry.empty(domain),
                List.of());
    }

    public ContentDocument {
        requireText(source, "source");
        requireText(domain, "domain");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(registry, "registry");
        if (!domain.equals(registry.domain())) {
            throw new IllegalArgumentException(
                    "document domain "
                            + domain
                            + " does not match registry domain "
                            + registry.domain());
        }

        Objects.requireNonNull(references, "references");
        List<ContentReference> copy = new ArrayList<>(references.size());
        for (ContentReference reference : references) {
            Objects.requireNonNull(reference, "reference");
            if (!domain.equals(reference.sourceDomain())) {
                throw new IllegalArgumentException(
                        "document domain "
                                + domain
                                + " does not match reference source domain "
                                + reference.sourceDomain()
                                + " at "
                                + reference.path());
            }
            copy.add(reference);
        }
        references = List.copyOf(copy);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

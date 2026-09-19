package rpg.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, jointly published view of a complete generation of B16 content documents.
 *
 * <p>A snapshot accepts documents only. Its reference view is derived from and captured with
 * those exact immutable document instances, so callers cannot publish new documents alongside a
 * separately supplied reference collection from an older generation. Runtime staging and the
 * decision whether to replace an active snapshot remain T015/T016 responsibilities.
 */
public final class ContentSnapshot {

    private final List<ContentDocument<?>> documents;
    private final Map<String, ContentDocument<?>> documentsBySource;
    private final Map<String, ContentDocument<?>> documentsByDomain;
    private final List<ContentReference> references;

    public ContentSnapshot(Collection<? extends ContentDocument<?>> documents) {
        Objects.requireNonNull(documents, "documents");

        List<ContentDocument<?>> ordered = new ArrayList<>(documents.size());
        Map<String, ContentDocument<?>> bySource = new LinkedHashMap<>();
        Map<String, ContentDocument<?>> byDomain = new LinkedHashMap<>();
        List<ContentReference> capturedReferences = new ArrayList<>();

        for (ContentDocument<?> document : documents) {
            Objects.requireNonNull(document, "document");
            if (bySource.putIfAbsent(document.source(), document) != null) {
                throw new IllegalArgumentException(
                        "duplicate content document source: " + document.source());
            }
            if (byDomain.putIfAbsent(document.domain(), document) != null) {
                throw new IllegalArgumentException(
                        "duplicate content document domain: " + document.domain());
            }
            ordered.add(document);
            capturedReferences.addAll(document.references());
        }

        this.documents = List.copyOf(ordered);
        this.documentsBySource = Collections.unmodifiableMap(bySource);
        this.documentsByDomain = Collections.unmodifiableMap(byDomain);
        this.references = List.copyOf(capturedReferences);
    }

    public static ContentSnapshot empty() {
        return new ContentSnapshot(List.of());
    }

    public List<ContentDocument<?>> documents() {
        return documents;
    }

    public List<ContentReference> references() {
        return references;
    }

    public Optional<ContentDocument<?>> findBySource(String source) {
        if (source == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(documentsBySource.get(source));
    }

    public ContentDocument<?> requireBySource(String source) {
        return findBySource(source)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unknown content document source: " + source));
    }

    public Optional<ContentDocument<?>> findByDomain(String domain) {
        if (domain == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(documentsByDomain.get(domain));
    }

    public ContentDocument<?> requireByDomain(String domain) {
        return findByDomain(domain)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unknown content document domain: " + domain));
    }

    public int size() {
        return documents.size();
    }
}

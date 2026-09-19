package rpg.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable structural contract for one bundled B16 content document.
 *
 * <p>The lists contain names and path patterns only. The contract deliberately has no YAML,
 * Bukkit, Paper, or plugin dependency and does not imply that a runtime loader already enforces
 * the declaration.
 *
 * @param fileName bundled runtime filename, for example {@code classes.yml}
 * @param documentType stable B16 document/domain name
 * @param reservedRootKeys root keys reserved by the document envelope
 * @param fixedRootKeys fixed root section keys
 * @param fixedPaths documented fixed section/path patterns
 * @param dynamicRegistries explicitly permitted dynamic mapping-key paths
 */
public record ContentDocumentContract(
        String fileName,
        String documentType,
        List<String> reservedRootKeys,
        List<String> fixedRootKeys,
        List<String> fixedPaths,
        List<ContentRegistryContract> dynamicRegistries) {

    public ContentDocumentContract {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(documentType, "documentType");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (documentType.isBlank()) {
            throw new IllegalArgumentException("documentType must not be blank");
        }

        reservedRootKeys = copyDistinct("reservedRootKeys", reservedRootKeys);
        fixedRootKeys = copyDistinct("fixedRootKeys", fixedRootKeys);
        fixedPaths = copyDistinct("fixedPaths", fixedPaths);
        dynamicRegistries = copyDistinctRegistries(dynamicRegistries);

        if (!reservedRootKeys.contains("schemaVersion")) {
            throw new IllegalArgumentException(
                    "reservedRootKeys must contain schemaVersion for " + fileName);
        }
    }

    /** Returns the registry declaration for a path pattern, if present. */
    public java.util.Optional<ContentRegistryContract> registry(String path) {
        if (path == null) {
            return java.util.Optional.empty();
        }
        return dynamicRegistries.stream().filter(registry -> registry.path().equals(path)).findFirst();
    }

    private static List<String> copyDistinct(String name, List<String> values) {
        Objects.requireNonNull(values, name);
        List<String> copy = new ArrayList<>(values.size());
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            Objects.requireNonNull(value, name + " entry");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not contain blank entries");
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(name + " contains duplicate entry: " + value);
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static List<ContentRegistryContract> copyDistinctRegistries(
            List<ContentRegistryContract> values) {
        Objects.requireNonNull(values, "dynamicRegistries");
        List<ContentRegistryContract> copy = new ArrayList<>(values.size());
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (ContentRegistryContract value : values) {
            Objects.requireNonNull(value, "dynamicRegistries entry");
            if (!paths.add(value.path())) {
                throw new IllegalArgumentException(
                        "dynamicRegistries contains duplicate path: " + value.path());
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }
}

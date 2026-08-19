package rpg.core.config;

import java.util.List;
import java.util.Map;

/**
 * Read-only access to a configuration document that has already been validated against its
 * {@link ConfigSchema}.
 *
 * <p>Every accessor is total for a path declared in the schema: validation has already guaranteed
 * presence and type, so the getters do not throw a checked exception. Asking for a path the schema
 * does not declare is a programming error and fails with {@link IllegalArgumentException}.
 */
public interface ConfigView {

    /** Schema version the underlying document was validated against. */
    int schemaVersion();

    String getString(String path);

    boolean getBoolean(String path);

    int getInt(String path);

    long getLong(String path);

    double getDouble(String path);

    List<?> getList(String path);

    Map<?, ?> getMap(String path);
}

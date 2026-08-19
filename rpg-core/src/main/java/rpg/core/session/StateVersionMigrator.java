package rpg.core.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Brings stored records up to the format this build writes (FR-025 to FR-027).
 *
 * <p>A step per version, applied in order. The alternative - one function per pair of versions -
 * grows quadratically and is how migration paths quietly stop covering older saves.
 *
 * <p>A record from a <em>newer</em> build is not migrated but refused. There is no way to guess
 * what a future format meant, and interpreting it with today's rules would corrupt it silently;
 * that check happens before this class is reached.
 */
public final class StateVersionMigrator {

    private final Map<Integer, UnaryOperator<PlayerCharacter>> steps = new HashMap<>();
    private final Logger logger;

    public StateVersionMigrator(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Registers the step that lifts a record from {@code fromVersion} to {@code fromVersion + 1}.
     *
     * <p>No step is registered today because version 1 is the first format. The mechanism exists
     * from the start regardless: a migration path added later cannot reach the records that were
     * already written without it.
     */
    public StateVersionMigrator register(int fromVersion, UnaryOperator<PlayerCharacter> step) {
        if (fromVersion < 1 || fromVersion >= PlayerCharacter.CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException(
                    "a migration step must lift a version below the current one, but was "
                            + fromVersion);
        }
        steps.put(fromVersion, Objects.requireNonNull(step, "step"));
        return this;
    }

    /** Migrates every character in the bundle that needs it. */
    public SessionBundle migrate(SessionBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        if (bundle.characters().stream().noneMatch(PlayerCharacter::needsMigration)) {
            return bundle;
        }
        List<PlayerCharacter> migrated = new ArrayList<>(bundle.characters().size());
        for (PlayerCharacter character : bundle.characters()) {
            migrated.add(migrateOne(character));
        }
        return new SessionBundle(
                bundle.playerId(), bundle.accountState(), migrated, bundle.items());
    }

    /** Whether migrating changed anything - the caller then writes the new format back (FR-026). */
    public boolean migratedAnything(SessionBundle before, SessionBundle after) {
        return !before.characters().equals(after.characters());
    }

    private PlayerCharacter migrateOne(PlayerCharacter character) {
        PlayerCharacter current = character;
        while (current.needsMigration()) {
            UnaryOperator<PlayerCharacter> step = steps.get(current.dataVersion());
            if (step == null) {
                throw new UnknownDataVersionException(
                        current.dataVersion(), PlayerCharacter.CURRENT_DATA_VERSION);
            }
            int before = current.dataVersion();
            current = step.apply(current);
            if (current.dataVersion() <= before) {
                // A step that does not advance the version would loop forever.
                throw new IllegalStateException(
                        "migration step from version " + before + " did not advance the version");
            }
            logger.fine(
                    "[session] migrated character "
                            + current.characterId()
                            + " from version "
                            + before
                            + " to "
                            + current.dataVersion());
        }
        return current;
    }
}

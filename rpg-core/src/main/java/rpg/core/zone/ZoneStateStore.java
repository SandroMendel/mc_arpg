package rpg.core.zone;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory authority for what a character carries out of this block.
 *
 * <p><b>While a character is online, this is the truth and the database is a copy</b> (Constitution
 * IV.2). Every read a game event makes is answered from here; the repository is only asked once, when
 * the session starts, and only told about changes afterwards. That is what keeps FR-063 - no database
 * access per movement, per zone change, per warning, per right-click.
 *
 * <p><b>A character who has not been loaded answers "nothing".</b> Not an exception and not a
 * blocking read: a right-click arriving a tick before the load finished should do nothing rather than
 * stall the tick, and the next one works. {@link #isNewCharacter} tells the two cases apart, because
 * "not loaded yet" and "never existed" must not be confused - the second one decides whether somebody
 * starts in the start region (FR-037b).
 */
public final class ZoneStateStore implements Waypoints {

    /** What is held for one loaded character. Mutable, but only from the tick. */
    private static final class Loaded {

        private final Set<String> unlocked = ConcurrentHashMap.newKeySet();
        private volatile String pendingRespawnZone;
        private volatile boolean everSeen;
    }

    private final ZoneStateRepository repository;
    private final Map<UUID, Loaded> loaded = new ConcurrentHashMap<>();

    public ZoneStateStore(ZoneStateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Takes the stored state into memory.
     *
     * <p>Called when a session becomes ready, with whatever the repository found - empty when this
     * block has never placed the character.
     */
    public void load(UUID characterId, Optional<ZoneCharacterState> stored) {
        Loaded state = new Loaded();
        stored.ifPresent(
                found -> {
                    state.unlocked.addAll(found.unlockedCrystals());
                    state.pendingRespawnZone = found.pendingRespawnZone().orElse(null);
                    state.everSeen = true;
                });
        loaded.put(characterId, state);
    }

    /** Drops a character that left. The row stays; this is only the copy. */
    public void unload(UUID characterId) {
        loaded.remove(characterId);
    }

    /**
     * Whether the memory copy is present at all.
     *
     * <p>The flush asks this before writing: an absent copy cannot be newer than the row, so there is
     * nothing to persist and the mark can be retired rather than retried forever.
     */
    public boolean isLoaded(UUID characterId) {
        return loaded.containsKey(characterId);
    }

    /**
     * Whether this block has never placed that character (FR-037b).
     *
     * <p>False for anybody not loaded, so a race cannot make a returning player look new - the worst
     * outcome of getting this wrong is teleporting somebody home who was standing somewhere else.
     */
    public boolean isNewCharacter(UUID characterId) {
        Loaded state = loaded.get(characterId);
        return state != null && !state.everSeen;
    }

    /** Records that the character has now been placed, so the next login knows they are not new. */
    public void markSeen(UUID characterId) {
        Loaded state = loaded.get(characterId);
        if (state == null || state.everSeen) {
            return;
        }
        state.everSeen = true;
        repository.markSeen(characterId);
    }

    // ---------------------------------------------------------------- pending respawn

    /** The region a combat logout owes this character, or empty (ADR-030). */
    public Optional<String> pendingRespawnOf(UUID characterId) {
        Loaded state = loaded.get(characterId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.pendingRespawnZone);
    }

    /** Records that this character died by logging out in the given region (FR-038). */
    public void setPendingRespawn(UUID characterId, String zoneKey) {
        Loaded state = loaded.get(characterId);
        if (state != null) {
            state.pendingRespawnZone = zoneKey;
        }
        // Told to the repository even when the memory copy is already gone: the session may be half
        // torn down by the time a logout is processed, and this is the one write that must not be
        // lost - it is the whole consequence of the rule.
        repository.setPendingRespawn(characterId, zoneKey);
    }

    /** Clears it once it has been applied on the next login (FR-041). */
    public void clearPendingRespawn(UUID characterId) {
        Loaded state = loaded.get(characterId);
        if (state != null) {
            state.pendingRespawnZone = null;
        }
        repository.setPendingRespawn(characterId, null);
    }

    // ---------------------------------------------------------------- waypoints

    @Override
    public boolean isUnlocked(UUID characterId, String crystalKey) {
        Loaded state = loaded.get(characterId);
        return state != null && state.unlocked.contains(crystalKey);
    }

    @Override
    public boolean unlock(UUID characterId, String crystalKey) {
        Loaded state = loaded.get(characterId);
        if (state == null || !state.unlocked.add(crystalKey)) {
            return false;
        }
        repository.unlock(characterId, crystalKey);
        return true;
    }

    @Override
    public Set<String> unlockedBy(UUID characterId) {
        Loaded state = loaded.get(characterId);
        return state == null ? Set.of() : Set.copyOf(new HashSet<>(state.unlocked));
    }
}

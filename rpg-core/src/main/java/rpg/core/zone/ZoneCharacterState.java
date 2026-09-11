package rpg.core.zone;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What one character carries out of this block, between sessions.
 *
 * <p>Two things and no more: the crystals they have discovered, and a respawn that is still owed to
 * them because they logged out in combat (ADR-030).
 *
 * <p><b>The pending respawn is a zone key, not a coordinate.</b> Between the logout and the next
 * login the server may restart and the map may be reconfigured. A stored point would be frozen and
 * meaningless by then; a key resolves against whatever the configuration says at the time, and falls
 * back cleanly when the region is gone (FR-037).
 *
 * <p><b>Absence of this record means the block has never placed that character</b> - which is exactly
 * what tells a new character from a returning one, and therefore what makes the start region apply
 * once and only once (FR-037b). B08b infers newness from a missing balance row in the same way.
 *
 * @param characterId whose state this is
 * @param unlockedCrystals the crystal keys this character may travel to; grows only (FR-051b2)
 * @param pendingRespawnZone the region a combat logout owes them, or empty
 */
public record ZoneCharacterState(
        UUID characterId, Set<String> unlockedCrystals, Optional<String> pendingRespawnZone) {

    public ZoneCharacterState {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(unlockedCrystals, "unlockedCrystals");
        Objects.requireNonNull(pendingRespawnZone, "pendingRespawnZone");
        unlockedCrystals = Set.copyOf(unlockedCrystals);
    }

    /** A character this block has seen before but who owes nothing and has discovered nothing. */
    public static ZoneCharacterState empty(UUID characterId) {
        return new ZoneCharacterState(characterId, Set.of(), Optional.empty());
    }

    public boolean hasUnlocked(String crystalKey) {
        return unlockedCrystals.contains(crystalKey);
    }
}

package rpg.core.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PlayerState;
import rpg.core.stats.CharacterResources;

/**
 * Everything a session needs, read in one go (FR-005).
 *
 * @param playerId the account
 * @param accountState the stored account record, empty for a first-time player
 * @param characters every character of the account, at most one per class
 * @param items the items belonging to those characters
 */
public record SessionBundle(
        UUID playerId,
        Optional<PlayerState> accountState,
        List<PlayerCharacter> characters,
        List<ItemInstance> items,
        List<CharacterResources> resources) {

    public SessionBundle {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(accountState, "accountState");
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    }

    /** A player connecting for the very first time: no record, no characters, no items. */
    public static SessionBundle empty(UUID playerId) {
        return new SessionBundle(playerId, Optional.empty(), List.of(), List.of(), List.of());
    }

    /**
     * The stored resources of one character, or empty if it has none yet - which means new (B04).
     *
     * <p>Carried in this bundle rather than loaded separately because FR-019b needs a calculated
     * holder <em>before</em> the player is released, and this load runs in the pre-login event,
     * before a player object even exists. A second load afterwards would put someone into the world
     * with the wrong health for at least a tick. The bundle already carries {@link ItemInstance},
     * which belongs to B11, for exactly the same reason: it is the one load path, not B03's private
     * property.
     */
    public Optional<CharacterResources> resourcesOf(UUID characterId) {
        return resources.stream().filter(r -> r.characterId().equals(characterId)).findFirst();
    }

    /** Whether this account has never been stored before. */
    public boolean isNewAccount() {
        return accountState.isEmpty();
    }

    /** The character to play, or empty if none exists yet (FR-021). */
    public Optional<PlayerCharacter> preferredCharacter() {
        // Most recently played first; a player with no character gets a session without one rather
        // than a silently invented one.
        return characters.stream()
                .max((a, b) -> a.lastPlayedAt().compareTo(b.lastPlayedAt()));
    }

    /** A character record written by a build newer than this one cannot be interpreted (FR-027). */
    public Optional<PlayerCharacter> anyFromFutureVersion() {
        return characters.stream().filter(PlayerCharacter::isFromFutureVersion).findFirst();
    }
}

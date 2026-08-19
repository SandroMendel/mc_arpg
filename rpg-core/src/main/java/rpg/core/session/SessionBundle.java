package rpg.core.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PlayerState;

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
        List<ItemInstance> items) {

    public SessionBundle {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(accountState, "accountState");
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /** A player connecting for the very first time: no record, no characters, no items. */
    public static SessionBundle empty(UUID playerId) {
        return new SessionBundle(playerId, Optional.empty(), List.of(), List.of());
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

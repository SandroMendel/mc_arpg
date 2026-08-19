package rpg.core.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads from the live session when there is one, from storage otherwise.
 *
 * <p>The order of those two is the whole point (FR-024). Reading storage first would be simpler and
 * would return data up to one autosave interval stale - for a connected player that means a
 * leaderboard showing them behind where they know they are.
 */
public final class DefaultOfflinePlayerReader implements OfflinePlayerReader {

    private final SessionRegistry sessions;
    private final CharacterRepository characters;

    public DefaultOfflinePlayerReader(SessionRegistry sessions, CharacterRepository characters) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.characters = Objects.requireNonNull(characters, "characters");
    }

    @Override
    public CompletableFuture<Optional<PlayerSnapshot>> read(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        Optional<PlayerSession> live = sessions.find(playerId);
        if (live.isPresent()) {
            return CompletableFuture.completedFuture(
                    Optional.of(
                            new PlayerSnapshot(
                                    playerId, live.get().availableCharacters(), true)));
        }

        // No session is opened here - reading must not change what it observes (FR-022).
        return characters
                .findByPlayer(playerId)
                .thenApply(
                        stored ->
                                stored.isEmpty()
                                        ? Optional.empty()
                                        : Optional.of(
                                                new PlayerSnapshot(playerId, stored, false)));
    }

    @Override
    public CompletableFuture<List<PlayerCharacter>> charactersOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return sessions.find(playerId)
                .map(session -> CompletableFuture.completedFuture(session.availableCharacters()))
                .orElseGet(() -> characters.findByPlayer(playerId));
    }
}

package rpg.core.session;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A read-only view of a player, whether or not they are connected.
 *
 * @param playerId the account
 * @param characters their characters
 * @param online whether the data came from a live session or from storage - reported rather than
 *     hidden, so a tool can show the difference instead of pretending there is none
 */
public record PlayerSnapshot(UUID playerId, List<PlayerCharacter> characters, boolean online) {

    public PlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
    }
}

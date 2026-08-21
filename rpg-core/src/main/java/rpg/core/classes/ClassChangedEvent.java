package rpg.core.classes;

import java.util.Objects;
import java.util.UUID;

import rpg.core.session.CharacterClass;

/**
 * A character came into existence with its class.
 *
 * <p>Published on the event bus from B01, <b>not</b> as a Bukkit event - {@code rpg-core} has no
 * Bukkit dependency (Constitution III.1). B13 draws from it, B08 uses it to bind abilities.
 *
 * <p>There is deliberately no counterpart for a class change: the class is permanent (FR-039), so an
 * event for it would describe something that cannot happen.
 */
public record ClassChangedEvent(UUID playerId, UUID characterId, CharacterClass characterClass) {

    public ClassChangedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(characterClass, "characterClass");
    }
}

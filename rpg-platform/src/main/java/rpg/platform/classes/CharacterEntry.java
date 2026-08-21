package rpg.platform.classes;

import org.bukkit.entity.Player;

import rpg.core.session.PlayerCharacter;

/**
 * Letting a player into the game state with the character they just chose (ADR-020).
 *
 * <p>A seam rather than a direct call, because the two halves live in modules that may not see each
 * other: the session lifecycle is in {@code rpg-core} and driven from {@code rpg-persistence}, the
 * equipment is applied through Bukkit here, and the plugin is what introduces them (Constitution
 * III.2). {@link ClassSelectionListener} knows only that entering can fail.
 *
 * <p>Runs on the player's own tick, inside the selection flow.
 */
public interface CharacterEntry {

    /**
     * Takes the character into play: the session activates it, the blocks that hang state off a session
     * build theirs, and the class equipment is put on.
     *
     * <p>No flag for "created" versus "resumed": the inventory is emptied either way. It belongs to the
     * player, not the character, so anything left in it came from a character that is no longer being
     * played - the warrior's loot must not follow its owner into the mage.
     *
     * @return {@code false} if the player did not enter - the selection stays open, which is the only
     *     safe state, because a player in the world without stats or equipment is worse than a player
     *     still looking at a menu
     */
    boolean enter(Player player, PlayerCharacter character);
}

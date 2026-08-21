package rpg.core.progression;

import java.util.UUID;

/**
 * Published exactly once per rise (FR-023).
 *
 * <p>A gain that carries a character from 12 to 15 publishes <b>one</b> event with
 * {@code previousLevel = 12} and {@code newLevel = 15} - not three. Three would turn what a player
 * experienced as one moment into three unlock passes and three messages.
 *
 * <p>{@code byAdmin} separates an operator's intervention from a natural rise (FR-024c). B13 will
 * not want to celebrate a level somebody set by hand, while B12 still wants to count it; without the
 * flag every receiver would have to guess.
 *
 * @param characterId who rose
 * @param playerId the player behind that character
 * @param previousLevel level before
 * @param newLevel level after
 * @param byAdmin whether this came from {@code setProgress} rather than from play
 */
public record LevelUpEvent(
        UUID characterId, UUID playerId, int previousLevel, int newLevel, boolean byAdmin) {

    public int levelsGained() {
        return newLevel - previousLevel;
    }
}

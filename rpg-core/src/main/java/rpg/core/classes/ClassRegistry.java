package rpg.core.classes;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

import rpg.core.message.MessageKey;
import rpg.core.session.CharacterClass;

/**
 * The public entry point of B07 for other blocks - see {@code contracts/class-api.md}.
 *
 * <p>Holds no state of its own beyond the configuration. Everything it answers is either read from an
 * immutable definition or derived from the character's level, which it asks B06 for.
 */
public final class ClassRegistry {

    private final ClassConfig config;
    private final ToIntFunction<UUID> levelOf;

    /**
     * @param levelOf the character's level, supplied by B06. A function rather than a dependency on
     *     the progression type, so this class stays testable without it
     */
    public ClassRegistry(ClassConfig config, ToIntFunction<UUID> levelOf) {
        this.config = Objects.requireNonNull(config, "config");
        this.levelOf = Objects.requireNonNull(levelOf, "levelOf");
    }

    public CharacterClassDefinition definition(CharacterClass id) {
        return config.definition(id);
    }

    /** The message key of the display name - never the text itself (Constitution V). */
    public MessageKey displayNameKey(CharacterClass id) {
        return config.definition(id).displayNameKey();
    }

    /** The full loadout of a class. Empty while B08 has not filled it in (FR-045). */
    public List<AbilityBinding> abilitiesOf(CharacterClass id) {
        return config.definition(id).abilities();
    }

    /**
     * The abilities available to a character right now - <b>derived</b> from its level, never stored
     * (FR-043). A stored unlock state would be a second place holding what the level already says.
     */
    public List<AbilityBinding> unlockedFor(CharacterClass id, UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        return config.definition(id).unlockedAt(levelOf.applyAsInt(characterId));
    }

    public EquipmentLadder ladder(CharacterClass id, LadderSlot slot) {
        return config.definition(id).ladder(slot);
    }

    public ClassConfig config() {
        return config;
    }
}

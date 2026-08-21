package rpg.core.classes;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;

/**
 * One line of the selection: a class, and what the account has made of it.
 *
 * <p>The selection is shown on every join, not only to players without a character (US1.4), so it has
 * to say two different things: "you play this one, here is where it stands" and "this one is free".
 * Both are this record, distinguished by whether {@link #characterId()} is present.
 *
 * <p>Bukkit-free and text-free. What a slot looks like and how its numbers are worded is the platform's
 * business; this is the data behind it.
 *
 * @param characterClass the class this slot stands for - always present, all of them are shown
 * @param characterId the character playing it, empty for a free slot
 * @param level 0 for a free slot
 * @param armorTier 1-based, 0 for a free slot
 * @param weaponTier 1-based, 0 for a free slot
 * @param lastPlayedAt when this character was last in play, empty for a free slot
 */
public record ClassSlot(
        CharacterClass characterClass,
        Optional<UUID> characterId,
        int level,
        int armorTier,
        int weaponTier,
        Optional<Instant> lastPlayedAt) {

    public ClassSlot {
        Objects.requireNonNull(characterClass, "characterClass");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(lastPlayedAt, "lastPlayedAt");
        if (characterId.isEmpty() && (level != 0 || armorTier != 0 || weaponTier != 0)) {
            throw new IllegalArgumentException(
                    "a slot without a character cannot have reached anything, but "
                            + characterClass
                            + " carries level "
                            + level
                            + ", tiers "
                            + armorTier
                            + "/"
                            + weaponTier);
        }
    }

    /** No character of this class yet - the slot is an offer to create one. */
    public static ClassSlot empty(CharacterClass characterClass) {
        return new ClassSlot(characterClass, Optional.empty(), 0, 0, 0, Optional.empty());
    }

    /** A character the account already plays, with what it has reached. */
    public static ClassSlot played(
            CharacterClass characterClass,
            PlayerCharacter character,
            int level,
            int armorTier,
            int weaponTier) {
        Objects.requireNonNull(character, "character");
        return new ClassSlot(
                characterClass,
                Optional.of(character.characterId()),
                level,
                armorTier,
                weaponTier,
                Optional.of(character.lastPlayedAt()));
    }

    /** Whether choosing this slot resumes a character rather than creating one. */
    public boolean isPlayed() {
        return characterId.isPresent();
    }
}

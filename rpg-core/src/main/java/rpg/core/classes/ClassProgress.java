package rpg.core.classes;

import java.util.Objects;
import java.util.UUID;

/**
 * The reached armour and weapon tier of a character - the only mutable, persisted part of B07.
 *
 * <p>Two integers. The class itself already lives in {@code rpg.character} from B03 and is
 * deliberately <b>not</b> repeated here: a second copy would be a second truth.
 *
 * <p><b>The tier is the character's state, the item is derived from it.</b> That direction is
 * one-way, and two properties follow from it that would otherwise be laborious to enforce: a missing
 * bound item heals itself on the next load (FR-023), and there is no way to gain a tier by tampering
 * with an item (Constitution VI).
 *
 * <p>No upper bound is checked here. The ladder length is configuration, and a constant in this class
 * would be wrong the moment a ladder changes. The startup check compares the stored tier against the
 * configured length instead (FR-024).
 *
 * @param characterId the character this belongs to
 * @param armorTier 1-based, 1 for a fresh character
 * @param weaponTier 1-based, independent of the armour tier
 */
public record ClassProgress(
        UUID characterId, int armorTier, int weaponTier, int dataVersion, long revision) {

    public static final int CURRENT_DATA_VERSION = 1;

    /** A fresh character wears tier 1 of both ladders (US3.1). */
    public static final int INITIAL_TIER = 1;

    public ClassProgress {
        Objects.requireNonNull(characterId, "characterId");
        if (armorTier < INITIAL_TIER) {
            throw new IllegalArgumentException(
                    "armorTier must be at least " + INITIAL_TIER + ", but was " + armorTier);
        }
        if (weaponTier < INITIAL_TIER) {
            throw new IllegalArgumentException(
                    "weaponTier must be at least " + INITIAL_TIER + ", but was " + weaponTier);
        }
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    public static ClassProgress initial(UUID characterId) {
        return new ClassProgress(characterId, INITIAL_TIER, INITIAL_TIER, CURRENT_DATA_VERSION, 0L);
    }

    public int tierOf(LadderSlot slot) {
        return slot == LadderSlot.ARMOR ? armorTier : weaponTier;
    }

    /** A copy with one slot advanced by one. The two ladders stay independent (US3.6). */
    public ClassProgress advanced(LadderSlot slot) {
        return slot == LadderSlot.ARMOR
                ? new ClassProgress(characterId, armorTier + 1, weaponTier, dataVersion, revision)
                : new ClassProgress(characterId, armorTier, weaponTier + 1, dataVersion, revision);
    }

    public boolean needsMigration() {
        return dataVersion < CURRENT_DATA_VERSION;
    }

    public boolean isFromFutureVersion() {
        return dataVersion > CURRENT_DATA_VERSION;
    }
}

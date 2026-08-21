package rpg.core.inventory;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * What one character was carrying and storing, as opaque blobs.
 *
 * <p><b>Opaque on purpose.</b> Only the platform can turn an item stack into bytes and back - the
 * format is Bukkit's, versioned by the server, and reproducing it here would mean this module knowing
 * about materials, components and enchantments (Constitution III.1). So the core carries it, stores it
 * and hands it back without ever looking inside.
 *
 * <p>Both containers belong to the <em>player</em> in vanilla, which is precisely the problem: one
 * player has up to three characters, and neither the backpack nor the ender chest may follow them from
 * one to the next.
 *
 * <p>The class equipment is deliberately <em>not</em> in here. It is rebuilt from the reached tier on
 * every entry, so storing it would be a second copy that could disagree - an old tier's sword coming
 * back out of a save after the ladder moved on.
 *
 * @param characterId the character this belongs to
 * @param contents the serialised backpack; empty for a character carrying nothing
 * @param enderChest the serialised ender chest; empty for a character storing nothing
 */
public record CharacterInventory(
        UUID characterId, byte[] contents, byte[] enderChest, int dataVersion, long revision) {

    public static final int CURRENT_DATA_VERSION = 1;

    public CharacterInventory {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(enderChest, "enderChest");
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1, was " + dataVersion);
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative, was " + revision);
        }
        contents = contents.clone();
        enderChest = enderChest.clone();
    }

    /** A character carrying and storing nothing - what a newly created one starts with. */
    public static CharacterInventory empty(UUID characterId) {
        return new CharacterInventory(
                characterId, new byte[0], new byte[0], CURRENT_DATA_VERSION, 0L);
    }

    public static CharacterInventory of(UUID characterId, byte[] contents, byte[] enderChest) {
        return new CharacterInventory(characterId, contents, enderChest, CURRENT_DATA_VERSION, 0L);
    }

    /**
     * A defensive copy, because a byte array in a record is not the immutable value it looks like.
     *
     * <p>Without this the caller holds the very array the flush is about to write, and a change made
     * afterwards would silently alter what gets persisted.
     */
    @Override
    public byte[] contents() {
        return contents.clone();
    }

    @Override
    public byte[] enderChest() {
        return enderChest.clone();
    }

    /** Whether there is nothing at all to restore, in either container. */
    public boolean isEmpty() {
        return contents.length == 0 && enderChest.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof CharacterInventory that
                && characterId.equals(that.characterId)
                && dataVersion == that.dataVersion
                && revision == that.revision
                && Arrays.equals(contents, that.contents)
                && Arrays.equals(enderChest, that.enderChest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                characterId,
                dataVersion,
                revision,
                Arrays.hashCode(contents),
                Arrays.hashCode(enderChest));
    }

    @Override
    public String toString() {
        return "CharacterInventory["
                + characterId
                + ", "
                + contents.length
                + " bytes + "
                + enderChest.length
                + " in the ender chest]";
    }
}

package rpg.core.currency;

import java.util.Objects;
import java.util.UUID;

/**
 * The stored form of a character's balance (FR-001).
 *
 * <p>Shaped like {@code CharacterProgress} in B06 and {@code CharacterResources} in B04: identity,
 * value, data version and revision. The revision only the database cares about, which is why it
 * does not appear anywhere in the booking arithmetic.
 *
 * <p><b>Belongs to the character, never the account</b> (ADR-011). Two characters of one player keep
 * separate purses, the same way they keep separate levels and separate ability ranks. A shared
 * account balance would have made "how much do I have" a question with three answers.
 *
 * @param characterId owner
 * @param balance coins held; never negative, enforced here, in {@link DefaultCurrency} and by a
 *     {@code CHECK} in the table - three places, because this is the promise the whole block rests
 *     on and it has to survive a write path that does not exist yet
 * @param dataVersion format of this record, so an old row can be migrated on load
 * @param revision incremented on every write, as in the other tables
 */
public record CharacterBalance(UUID characterId, long balance, int dataVersion, long revision) {

    public static final int CURRENT_DATA_VERSION = 1;

    public CharacterBalance {
        Objects.requireNonNull(characterId, "characterId");
        if (balance < 0L) {
            throw new IllegalArgumentException("balance must not be negative, but was " + balance);
        }
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    /**
     * A character that has never been stored.
     *
     * <p><b>Zero, not the configured starting balance</b> (FR-011b). The starting balance is a
     * booking made once at character creation (FR-011a); applying it here instead would mean that
     * raising the configured number later silently enriched every character that had never touched
     * a coin - with no booking and no trace. This method is the one place that temptation shows up,
     * so the answer is written down next to it.
     */
    public static CharacterBalance empty(UUID characterId) {
        return new CharacterBalance(characterId, 0L, CURRENT_DATA_VERSION, 0L);
    }

    /** The same balance with a new amount. Records are immutable; a booking produces a new one. */
    public CharacterBalance withBalance(long newBalance) {
        return new CharacterBalance(characterId, newBalance, dataVersion, revision);
    }
}

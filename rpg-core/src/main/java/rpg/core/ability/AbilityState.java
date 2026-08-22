package rpg.core.ability;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a character owns on one ability (FR-062, FR-064).
 *
 * <p>Belongs to the <b>character</b>, not the account (ADR-011): two characters of the same account
 * rank their abilities independently.
 *
 * <p>Split from the runtime state the same way B06 splits {@code CharacterProgress} from
 * {@code ProgressState}: this record carries identity, data version and revision, which only the
 * database cares about. Rage, charges and a running sustained ability are not here at all - they are
 * runtime and none of them survives a logout (ADR-025).
 *
 * @param characterId owner
 * @param abilityId the id from {@code abilities.yml}
 * @param rank at least 1; the ceiling comes from the ability and is not repeated here
 * @param cooldownUntil when the cooldown expires, or {@code null} if none is running
 * @param toggleState the player's setting, or {@code null} for the ability's default
 * @param dataVersion format of this record, so an old row can be migrated on load
 * @param revision incremented on every write, as in the other tables
 */
public record AbilityState(
        UUID characterId,
        String abilityId,
        int rank,
        Instant cooldownUntil,
        ToggleState toggleState,
        int dataVersion,
        long revision) {

    public static final int CURRENT_DATA_VERSION = 1;

    public AbilityState {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(abilityId, "abilityId");
        if (abilityId.isBlank()) {
            throw new IllegalArgumentException("abilityId must not be blank");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1, but was " + rank);
        }
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    /** The state a character has on an ability it has never touched: rank 1 and nothing else. */
    public static AbilityState initial(UUID characterId, String abilityId) {
        return new AbilityState(
                characterId, abilityId, 1, null, null, CURRENT_DATA_VERSION, 0L);
    }

    /** The cooldown, or empty once it has passed. Reading is a comparison, never a countdown. */
    public Optional<Instant> runningCooldown(Instant now) {
        return cooldownUntil != null && cooldownUntil.isAfter(now)
                ? Optional.of(cooldownUntil)
                : Optional.empty();
    }

    /** The player's setting, or {@link ToggleState#ON} when they never changed it. */
    public ToggleState effectiveToggle() {
        return toggleState == null ? ToggleState.ON : toggleState;
    }

    /**
     * Whether this row carries anything worth storing.
     *
     * <p>Rank 1, no cooldown and no toggle is the state of an ability nobody has touched. Writing it
     * would put eighteen rows of pure defaults behind every fresh character, and the load path drops
     * such a row instead (see the V8_1 header).
     */
    public boolean isDefault(Instant now) {
        return rank == 1 && runningCooldown(now).isEmpty() && toggleState == null;
    }

    public AbilityState withRank(int newRank) {
        return new AbilityState(
                characterId, abilityId, newRank, cooldownUntil, toggleState, dataVersion, revision);
    }

    public AbilityState withCooldown(Instant until) {
        return new AbilityState(
                characterId, abilityId, rank, until, toggleState, dataVersion, revision);
    }

    public AbilityState withToggle(ToggleState state) {
        return new AbilityState(
                characterId, abilityId, rank, cooldownUntil, state, dataVersion, revision);
    }
}

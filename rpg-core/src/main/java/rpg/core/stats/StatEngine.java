package rpg.core.stats;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The public interface of B04 - the only way in (FR-040, FR-041).
 *
 * <p>B05 to B13 are built against this. Reaching past it into {@link StatHolder} or
 * {@link DefaultStatEngine} is not allowed (Principle III), and changing anything here is
 * ADR-worthy from now on.
 *
 * <h2>The one thing to get right when using this</h2>
 *
 * <p>Take a snapshot once, at the start of an action, and hold it. A projectile in flight, an
 * ability mid-cast, a multi-stage combat action all finish with the values they started with
 * (FR-021). Re-reading the engine partway through is a bug: it makes the outcome depend on whether
 * a buff happened to expire between two lines of code.
 */
public interface StatEngine {

    // ---------------------------------------------------------------- reading

    /**
     * The current snapshot of a holder.
     *
     * @throws java.util.NoSuchElementException if no such holder exists
     * @throws rpg.core.session.SessionNotReadyException if the holder is a player whose session is
     *     not ready - never default values (FR-037)
     */
    StatSnapshot snapshot(UUID holderId);

    /** Like {@link #snapshot}, but empty instead of throwing for an unknown holder. */
    Optional<StatSnapshot> findSnapshot(UUID holderId);

    /** Convenience for a single value; same rules as {@link #snapshot}. */
    double value(UUID holderId, Attribute attribute);

    /** Which sources contribute to one attribute, and how much (FR-010). Triggers no calculation. */
    List<AttributeContribution> contributions(UUID holderId, Attribute attribute);

    // ----------------------------------------------------------- contributing

    /** Sets one source's contributions; an existing set with the same id is replaced (FR-008). */
    void apply(UUID holderId, ModifierSet set);

    /** Sets several sources at once. Convenience only - bundling does not depend on it (FR-019a). */
    void applyAll(UUID holderId, Collection<ModifierSet> sets);

    /** Removes one source (FR-007). An unknown source is a no-op and causes no recalculation. */
    void remove(UUID holderId, SourceId source);

    /** Removes every source of one kind - all equipment contributions at once, for instance. */
    void removeKind(UUID holderId, SourceKind kind);

    // ----------------------------------------------------------------- holders

    /**
     * Creates a holder for a player character.
     *
     * @return the holder id, which is the player's UUID - the same value every other method here
     *     expects. A separate handle type would be a second identifier for one thing, and therefore
     *     an opportunity to pass the wrong one.
     */
    UUID createForCharacter(UUID playerId, UUID characterId, ResourcePool initial);

    /** Creates a holder without a player behind it - a mob (B10, FR-035). */
    UUID createForEntity(UUID entityId);

    /**
     * The character behind a holder, or empty for a holder without a player (a mob).
     *
     * <p>Added for B05, which needs it twice: to decide whether the no-PvP rule applies, and to put
     * the right id into its death event. Both were previously guesswork from the outside - the
     * session registry answers "is a session loaded", not "is this a character", and using the
     * player id as a character id is simply wrong.
     */
    Optional<UUID> characterIdOf(UUID holderId);

    /** Drops a holder with everything attached to it (FR-036). Idempotent; never throws. */
    void remove(UUID holderId);

    /**
     * Recalculates immediately, skipping the bundling (FR-019b).
     *
     * <p>For the load path, which needs a result before the player is released, and for holders
     * without an entity, for which no entity-bound scheduler exists. Not for normal gameplay - in
     * gameplay, changes bundle.
     */
    StatSnapshot recalculateNow(UUID holderId);

    // --------------------------------------------------------------- resources

    /** Current health and mana with the maxima they are measured against. */
    ResourceView resources(UUID holderId);

    /**
     * Changes current health by {@code delta}, clamped into {@code [0, maxHealth]}.
     *
     * @return the value <b>after</b> clamping, so the caller learns how much actually applied
     */
    double changeHealth(UUID holderId, double delta);

    /** Changes current mana by {@code delta}, clamped into {@code [0, maxMana]}. */
    double changeMana(UUID holderId, double delta);

    /**
     * Sets both resources outright, clamped against the current maxima (FR-027, FR-028).
     *
     * <p>For load paths, not for gameplay: B03 restores a player's stored values here, and B05 uses
     * it to start a freshly equipped creature at full. Both need it <em>after</em> the first
     * calculation, because the maxima do not exist before it - setting resources first would clamp
     * them against a maximum that has not been computed yet.
     */
    void restoreResources(UUID holderId, ResourcePool pool);

    // ------------------------------------------------------------- registration

    /** Registers a supplier of base values - B06 for level, B07 for class (FR-039). */
    void registerBaseStatContributor(BaseStatContributor contributor);

    /** Registers the vanilla mirror. Without one, mirroring is a no-op (FR-034). */
    void registerVanillaBridge(VanillaAttributeBridge bridge);

    /** How many holders currently exist. For diagnostics and leak tests (SC-010). */
    int holderCount();
}

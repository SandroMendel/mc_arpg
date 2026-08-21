package rpg.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The in-memory, authoritative state of a connected player (FR-016).
 *
 * <p>Two absences are the design, not an oversight:
 *
 * <ul>
 *   <li><strong>No way to <em>change</em> the active character.</strong> Switching from one
 *       character to another would require the whole load and unload path - with every failure case
 *       from User Story 3 - to work a second time for an already-connected player. The missing
 *       method is the enforcement. {@link #activate} is not that method: it only ever fills an
 *       empty slot, exactly once, and is refused on a session that already plays someone
 *       (FR-021a/FR-021b).
 *   <li><strong>No getter that returns defaults.</strong> Asking a session that is not
 *       {@link SessionState#READY} throws rather than substituting values the player never had
 *       (FR-004).
 * </ul>
 *
 * <p>The state is the only mutable part, guarded by an atomic so the async load path and the tick
 * can read it without a lock.
 */
public final class PlayerSession {

    private final UUID playerId;
    private final AtomicReference<PlayerCharacter> activeCharacter;
    private final CopyOnWriteArrayList<PlayerCharacter> availableCharacters;
    private final AtomicReference<SessionState> state;
    private final AtomicReference<Instant> readyAt = new AtomicReference<>();

    public PlayerSession(
            UUID playerId,
            PlayerCharacter activeCharacter,
            List<PlayerCharacter> availableCharacters) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        // May be null: a player who has not created a character yet gets a session without one
        // (FR-021) rather than a silently invented character.
        this.activeCharacter = new AtomicReference<>(activeCharacter);
        // Copy-on-write, not List.copyOf: a character created during the session joins this list, and
        // it is read from the tick while the selection writes to it.
        this.availableCharacters =
                new CopyOnWriteArrayList<>(
                        Objects.requireNonNull(availableCharacters, "availableCharacters"));
        this.state = new AtomicReference<>(SessionState.LOADING);

        if (activeCharacter != null && !this.availableCharacters.contains(activeCharacter)) {
            throw new IllegalArgumentException(
                    "the active character must be one of the account's characters");
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public SessionState state() {
        return state.get();
    }

    /** The character being played, or empty for a player who has not created one yet. */
    public Optional<PlayerCharacter> activeCharacter() {
        return Optional.ofNullable(activeCharacter.get());
    }

    /**
     * All characters of this account: those read during the load, plus one created since.
     *
     * <p>A snapshot, so a caller iterating it is not surprised by a concurrent {@link #activate}.
     */
    public List<PlayerCharacter> availableCharacters() {
        return List.copyOf(availableCharacters);
    }

    /**
     * Takes a newly created character into play - the one and only way a session gains one.
     *
     * <p>Needed by B07: before the class is chosen there is no character (ADR-020), and afterwards
     * the player enters the game state <em>in the same session</em>. Without this the choice would only
     * take effect on the next login, and the block's own acceptance criterion would fail.
     *
     * <p><b>Fills an empty slot, never replaces a full one.</b> That is the whole difference to the
     * setter this class refuses to have: there is no state to unload, no stored values to reload, and
     * no failure case from User Story 3 to repeat - the character is new and has nothing behind it. A
     * session that already plays someone is refused, which is what keeps a class change impossible
     * (FR-039) and the load path single-use.
     *
     * @return {@code false} if this session already has an active character, in which case nothing
     *     changed - the caller lost a race and must not proceed as though it had won
     * @throws IllegalArgumentException if the character belongs to another account
     */
    public boolean activate(PlayerCharacter character) {
        Objects.requireNonNull(character, "character");
        if (!character.playerId().equals(playerId)) {
            throw new IllegalArgumentException(
                    "character " + character.characterId() + " does not belong to " + playerId);
        }
        // Before the slot is won, not after. The constructor's invariant - the active character is one
        // of the account's - must hold for every reader at every moment, and doing this second would
        // leave a window where a thread sees the character in play but not in the list. A caller that
        // then loses the race leaves an extra entry behind, which is harmless and true: the character
        // was created and does belong to this account.
        availableCharacters.addIfAbsent(character);
        return activeCharacter.compareAndSet(null, character);
    }

    /** When the session became ready; used to measure SC-001. */
    public Optional<Instant> readyAt() {
        return Optional.ofNullable(readyAt.get());
    }

    /** Whether other blocks may query this session's values. */
    public boolean isReady() {
        return state.get().isQueryable();
    }

    /**
     * Moves the session to {@code target}.
     *
     * @throws IllegalStateException if the transition is not one of those declared in
     *     {@link SessionState} - an undeclared transition is a bug, and the two that must never
     *     happen are exactly the ones that would write a failed load over real progress
     */
    public void transitionTo(SessionState target, Instant now) {
        Objects.requireNonNull(target, "target");
        SessionState current =
                state.getAndUpdate(
                        from -> from.canTransitionTo(target) ? target : from);
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "illegal session transition for "
                            + playerId
                            + ": "
                            + current
                            + " -> "
                            + target
                            + " (allowed from "
                            + current
                            + ": "
                            + current.allowedTargets()
                            + ")");
        }
        if (target == SessionState.READY) {
            readyAt.set(now);
        }
    }

    /**
     * Whether this session's state may be written to storage.
     *
     * <p>The predicate the flush path must consult. A {@code FAILED} session, or one abandoned
     * mid-load, has no state the player ever received - writing it would replace their real record
     * with nothing (FR-012, FR-015).
     */
    public boolean mayBeWritten() {
        return state.get().mayBeWritten();
    }

    @Override
    public String toString() {
        return "PlayerSession[" + playerId + " " + state.get() + "]";
    }
}

package rpg.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The in-memory, authoritative state of a connected player (FR-016).
 *
 * <p>Two absences are the design, not an oversight:
 *
 * <ul>
 *   <li><strong>No way to change the active character.</strong> It is set when the session is
 *       created and never afterwards (FR-021a/FR-021b). Offering a setter would require the whole
 *       load and unload path - with every failure case from User Story 3 - to work a second time
 *       for an already-connected player. The missing method is the enforcement.
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
    private final PlayerCharacter activeCharacter;
    private final List<PlayerCharacter> availableCharacters;
    private final AtomicReference<SessionState> state;
    private final AtomicReference<Instant> readyAt = new AtomicReference<>();

    public PlayerSession(
            UUID playerId,
            PlayerCharacter activeCharacter,
            List<PlayerCharacter> availableCharacters) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        // May be null: a player who has not created a character yet gets a session without one
        // (FR-021) rather than a silently invented character.
        this.activeCharacter = activeCharacter;
        this.availableCharacters =
                List.copyOf(Objects.requireNonNull(availableCharacters, "availableCharacters"));
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
        return Optional.ofNullable(activeCharacter);
    }

    /** All characters of this account, read once during the load. */
    public List<PlayerCharacter> availableCharacters() {
        return availableCharacters;
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

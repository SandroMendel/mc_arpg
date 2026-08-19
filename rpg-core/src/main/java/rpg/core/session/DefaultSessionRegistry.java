package rpg.core.session;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The session cache.
 *
 * <p>A plain concurrent map, because that is all it needs to be. What makes it trustworthy is not
 * this class but the reconciliation that sweeps it (see {@link SessionReconciler}): FR-009 and
 * SC-008 ask for a guarantee that no session is left behind, and careful bookkeeping in the unload
 * path is an intention, not a guarantee.
 *
 * <p>Opening a session for a player who already has one is <strong>rejected</strong> rather than
 * replacing the existing entry. Replacing would silently drop the first session together with
 * whatever progress it had not written yet - and it would do so at exactly the moment a player
 * reconnects quickly, which is when this block is under the most pressure.
 */
public final class DefaultSessionRegistry implements SessionRegistry {

    private final ConcurrentHashMap<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<PlayerSession> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerSession session = sessions.get(playerId);
        // Not ready is reported as absent, never as defaults (FR-004).
        return session != null && session.isReady() ? Optional.of(session) : Optional.empty();
    }

    @Override
    public PlayerSession require(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isReady()) {
            throw new SessionNotReadyException(
                    playerId, session == null ? null : session.state());
        }
        return session;
    }

    @Override
    public boolean isReady(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        return session != null && session.isReady();
    }

    @Override
    public int activeSessionCount() {
        return sessions.size();
    }

    // --- lifecycle-facing operations, not part of the public contract ---

    /**
     * Registers a newly opened session.
     *
     * @throws DuplicateSessionException if one already exists for this player (FR-014)
     */
    public void open(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        PlayerSession existing = sessions.putIfAbsent(session.playerId(), session);
        if (existing != null) {
            throw new DuplicateSessionException(session.playerId());
        }
    }

    /** Removes a session. Called only once its final write finished, or by the reconciliation. */
    public Optional<PlayerSession> remove(UUID playerId) {
        return Optional.ofNullable(sessions.remove(playerId));
    }

    /** The session regardless of readiness; the lifecycle needs this, callers outside do not. */
    public Optional<PlayerSession> peek(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /** Every held session, ready or not. */
    public List<PlayerSession> all() {
        return List.copyOf(sessions.values());
    }

    /** The players currently holding a session; compared against the connected ones. */
    public Set<UUID> heldPlayerIds() {
        return Set.copyOf(sessions.keySet());
    }

    /**
     * The sessions whose player is not in {@code connectedPlayerIds}.
     *
     * <p>The difference the reconciliation removes. Computed here so the sweep itself stays a
     * simple loop over an already-decided set.
     */
    public List<PlayerSession> orphanedAgainst(Collection<UUID> connectedPlayerIds) {
        Set<UUID> connected = Set.copyOf(connectedPlayerIds);
        return sessions.values().stream()
                .filter(session -> !connected.contains(session.playerId()))
                .toList();
    }
}

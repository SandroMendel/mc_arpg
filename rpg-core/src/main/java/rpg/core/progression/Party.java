package rpg.core.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A group of players sharing experience. Pure runtime state - never persisted (FR-029).
 *
 * <p>A restart erases every party, and that is the promise rather than a side effect: a party across
 * session boundaries would need storage, and storage was ruled out.
 *
 * <p><b>Arrays, not collections.</b> The member list is read on every kill of every party member, so
 * fixed arrays at the configured maximum size avoid an iterator and boxing in the combat path.
 *
 * <p><b>{@code joinedAt} carries the seniority</b> that FR-029c needs. Without a join timestamp
 * "the longest-serving remaining member" is not decidable, and the specification did not name the
 * field - it was added while designing the data model. On equal timestamps the lower index wins,
 * which is the order they joined in.
 */
public final class Party {

    private final UUID partyId;
    private final UUID[] members;
    private final long[] joinedAt;
    private int size;
    private UUID leader;

    Party(UUID partyId, UUID founder, long now, int maxSize) {
        this.partyId = Objects.requireNonNull(partyId, "partyId");
        this.members = new UUID[maxSize];
        this.joinedAt = new long[maxSize];
        this.members[0] = Objects.requireNonNull(founder, "founder");
        this.joinedAt[0] = now;
        this.size = 1;
        this.leader = founder;
    }

    public UUID partyId() {
        return partyId;
    }

    /** Never absent: a party without a leader must not exist (FR-029c). */
    public UUID leader() {
        return leader;
    }

    public int size() {
        return size;
    }

    public boolean isFull() {
        return size >= members.length;
    }

    public boolean isLeader(UUID playerId) {
        return leader.equals(playerId);
    }

    public boolean contains(UUID playerId) {
        return indexOf(playerId) >= 0;
    }

    /** A copy, for display. The hot path uses {@link #copyMembersInto}. */
    public List<UUID> memberList() {
        List<UUID> copy = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            copy.add(members[i]);
        }
        return List.copyOf(copy);
    }

    /**
     * Writes the members into {@code out} and returns how many. No allocation - this runs on every
     * kill.
     *
     * @throws IllegalArgumentException if {@code out} is too small. Truncating silently in the
     *     combat path would hand some members no experience and leave no trace of why.
     */
    public int copyMembersInto(UUID[] out) {
        Objects.requireNonNull(out, "out");
        if (out.length < size) {
            throw new IllegalArgumentException(
                    "out must hold at least party.max-size entries, but held " + out.length);
        }
        System.arraycopy(members, 0, out, 0, size);
        return size;
    }

    boolean add(UUID playerId, long now) {
        if (isFull() || contains(playerId)) {
            return false;
        }
        members[size] = playerId;
        joinedAt[size] = now;
        size++;
        return true;
    }

    /**
     * Removes a member and hands the leadership on if it was the leader.
     *
     * @return the new leader when the role moved, otherwise {@code null}
     */
    UUID remove(UUID playerId) {
        int index = indexOf(playerId);
        if (index < 0) {
            return null;
        }
        boolean wasLeader = leader.equals(playerId);
        // Compact in place, keeping join order intact - seniority depends on it.
        for (int i = index; i < size - 1; i++) {
            members[i] = members[i + 1];
            joinedAt[i] = joinedAt[i + 1];
        }
        members[size - 1] = null;
        joinedAt[size - 1] = 0L;
        size--;
        if (!wasLeader || size == 0) {
            return null;
        }
        leader = longestServing();
        return leader;
    }

    /** Lowest join timestamp; ties go to the lower index, which is the earlier join. */
    private UUID longestServing() {
        int best = 0;
        for (int i = 1; i < size; i++) {
            if (joinedAt[i] < joinedAt[best]) {
                best = i;
            }
        }
        return members[best];
    }

    private int indexOf(UUID playerId) {
        for (int i = 0; i < size; i++) {
            if (members[i].equals(playerId)) {
                return i;
            }
        }
        return -1;
    }
}

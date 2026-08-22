package rpg.core.progression;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Who gets how much of what a dead creature is worth (FR-039, FR-040 to FR-043).
 *
 * <p><b>Extracted from {@link XpDistributor}, not written anew</b> (ADR-029). B08b has to split coins
 * by exactly the rule that splits experience, and a second implementation would have stayed identical
 * only until somebody touched one of them. The divergence would then hit two players of the same
 * party differently, and nobody could explain why.
 *
 * <p>It stays in <b>B06's package</b>, because B06 owns this rule. Something does not move house
 * because a second user turns up.
 *
 * <p>The five steps, unchanged:
 *
 * <pre>
 *   1. amount   comes from the caller - experience or coins, this class does not care
 *   2. shares   the damage split from B05, never recomputed
 *   3. group    shares of members of the same party added together, counted as ONE contributor
 *   4. split    the party share spread evenly over the members IN RANGE
 *   5. bonus    a percentage on top of the party share, per additional member in range
 * </pre>
 *
 * <p><b>Rounding is downwards and the remainder stays on the table</b> (FR-047). Rounding up would
 * produce value out of nothing on every kill - at 800 mobs a visible inflation, and for coins an
 * actual one.
 *
 * <p><b>There is no minimum share.</b> A contributor with a small share gets a small amount, not
 * nothing. Confirmed at {@code /clarify} for B08b: introducing a threshold for coins alone would be
 * the first place where two rules value the same kill differently.
 *
 * <p><b>On allocation.</b> Two small arrays per <em>kill</em>, not per damage event, and the result
 * is handed out through a callback rather than a collection - so a caller that wants to grant
 * immediately never builds a list it throws away.
 */
public final class ShareCalculator {

    /** What to do with one recipient's amount. */
    @FunctionalInterface
    public interface ShareRecipient {
        /**
         * @param holderId the stat holder - a player id, as B05 keys its damage shares
         * @param amount always positive; recipients with nothing are not reported
         */
        void receive(UUID holderId, long amount);
    }

    private final PartyRegistry parties;
    private final ProgressionConfig config;
    private final ProximityCheckSource proximity;

    /** Where the proximity check comes from, looked up per call because it is registered late. */
    @FunctionalInterface
    public interface ProximityCheckSource {
        ProximityCheck get();
    }

    public ShareCalculator(
            PartyRegistry parties, ProgressionConfig config, ProximityCheckSource proximity) {
        this.parties = Objects.requireNonNull(parties, "parties");
        this.config = Objects.requireNonNull(config, "config");
        this.proximity = Objects.requireNonNull(proximity, "proximity");
    }

    /**
     * Splits {@code amount} over the contributors and reports each share.
     *
     * @param shares the damage split from B05, taken as given and never recomputed
     * @param amount what the creature is worth; nothing happens at or below zero
     * @param origin where it died, for the range check; {@code null} falls back to the contributor
     *     alone rather than paying everyone or swallowing the share (FR-044)
     * @param recipient called once per recipient with a positive amount
     */
    public void allocate(
            Map<UUID, Double> shares, long amount, WorldPoint origin, ShareRecipient recipient) {
        Objects.requireNonNull(shares, "shares");
        Objects.requireNonNull(recipient, "recipient");
        if (amount <= 0L || shares.isEmpty()) {
            return;
        }

        UUID[] contributors = shares.keySet().toArray(new UUID[0]);
        boolean[] handled = new boolean[contributors.length];
        UUID[] members = new UUID[config.partyMaxSize()];
        UUID[] inRange = new UUID[config.partyMaxSize()];

        for (int i = 0; i < contributors.length; i++) {
            if (handled[i]) {
                continue;
            }
            UUID contributor = contributors[i];
            int memberCount = parties.membersOf(contributor, members);

            if (memberCount <= 1) {
                // No party, or a party of one - which behaves exactly like no party (FR-035, FR-046).
                handled[i] = true;
                long own = (long) Math.floor(amount * shares.getOrDefault(contributor, 0.0));
                if (own > 0L) {
                    recipient.receive(contributor, own);
                }
                continue;
            }

            // Step 3: one contributor, whose share is the sum of the members' shares (FR-040).
            double partyShare = 0.0;
            for (int m = 0; m < memberCount; m++) {
                partyShare += shares.getOrDefault(members[m], 0.0);
            }
            for (int j = i; j < contributors.length; j++) {
                if (!handled[j] && parties.sameParty(contributor, contributors[j])) {
                    handled[j] = true;
                }
            }

            long partyAmount = (long) Math.floor(amount * partyShare);
            if (partyAmount <= 0L) {
                continue;
            }

            // Step 4: only members in range, measured from the dead creature (FR-041a, FR-042).
            int reachable = reachable(origin, members, memberCount, inRange);
            if (reachable == 0) {
                continue;
            }

            // Step 5: the bonus applies to the party share BEFORE it is divided (FR-043).
            long withBonus = (long) Math.floor(partyAmount * (1.0 + config.bonusFor(reachable)));
            long each = withBonus / reachable;
            if (each <= 0L) {
                continue;
            }
            for (int m = 0; m < reachable; m++) {
                recipient.receive(inRange[m], each);
            }
        }
    }

    /**
     * Which members are close enough.
     *
     * <p>Without a registered check only the contributor itself counts, so the split falls back to
     * the no-party behaviour instead of handing value to everyone or swallowing it (FR-044).
     */
    private int reachable(WorldPoint origin, UUID[] members, int memberCount, UUID[] out) {
        ProximityCheck check = proximity.get();
        if (check == null || origin == null) {
            out[0] = members[0];
            return 1;
        }
        return check.inRange(origin, members, memberCount, config.partyRange(), out);
    }
}

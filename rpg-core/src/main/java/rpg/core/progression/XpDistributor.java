package rpg.core.progression;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.stats.StatEngine;

/**
 * Turns a death into credited experience, in the five steps of FR-039.
 *
 * <p><b>This continues B05's decision, it does not replace it.</b> B05 settled that experience is
 * split by damage share. A party is treated as <b>one</b> contributor whose share is the sum of its
 * members' shares; without that bracket there would be two competing rules for the same experience.
 *
 * <pre>
 *   1. amount   = configured experience of the creature
 *   2. shares   = the damage split from B05, never recomputed
 *   3. group    = shares of members of the same party added together
 *   4. split    = the party share spread evenly over the members IN RANGE
 *   5. bonus    = a percentage on top of the party share, per additional member in range
 * </pre>
 *
 * <p><b>On allocation.</b> Two small arrays are taken per <em>death</em>, not per damage event. B05
 * already builds one map per death, and a death is orders of magnitude rarer than a hit; what FR-062
 * rules out is an object per experience event, and granting takes primitives.
 *
 * <p><b>Rounding is downwards</b> and the remainder stays on the table (FR-047). Rounding up would
 * have produced up to four experience out of nothing per kill with a five-member party - at 800 mobs
 * a visible inflation.
 */
public final class XpDistributor {

    private final DefaultProgression progression;
    private final PartyRegistry parties;
    private final StatEngine stats;
    private final ProgressionConfig config;
    private final Logger logger;

    public XpDistributor(
            DefaultProgression progression,
            PartyRegistry parties,
            StatEngine stats,
            ProgressionConfig config,
            Logger logger) {
        this.progression = Objects.requireNonNull(progression, "progression");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Distributes the experience of one dead creature.
     *
     * @param death the death event from B05, whose shares are taken as given (FR-011)
     * @param mobTypeKey which kind of creature it was, for the configured amount
     * @param origin where it died, read by the listener while that was still valid (FR-041a)
     * @return total experience credited, bonus included
     */
    public long distribute(CombatDeathEvent death, String mobTypeKey, WorldPoint origin) {
        Objects.requireNonNull(death, "death");
        if (death.playerVictim()) {
            // No experience for killing a player. PvP is off in B05 anyway (FR-013).
            return 0L;
        }
        Map<UUID, Double> shares = death.shares().shares();
        if (shares.isEmpty()) {
            // Burned to death in lava, for instance. A regular case, not a failure (FR-012).
            return 0L;
        }

        long amount = progression.xpForMob(mobTypeKey);
        if (amount <= 0L) {
            return 0L;
        }

        UUID[] attackers = shares.keySet().toArray(new UUID[0]);
        boolean[] handled = new boolean[attackers.length];
        UUID[] members = new UUID[config.partyMaxSize()];
        UUID[] inRange = new UUID[config.partyMaxSize()];

        long credited = 0L;
        for (int i = 0; i < attackers.length; i++) {
            if (handled[i]) {
                continue;
            }
            UUID attacker = attackers[i];
            int memberCount = parties.membersOf(attacker, members);

            if (memberCount <= 1) {
                // No party, or a party of one - which behaves exactly like no party (FR-035, FR-046).
                handled[i] = true;
                credited += grant(attacker, share(shares, attacker, amount));
                continue;
            }

            // Step 3: one contributor, whose share is the sum of the members' shares (FR-040).
            double partyShare = 0.0;
            for (int m = 0; m < memberCount; m++) {
                partyShare += shares.getOrDefault(members[m], 0.0);
            }
            for (int j = i; j < attackers.length; j++) {
                if (!handled[j] && parties.sameParty(attacker, attackers[j])) {
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
                credited += grant(inRange[m], each);
            }
        }
        return credited;
    }

    /**
     * Which members are close enough.
     *
     * <p>Without a registered check only the contributor itself counts, so the split falls back to
     * the no-party behaviour instead of handing experience to everyone or swallowing it (FR-044).
     */
    private int reachable(WorldPoint origin, UUID[] members, int memberCount, UUID[] out) {
        ProximityCheck check = progression.proximityCheck();
        if (check == null || origin == null) {
            out[0] = members[0];
            return 1;
        }
        return check.inRange(origin, members, memberCount, config.partyRange(), out);
    }

    private long share(Map<UUID, Double> shares, UUID attacker, long amount) {
        return (long) Math.floor(amount * shares.getOrDefault(attacker, 0.0));
    }

    /**
     * Credits a player's active character.
     *
     * <p>Damage shares from B05 are keyed by player - a stat holder for a character <em>is</em> the
     * player id. The character comes from the same source B05 uses for attribution.
     */
    private long grant(UUID playerId, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var characterId = stats.characterIdOf(playerId);
        if (characterId.isEmpty()) {
            logger.fine("[progression] no character for holder " + playerId + " - share lapses");
            return 0L;
        }
        XpResult result = progression.grant(characterId.get(), amount, XpSource.MOB_KILL);
        // A share that lapses - logged out, or already at the maximum - is not redistributed
        // (FR-014, FR-052). Redistributing would make a party with a maxed member stronger than one
        // without.
        return result.granted();
    }
}

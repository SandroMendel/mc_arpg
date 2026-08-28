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
    private final ShareCalculator shareCalculator;

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
        this.shareCalculator =
                new ShareCalculator(parties, config, progression::proximityCheck);
    }

    /**
     * The split, shared with B08b (ADR-029).
     *
     * <p>Built here rather than injected: the rule belongs to this block, and a caller who could
     * hand in a different one could make experience and coins disagree - which is the exact failure
     * the extraction was meant to rule out.
     */
    public ShareCalculator shareCalculator() {
        return shareCalculator;
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

        // The split itself lives in ShareCalculator since ADR-029: B08b needs the very same rule for
        // coins, and two implementations of it would agree only until somebody edited one.
        long[] credited = {0L};
        shareCalculator.allocate(
                shares, amount, origin, (holderId, share) -> credited[0] += grant(holderId, share));
        return credited[0];
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

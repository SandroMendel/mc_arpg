package rpg.core.progression;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * The validated contents of {@code progression.yml} (FR-005).
 *
 * <p>Every balancing number of this block is here and none is in code. A new mob type is a line, a
 * higher ceiling is a line, and B07 replaces {@code growth} per class without touching a class file
 * (FR-006, FR-022).
 */
public record ProgressionConfig(
        XpCurve curve,
        LevelGrowth growth,
        long mobXpDefault,
        Map<String, Long> mobXpByType,
        int partyMaxSize,
        double partyRange,
        double partyBonusPerMember,
        double partyBonusCap,
        Duration inviteTimeout,
        Duration progressWindow) {

    public ProgressionConfig {
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(growth, "growth");
        mobXpByType = Map.copyOf(Objects.requireNonNull(mobXpByType, "mobXpByType"));
        Objects.requireNonNull(inviteTimeout, "inviteTimeout");
        Objects.requireNonNull(progressWindow, "progressWindow");
        if (mobXpDefault < 1L) {
            throw new IllegalArgumentException(
                    "progression.mob-xp.default must be at least 1, but was " + mobXpDefault);
        }
        if (partyMaxSize < 1) {
            throw new IllegalArgumentException(
                    "progression.party.max-size must be at least 1, but was " + partyMaxSize);
        }
        if (!Double.isFinite(partyRange) || partyRange <= 0.0) {
            throw new IllegalArgumentException(
                    "progression.party.range-blocks must be finite and positive, but was "
                            + partyRange);
        }
        if (!Double.isFinite(partyBonusPerMember) || partyBonusPerMember < 0.0) {
            throw new IllegalArgumentException(
                    "progression.party.bonus-per-member must be finite and not negative, but was "
                            + partyBonusPerMember);
        }
        if (!Double.isFinite(partyBonusCap) || partyBonusCap < 0.0) {
            throw new IllegalArgumentException(
                    "progression.party.bonus-cap must be finite and not negative, but was "
                            + partyBonusCap);
        }
        // A ceiling below the per-member step would cap the bonus at the second member and make
        // bonus-per-member meaningless. Better a startup error than a number that does nothing.
        if (partyBonusCap < partyBonusPerMember) {
            throw new IllegalArgumentException(
                    "progression.party.bonus-cap ("
                            + partyBonusCap
                            + ") must not be below bonus-per-member ("
                            + partyBonusPerMember
                            + ") - it would cap the bonus at the second member and make"
                            + " bonus-per-member meaningless");
        }
        if (inviteTimeout.isNegative() || inviteTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "progression.party.invite-timeout-seconds must be positive, but was "
                            + inviteTimeout.toSeconds());
        }
        if (progressWindow.isNegative() || progressWindow.isZero()) {
            throw new IllegalArgumentException(
                    "progression.progress-event.window-millis must be positive, but was "
                            + progressWindow.toMillis());
        }
    }

    /** The maximum level, always from the curve and never from a constant (FR-004). */
    public int maxLevel() {
        return curve.maxLevel();
    }

    /** Configured experience for a mob type, or empty when it has no entry of its own. */
    public OptionalLong mobXpFor(String mobTypeKey) {
        Long value = mobXpByType.get(mobTypeKey);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /**
     * The proximity bonus for a given number of members in range.
     *
     * <p>A percentage, never a fixed amount: a fixed bonus would be enormous at level 1 and
     * irrelevant at level 60, because mob experience rises steeply across the progression (FR-043).
     * One member in range means no bonus at all - a party of one behaves exactly like no party.
     */
    public double bonusFor(int membersInRange) {
        if (membersInRange <= 1) {
            return 0.0;
        }
        double raw = partyBonusPerMember * (membersInRange - 1);
        return Math.min(raw, partyBonusCap);
    }
}

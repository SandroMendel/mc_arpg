package rpg.core.progression;

import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

import rpg.core.stats.BaseStatContributor;
import rpg.core.stats.BaseStatSink;
import rpg.core.stats.StatHolderView;

/**
 * Feeds the level growth into B04 as a <b>base</b> contribution (FR-020).
 *
 * <p><b>Why a base contribution and not a modifier.</b> ADR-013 already settled it when B04 closed
 * ("Basiswerte kommen über BaseStatContributor (B06 Level, B07 Klasse)"), and the arithmetic leaves
 * no choice. {@code StatCalculator} adds a base contribution onto {@code definition.base()} to form
 * the <em>effective</em> base, and puts the modifier band around exactly that value:
 *
 * <pre>
 *   effectiveBase = definition.base() + baseBonus
 *   raw           = (effectiveBase + flat) * (1 + percent)
 *   raw           = clamp(raw, bandFloor(effectiveBase), bandCeiling(effectiveBase))
 * </pre>
 *
 * <p>As a FLAT modifier the growth would land in {@code flat} - <em>inside</em> a band anchored to
 * the untouched level-1 base. "Plus or minus 30 percent" would tighten relatively with every level,
 * and B11's equipment contributions would be measurably mis-clamped at level 60. The javadoc of
 * {@code AttributeDefinition.bandFloor} names B06 and B07 for precisely this reason.
 *
 * <p>{@code SourceKind.LEVEL} therefore stays unused. It keeps its purpose for a modifier that
 * <em>follows</em> from the level without raising the base - a milestone bonus every ten levels, say.
 * B06 needs nothing of the kind; see research.md, decision 1.
 */
public final class LevelStatContributor implements BaseStatContributor {

    /** Stable id, so a second registration replaces rather than duplicates. */
    public static final String ID = "progression-level";

    private final LevelGrowth growth;
    private final ToIntFunction<UUID> levelOf;

    /**
     * @param levelOf level of a character, 0 when nothing is loaded for it. A primitive function on
     *     purpose: this runs on every recalculation, and an {@code OptionalInt} per call would
     *     allocate in a path that promises not to.
     */
    public LevelStatContributor(LevelGrowth growth, ToIntFunction<UUID> levelOf) {
        this.growth = Objects.requireNonNull(growth, "growth");
        this.levelOf = Objects.requireNonNull(levelOf, "levelOf");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void contribute(StatHolderView holder, BaseStatSink sink) {
        // A creature has no character, so nothing to contribute - and no exception either: B04
        // recalculates mobs through the same path.
        holder.characterId()
                .ifPresent(
                        characterId -> {
                            int level = levelOf.applyAsInt(characterId);
                            if (level > 1) {
                                growth.contributeTo(level, sink);
                            }
                        });
    }
}

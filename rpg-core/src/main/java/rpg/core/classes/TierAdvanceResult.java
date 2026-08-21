package rpg.core.classes;

import java.util.Objects;
import java.util.Optional;

/** The outcome of a tier advance: the new tier, or a named reason why not. */
public final class TierAdvanceResult {

    private final int newTier;
    private final TierAdvanceRejection rejection;

    private TierAdvanceResult(int newTier, TierAdvanceRejection rejection) {
        this.newTier = newTier;
        this.rejection = rejection;
    }

    public static TierAdvanceResult advanced(int newTier) {
        if (newTier < ClassProgress.INITIAL_TIER) {
            throw new IllegalArgumentException("newTier must be at least 1, but was " + newTier);
        }
        return new TierAdvanceResult(newTier, null);
    }

    public static TierAdvanceResult rejected(TierAdvanceRejection rejection) {
        return new TierAdvanceResult(0, Objects.requireNonNull(rejection, "rejection"));
    }

    public boolean advanced() {
        return rejection == null;
    }

    /** The tier now reached, present exactly when {@link #advanced()}. */
    public int newTier() {
        if (!advanced()) {
            throw new IllegalStateException("no new tier - the advance was rejected: " + rejection);
        }
        return newTier;
    }

    public Optional<TierAdvanceRejection> rejection() {
        return Optional.ofNullable(rejection);
    }

    @Override
    public String toString() {
        return advanced()
                ? "TierAdvanceResult[advanced to " + newTier + "]"
                : "TierAdvanceResult[rejected " + rejection + "]";
    }
}

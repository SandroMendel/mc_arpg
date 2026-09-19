package rpg.core.performance;

import java.time.Duration;
import java.util.Objects;

/** The budget and owning architecture block for one measured subsystem. */
public record PerformanceBudget(SubsystemId id, long budgetNanos, String owner) {

    public PerformanceBudget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        owner = owner.trim();
        if (owner.isEmpty()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (budgetNanos <= 0) {
            throw new IllegalArgumentException("budgetNanos must be positive");
        }
    }

    public static PerformanceBudget of(SubsystemId id, Duration budget, String owner) {
        Objects.requireNonNull(budget, "budget");
        long nanos = budget.toNanos();
        if (nanos <= 0) {
            throw new IllegalArgumentException("budget must be positive");
        }
        return new PerformanceBudget(id, nanos, owner);
    }
}

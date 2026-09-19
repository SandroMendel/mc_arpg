package rpg.core.performance;

import java.time.Duration;
import java.util.Objects;

/** Thresholds for one alert state machine. */
public record AlertPolicy(double warningRatio, Duration criticalAfter) {

    public AlertPolicy {
        Objects.requireNonNull(criticalAfter, "criticalAfter");
        if (!(warningRatio > 0.0d && warningRatio <= 1.0d)) {
            throw new IllegalArgumentException("warningRatio must be in (0, 1]");
        }
        if (criticalAfter.isZero() || criticalAfter.isNegative()) {
            throw new IllegalArgumentException("criticalAfter must be positive");
        }
    }
}

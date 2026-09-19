package rpg.core.performance;

import java.util.Objects;

/** Stable identifier of one measured RPG subsystem. */
public record SubsystemId(String value) {

    public SubsystemId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("subsystem id must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

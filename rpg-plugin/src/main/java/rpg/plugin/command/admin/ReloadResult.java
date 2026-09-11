package rpg.plugin.command.admin;

import java.util.Objects;

import rpg.core.config.ConfigValidationException;

/** Outcome of the global configuration reload used by {@code /rpg reload}. */
public record ReloadResult(boolean applied, ConfigValidationException rejection) {

    public ReloadResult {
        if (applied && rejection != null) {
            throw new IllegalArgumentException("ein erfolgreicher Reload darf keine Ablehnung tragen");
        }
        if (!applied) {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    public static ReloadResult success() {
        return new ReloadResult(true, null);
    }

    public static ReloadResult rejected(ConfigValidationException failure) {
        return new ReloadResult(false, Objects.requireNonNull(failure, "failure"));
    }
}

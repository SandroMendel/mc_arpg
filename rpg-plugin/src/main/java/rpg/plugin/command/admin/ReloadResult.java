package rpg.plugin.command.admin;

import java.util.Map;
import java.util.Objects;

import rpg.core.config.ConfigValidationException;

/** Outcome of the global configuration reload used by {@code /rpg reload}. */
public record ReloadResult(boolean applied, ConfigValidationException rejection) {

    /** Stable audit value for a committed reload. */
    public static final String RESULT_APPLIED = "APPLIED";

    /** Stable audit value for a rejected reload. */
    public static final String RESULT_REJECTED = "REJECTED";

    /** Source marker used when every registered configuration source was considered. */
    public static final String ALL_SOURCES = "all-registered-sources";

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

    /**
     * Details written by the existing admin audit seam.
     *
     * <p>Both outcomes are recorded. A rejection carries the exact validation coordinates and the
     * exception message, so an operator can identify the source without correlating a second log
     * line. The details map deliberately uses the existing {@code AuditEntry.details} extension
     * point; no new persistence field or player state is introduced.
     */
    public Map<String, Object> auditDetails() {
        if (applied) {
            return Map.of(
                    "scope", "global",
                    "result", RESULT_APPLIED,
                    "source", ALL_SOURCES);
        }

        return Map.of(
                "scope", "global",
                "result", RESULT_REJECTED,
                "source", rejection.sourceFile().toString(),
                "path", rejection.documentPath(),
                "expected", rejection.expected(),
                "actual", rejection.actual(),
                "reason", rejection.getMessage());
    }
}

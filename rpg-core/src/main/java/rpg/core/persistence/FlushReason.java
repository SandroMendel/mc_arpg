package rpg.core.persistence;

/** Why a flush is running. Carried into the log so a write can be traced to its trigger. */
public enum FlushReason {
    /** The autosave interval elapsed (FR-003). */
    INTERVAL,
    /** A player left; their aggregates are written immediately (FR-004). */
    SESSION_END,
    /** The server is stopping; everything outstanding is written within 8s (FR-011). */
    SHUTDOWN,
    /** Storage became reachable again after an outage (FR-010). */
    RECOVERY
}

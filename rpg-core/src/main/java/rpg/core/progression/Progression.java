package rpg.core.progression;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The public face of B06. B07 to B14 develop against this and against {@code PartyRegistry}; nobody
 * reaches into anything else in this package (Principle III).
 *
 * <p>Two promises hold across every method here. No database access per XP event - a gain marks the
 * character and nothing more (FR-054). And no query ever aborts a caller: an unknown character
 * answers "not met" and logs, rather than throwing (FR-027), because five blocks gate content on
 * these answers.
 */
public interface Progression {

    // --- granting -------------------------------------------------------------------------------

    /**
     * Credits experience to a character. The single entry point every source uses (FR-007).
     *
     * <p>Returns a result rather than throwing: this runs in the combat path, where an exception per
     * rejected amount would be an allocation plus a stack trace (FR-062).
     *
     * <p>An amount of zero, a negative amount or a non-finite one is rejected and logged, never read
     * as a deduction (FR-015). A character whose session is not ready loses the amount silently
     * (FR-014). At the maximum level the amount is discarded silently - no event, no log line per
     * call (FR-049, FR-050).
     */
    XpResult grant(UUID characterId, long amount, XpSource source);

    // --- queries --------------------------------------------------------------------------------

    /**
     * Whether the character has reached a required level (FR-025).
     *
     * <p>Answers without a database access and without recomputing anything (FR-026). An unknown
     * character answers {@code false} and logs the occurrence - never an exception (FR-027).
     */
    boolean meetsLevel(UUID characterId, int requiredLevel);

    /** Current progress, ready to display with nothing left to compute (FR-028). */
    Optional<ProgressView> progressOf(UUID characterId);

    /** Just the level, when that is all a caller needs. */
    OptionalInt levelOf(UUID characterId);

    /** The maximum level, always derived from the curve and never from a constant (FR-004). */
    int maxLevel();

    // --- administration -------------------------------------------------------------------------

    /**
     * Sets level and experience freely, <b>lowering included</b> (FR-024a).
     *
     * <p>The only way progress can go down. FR-024 promises a player that nothing they earned in
     * play is taken away; it does not tie an operator's hands - otherwise a level handed out by a
     * bug could only be fixed by editing the database behind the authoritative cache.
     *
     * <p>{@code actorId} is who did it and goes into B02's audit log with the old and the new state
     * (FR-024b). It is a required parameter because an intervention nobody can attribute is worse
     * than none.
     *
     * <p>Triggers the same recalculation and the same events as a natural rise (FR-024c). A
     * <em>lowered</em> level does not refill health and mana; a value above the new maximum is
     * clamped to it.
     */
    XpResult setProgress(UUID actorId, UUID characterId, int level, long xpInLevel);

    // --- extension points -----------------------------------------------------------------------

    /** Replaces the experience amounts per mob type. B10 calls this at startup (FR-009). */
    void setMobXpProvider(MobXpProvider provider);

    /**
     * Installs the distance measurement. Without one, only the contributor itself counts as in range
     * and the party split falls back to the no-party behaviour (FR-044).
     */
    void setProximityCheck(ProximityCheck check);

    // --- lifecycle ------------------------------------------------------------------------------

    /**
     * Puts a character's stored progress into memory. Called when the session opens.
     *
     * <p>{@code playerId} comes along because two things need it and neither can derive it from the
     * character: checking that the session is ready (FR-014), and addressing the stat holder, which
     * B04 keys by player id. Party membership is keyed by player as well.
     */
    void load(UUID characterId, UUID playerId, ProgressState state);

    /**
     * Drops everything held for a character - state, open bundle, party membership.
     *
     * <p>The promise against leaks. An open display bundle is discarded rather than delivered: it is
     * presentation only, and the recipient is already gone. The experience itself was credited long
     * before and gets written.
     */
    void release(UUID characterId);
}

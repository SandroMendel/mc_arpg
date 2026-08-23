/**
 * B09 persistence - waypoint unlocks and the pending respawn marker.
 *
 * <p>Two tables, one aggregate type {@code CHARACTER_ZONE_STATE}, migration
 * {@code V9_1__character_zone_state.sql}:
 *
 * <ul>
 *   <li>{@code rpg.character_waypoints} - one row per unlocked crystal. <b>Grows only</b>; no row is
 *       ever deleted except with the character (FR-051b2).
 *   <li>{@code rpg.character_zone_state} - one row per character carrying the pending respawn set by
 *       a combat logout (ADR-030). Read, applied and cleared on the next join.
 * </ul>
 *
 * <p><b>Both carry {@code REFERENCES rpg.character (character_id) ON DELETE CASCADE}</b>, like every
 * character-bound table in this project. That single line settles FR-051b1 - unlocks end with the
 * character - and anonymisation with it, the same argument the comments in {@code V4_1} and
 * {@code V6_1} already make.
 *
 * <p><b>Why an aggregate type and not a passenger in the session bundle.</b> Unlocks grow and are
 * touched rarely. In a record that is read in full at every login and written in full at every
 * flush they would get more expensive with every session (research.md R8).
 *
 * <p><b>Why one aggregate for two tables.</b> Both are zone state of the same character and are
 * written in the same moment. Two aggregate types would be two places in the write order for one
 * thing; {@code CHARACTER_INVENTORY} already shows an aggregate may carry more than one table.
 *
 * <p><b>{@code character_waypoints} has no foreign key on a zone or crystal.</b> A crystal key can
 * disappear from the configuration for a while and the unlock is meant to survive that (FR-051b). An
 * orphaned row is a valid state here, not a data error.
 *
 * <p>Registration follows ADR-015 point 7 - the {@code AggregateType} constant, its place in
 * {@code FlushCycle.WRITE_ORDER} after {@code CHARACTER}, and the repository wired into the flush
 * cycle. A missing third registration has its marks counted as failed on every flush, which is why
 * a test asserts all three rather than trusting them.
 */
package rpg.persistence.zone;

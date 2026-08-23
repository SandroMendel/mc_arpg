/**
 * B09 - the spatial layout of the world: named regions, their rules, and travel between them.
 *
 * <p><b>What this block owns.</b> Where a zone is, what rules apply inside it, when a character
 * crosses a border, where a death leads, and how a player travels between regions. The geometry and
 * every rule that reads it live here, testable without a running server.
 *
 * <p><b>The one architectural rule this package exists to enforce.</b> A {@code Zone} is
 * <b>never</b> a {@code World} (ADR-006). A zone is {@code (worldId, geometry)}, so moving a zone
 * into its own world is one configuration line and not a rebuild. Nothing here may take a
 * {@code World}, return one, or assume a zone maps to one.
 *
 * <p><b>No Paper anywhere in this package.</b> Cuboids, the chunk index, the level band, the damage
 * permission and the travel sequence are arithmetic and rules; they compile and are tested without
 * Bukkit on the classpath (Constitution III.1). Positions are expressed as
 * {@link rpg.core.scheduler.WorldPosition}, which already exists for exactly this reason - a second
 * position type would be the duplication ADR-015 point 6 set out to avoid. Translating an
 * {@code org.bukkit.Location} into that value, every listener, the selection window and the actual
 * teleport all live in {@code rpg.platform.zone}.
 *
 * <p><b>What it deliberately does not own.</b>
 *
 * <ul>
 *   <li><b>B10 mobs</b>: the eight creature types per region, their attributes, their level, the
 *       boss, and horde logic. This block ships {@link rpg.core.zone.SpawnArea} - a key and a
 *       geometry, <em>nothing else</em>. No role, no type, no creature list, no number. The key is
 *       the anchor B10 fills.
 *   <li><b>B10 again - the difficulty modifier</b>, and <b>B11 - the loot assignment</b>. Both were
 *       removed from this block on purpose (2026-08-23, {@code /clarify}). A field whose meaning
 *       this block does not know is a field it cannot validate, and B07's opaque {@code cost} block
 *       showed what that costs (ADR-027). Whoever owns the meaning adds the field.
 *   <li><b>B11 items</b>: loot tables, and the equipment damage a death costs. Until B11 exists a
 *       death costs only the trip to the safe core - a named gap, not a silent one (FR-044).
 *   <li><b>B13 UI</b>: showing the zone name. This block hands out the <em>key</em>; B13 resolves
 *       {@code zone.<key>.name}. Nothing here returns finished display text (FR-058).
 *   <li><b>Zone objectives.</b> {@code XpSource.ZONE_OBJECTIVE} stays unfilled: this block defines
 *       zones, not objectives. Same for {@code SourceKind} on zone-bound effects, which needed the
 *       difficulty modifier that is no longer here.
 * </ul>
 *
 * <p><b>Four shipped blocks left an interface waiting for B09. Two are redeemed, two are not, and
 * the score is written down rather than left to be worked out.</b>
 *
 * <ul>
 *   <li><b>Redeemed:</b> {@code WorldCondition.isOpenWorld} from B08 - answered by
 *       {@link rpg.core.zone.ZoneWorldCondition} (FR-052), and {@code DamagePermission} from B05 -
 *       replaced, not duplicated, by {@link rpg.core.zone.ZoneDamagePermission} (FR-026).
 *   <li><b>Still waiting:</b> {@code XpSource.ZONE_OBJECTIVE} from B06 and {@code SourceKind} for
 *       zone-bound effects from B04. Neither is an oversight and neither is B09's to fill: the first
 *       needs objectives inside a zone, which is content and not geometry; the second needed the
 *       difficulty modifier that {@code /clarify} took out of scope. A block that filled them anyway
 *       would be producing values nothing consumes, which is worse than an honest gap
 *       (research.md R12).
 * </ul>
 *
 * <p><b>Where this block reaches past its own layer</b>, each covered by an ADR agreed before
 * implementation began:
 *
 * <ul>
 *   <li>{@code DeathCause.LOGOUT} is added to B05, a shipped block (ADR-030). Logging out in combat
 *       is a death; without a distinguishable cause B12 could not later tell "died" from "ran away".
 *   <li>{@code BookingReason.WAYPOINT_TRAVEL} and {@code WAYPOINT_REFUND} are added to B08b, a
 *       finished block (ADR-032). Two reasons and not one: without the second, a refund would be
 *       indistinguishable from an ordinary credit in the ledger.
 *   <li>Right-click input and the selection window belong to B13. They live here <b>temporarily</b>
 *       (ADR-032, the arrangement ADR-028 set for B08b's account window). A waypoint system without
 *       a way to choose a destination is not a waypoint system.
 * </ul>
 *
 * <p><b>The public contract is {@link rpg.core.zone.Zones}, {@link rpg.core.zone.Waypoints} and
 * {@link rpg.core.zone.Travel}.</b> Reaching past them - at the index, at {@code zones.yml}, at a
 * listener - is not allowed (FR-060), and a source test guards it. From now on a change to those
 * interfaces is ADR-bound, the same promise {@code CombatPipeline}, {@code StatEngine},
 * {@code AbilityRegistry} and {@code Currency} made for themselves.
 */
package rpg.core.zone;

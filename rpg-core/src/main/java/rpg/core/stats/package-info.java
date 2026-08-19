/**
 * B04 - the attribute and stat engine.
 *
 * <p>This is the contract the rest of the game is built against: how class, level, equipment and
 * effects turn into the concrete numbers a player or mob has. Eight attributes, one generic model -
 * not eight special cases.
 *
 * <h2>What this block owns</h2>
 *
 * <ul>
 *   <li>{@link rpg.core.stats.Attribute} - the closed set of eight attributes
 *   <li>{@link rpg.core.stats.StatModifier} and {@link rpg.core.stats.ModifierSet} - contributions
 *       with a traceable source, removable as a unit
 *   <li>{@link rpg.core.stats.StatCalculator} - the one formula:
 *       {@code clamp((base + sum(flat)) * (1 + sum(percent)), min, max)}
 *   <li>{@link rpg.core.stats.StatSnapshot} - the immutable result an in-flight action holds on to
 *   <li>{@link rpg.core.stats.ResourcePool} - the container for current health and mana, plus the
 *       clamping rules when a maximum moves
 *   <li>{@link rpg.core.stats.DamageMitigation} - the divisor model from ADR-008, as a pure
 *       function for B05
 * </ul>
 *
 * <h2>What this block deliberately does NOT do</h2>
 *
 * <p>Every line here is a block boundary, not an omission. The interface is defined; the content
 * belongs to whoever owns it.
 *
 * <ul>
 *   <li><b>B05 Combat</b> - applying damage, hit handling, redirecting vanilla damage sources.
 *       This block supplies the mitigation function and the resource container; it never decides
 *       that something takes damage or dies.
 *   <li><b>B06 Progression</b> - XP, level curves, per-level stat growth. Arrives through
 *       {@link rpg.core.stats.BaseStatContributor}.
 *   <li><b>B07 Classes</b> - per-class base values. Same interface.
 *   <li><b>B08 Abilities</b> - mana regeneration, cooldown bookkeeping, buff durations. This block
 *       supplies {@code ABILITY_COOLDOWN} as a number and mana as a container.
 *   <li><b>B10 Mobs</b> - mob definitions and spawning. This block supplies the holder-neutral
 *       stat holder.
 *   <li><b>B11 Items</b> - item definitions, rolls, equipment slots. Arrives as
 *       {@link rpg.core.stats.ModifierSet} with a {@link rpg.core.stats.SourceKind#EQUIPMENT}
 *       source.
 *   <li><b>B13 UI</b> - HUD and player-facing text. This block publishes
 *       {@link rpg.core.stats.StatsRecalculatedEvent} and mirrors to vanilla attributes.
 * </ul>
 *
 * <h2>Two properties worth knowing before using this</h2>
 *
 * <p><b>Recalculation is event-driven, never periodic.</b> A change marks the holder and schedules
 * exactly one entity-bound task; further changes in the same tick find the mark already set. A tick
 * in which nothing changed costs nothing at all, because no task exists.
 *
 * <p><b>A snapshot is taken once and held.</b> An in-flight projectile, a running ability, a
 * multi-stage combat action all compute with the values from the moment they were triggered.
 * Re-reading the engine mid-action is a bug, not a feature.
 */
package rpg.core.stats;

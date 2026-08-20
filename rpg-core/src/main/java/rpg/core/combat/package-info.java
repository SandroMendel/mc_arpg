/**
 * B05 - the combat and damage pipeline.
 *
 * <p>Replaces the vanilla combat system entirely with one built on B04's eight attributes. This is
 * the most frequently executed path in the whole plugin: at 150 players against 800 mobs it runs
 * thousands of times per second, which is why almost every decision here is about not allocating
 * and not scheduling.
 *
 * <h2>What this block owns</h2>
 *
 * <ul>
 *   <li>{@link rpg.core.combat.DamageFormula} - raw damage from the attacker's attribute times a
 *       factor, then B04's divisor model for defence. No randomness at all: no crit, no dodge, no
 *       block, no resistances (ADR-008).
 *   <li>{@link rpg.core.combat.CombatPipeline} - six named stages with an interception point each,
 *       so abilities and item effects attach at defined places rather than as special cases.
 *   <li>{@link rpg.core.combat.AttackWindow} - attack speed as a real limit; clicking faster does
 *       nothing.
 *   <li>{@link rpg.core.combat.AttributionWindow} - who contributed how much, bounded in both count
 *       and age.
 *   <li>{@link rpg.core.combat.CombatState} - whether a holder is in combat, for B08's reduced mana
 *       regeneration.
 *   <li>Neutralising every vanilla damage source and mapping the environmental ones onto own
 *       damage (ADR-003).
 * </ul>
 *
 * <h2>What this block deliberately does NOT do</h2>
 *
 * <ul>
 *   <li><b>B06 Progression</b> - XP curves and level rules. This block supplies the damage split in
 *       the death event.
 *   <li><b>B08 Abilities</b> - abilities, mana costs, cooldown bookkeeping. This block supplies the
 *       damage factor as a contract and the combat state.
 *   <li><b>B09 Zones</b> - zone rules. This block has the one place where permission is decided,
 *       ready to be replaced.
 *   <li><b>B10 Mobs</b> - mob definitions, spawning, behaviour. This block bridges their <i>values</i>
 *       so that combat can happen at all, behind an interface B10 takes over.
 *   <li><b>B11 Items</b> - item definitions, durability, loot tables, and the equipment damage that
 *       follows a death. This block never touches equipment; it publishes the death event.
 *   <li><b>B12 Statistics</b> - evaluation and leaderboards.
 *   <li><b>B13 UI</b> - drawing damage numbers. This block aggregates them and publishes the result;
 *       it creates no display entities.
 * </ul>
 *
 * <h2>Three properties worth knowing before using this</h2>
 *
 * <p><b>A snapshot is taken once and held.</b> An attack computes with the attacker's values from
 * the moment it started. Re-reading mid-action would make the outcome depend on whether a buff
 * happened to expire between two lines of code.
 *
 * <p><b>The damage context is reused.</b> {@link rpg.core.combat.DamageView} is valid only for the
 * duration of the call that receives it. Holding on to one and reading it later throws rather than
 * returning another fight's data.
 *
 * <p><b>Nothing here schedules anything.</b> Attack window, combat state and contribution age are
 * timestamps evaluated on access. A tick without a hit costs nothing, because there is nothing
 * running.
 */
package rpg.core.combat;

/**
 * B08 - the ability framework. Four levels, kept apart on purpose.
 *
 * <ol>
 *   <li><b>Definition</b> ({@link rpg.core.ability.Ability}, {@link rpg.core.ability.EffectSpec},
 *       {@link rpg.core.ability.TargetSpec}) - declarative, loaded once, immutable. Eighteen objects
 *       for the whole server, not per player.
 *   <li><b>Effect primitives</b> ({@code rpg.core.ability.effect}) - one stateless application per
 *       primitive. What an ability <em>does</em> is a list of these, never a class of its own. That
 *       is the only shape in which a new ability can come from configuration alone (SC-001).
 *   <li><b>Targeting</b> ({@link rpg.core.ability.TargetResolver}) - the rules live here, the lookup
 *       in the world lives in {@code rpg-platform}. Same split as {@code MobStatProvider} in B05 and
 *       for the same reason: cone, range, cap and ordering are testable without a server.
 *   <li><b>Runtime</b> - mana, cooldowns, the global lock, casting, sustained abilities and charges.
 * </ol>
 *
 * <h2>Three timestamped states, not one task per player</h2>
 *
 * <p>Cooldowns, the global lock, charges, rage and both regeneration rates are pure timestamp
 * arithmetic: they are evaluated when somebody asks. Only two things are scheduled, and both are
 * one-shots that exist while they run - a cast and a sustained ability. Every interval effect in the
 * game shares <b>one</b> server-wide sweep, never one per target (Constitution II).
 *
 * <h2>Where this block stops</h2>
 *
 * <p>Damage goes through {@link rpg.core.combat.CombatPipeline} and never around it. Attributes come
 * from B04 and this block adds none - lifesteal, evasion and the revive chance are effects, not
 * secondary stats (ADR-008, ADR-022). Level and unlock come from B06; the class binding comes from
 * B07. Drawing anything is B13's, which reads {@link rpg.core.ability.AbilityRegistry}.
 *
 * <p>Three mechanics are deliberately incomplete until B09 and B10 exist: the clone pulls no aggro,
 * invisibility does not turn mobs away, and Second Life does not check for instances. The hooks are
 * defined here, the behaviour arrives with those blocks (ADR-025, workflow rule 5).
 */
package rpg.core.ability;

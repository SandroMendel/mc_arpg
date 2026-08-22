/**
 * The Paper-facing half of B08: input, hotbar, target lookup and the effects that need the world.
 *
 * <p>The rules live in {@code rpg.core.ability} and are testable without a server. What is here is
 * everything that cannot be: reading a right-click, putting an item into a slot, asking the world
 * which entities stand in a cone, launching a projectile, spawning a clone.
 *
 * <p><b>Input is right-click on a hotbar slot and nothing else</b> (ADR-005). There are no keybinds
 * and no client requirement. A second right-click on the slot of a running sustained ability ends
 * it - same slot, same button, no second control (FR-055a).
 *
 * <p>The ability items are locked by B07's {@code EquipmentLockListener}, because they carry its
 * {@code BoundItemTag}. Nothing here re-implements that. A second tag carries the ability id, so the
 * ability is never derived from the material - an obtained goat horn grants nothing (FR-058).
 */
package rpg.platform.ability;

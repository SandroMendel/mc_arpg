/**
 * B07 platform bindings - selection menu, inventory lock and bound item construction.
 *
 * <p>The rules live in {@code rpg.core.classes} without a single Bukkit reference; only the
 * listeners and the item construction live here. That is the same split B05 used for
 * {@code VanillaDamageListener}: the layer boundary does not have to follow the block boundary.
 *
 * <p>The one thing this package does that no other does: it <b>suppresses vanilla behaviour</b>. A
 * class weapon carries an explicitly empty attribute modifier set, because otherwise the weapon type
 * would be an unmodelled ninth stat source and ADR-008 knows only eight attributes.
 */
package rpg.platform.classes;

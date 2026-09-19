/**
 * Bukkit-free performance measurement and alert rules for B15.
 *
 * <p>This package stores primitive durations and immutable snapshots only. It deliberately cannot
 * reach Paper, schedule work, perform I/O or call a database. Platform adapters belong in
 * {@code rpg-platform}; reporting and exporting belong in the plugin layer.
 */
package rpg.core.performance;

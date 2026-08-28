/**
 * Provisional commands. <b>This package is meant to be deleted.</b>
 *
 * <p>Commands, the permission tree and tab completion belong to B14 - layer 3, and it depends on
 * every other block, which makes it the last one, not the next. When this package was written there
 * was <b>not a single command</b> anywhere in the project and {@code plugin.yml} had no
 * {@code commands} block at all.
 *
 * <p><b>Why it exists anyway</b> (ADR-028): B08b has to let an operator correct a balance, and an
 * interface with no way to call it is present and unusable. The violation of Constitution III is
 * real and was accepted explicitly rather than defined away - the governance rule requires a
 * reasoned exception, and ADR-028 is it.
 *
 * <p><b>How the violation is contained.</b> Every rule lives in {@code rpg.core.currency}:
 * bukkit-free, permission-free, testable without a server. What lives here parses arguments, checks
 * a permission and calls. Nothing else. B14 replaces this shell and leaves the interface standing
 * (FR-046).
 *
 * <p>So the measure of a class in this package is how little it contains. Anything that grows a rule
 * here is in the wrong module.
 */
package rpg.plugin.command;

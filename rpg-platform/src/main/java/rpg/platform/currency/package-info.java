/**
 * B08b, Paper side - the coin piles that lie in the world, and the window that shows a ledger.
 *
 * <p><b>This is the only package of the block that touches Paper.</b> Everything about what a
 * booking <em>means</em> lives in {@code rpg.core.currency}, which has no Bukkit dependency and is
 * testable without a server (Constitution III).
 *
 * <p><b>A coin pile is a plain vanilla {@code Item} entity, and that is a decision, not a
 * shortcut.</b> Three requirements fall out of it without a line of runtime code of our own:
 *
 * <ul>
 *   <li>{@code Item.setOwner} keeps everyone else from picking it up (FR-027),
 *   <li>the vanilla despawn clears away what nobody collected (FR-029),
 *   <li>and the server ticks item entities anyway, so this block schedules <b>nothing</b> (FR-030).
 * </ul>
 *
 * <p>The alternative - a pile registry of our own - needed a sweep to clear expired piles. The only
 * sweep that exists runs <em>asynchronously</em> ({@code startAbilitySweep}, every 500 ms) and must
 * not touch the Bukkit API, so a second, synchronous one would have been necessary; {@code Scheduler}
 * deliberately offers no recurring synchronous task, and adding one would have carried the weight of
 * ADR-010 and ADR-024 for something vanilla does for free.
 *
 * <p><b>Two things vanilla does not do, and this package therefore does.</b>
 *
 * <ol>
 *   <li><b>Merging is a hazard here, not a feature.</b> Vanilla merges similar stacks by adding
 *       their <em>counts</em>. With the amount in the data container, two piles of 500 would become
 *       one stack of two carrying 500 - the player would silently lose half. Every pile therefore
 *       carries a unique id, which makes no two piles similar and keeps vanilla away from them.
 *       Merging happens <em>before</em> a pile is created instead (FR-028).
 *   <li><b>{@code setOwner} knows players; ADR-011 knows characters.</b> A player has up to three,
 *       and B03 lets them switch mid-session. Without the second check, character B would collect
 *       what character A earned. Vanilla filters coarsely and for free; this package checks exactly.
 * </ol>
 *
 * <p><b>A pile is visible only to the character entitled to it</b> (FR-027a). A visible pile that
 * cannot be picked up looks like a bug from the player's side - they walk over it, nothing happens,
 * and nobody tells them why. Invisibility is the honest lock. The pickup lock stays in place all the
 * same: invisibility is presentation, and presentation is never the authority (Constitution VI).
 *
 * <p><b>The window is provisional</b> (ADR-028). Display belongs to B13; until it exists, this
 * package builds a selection and a paged ledger view from pure vanilla materials (ADR-005), after
 * the pattern of {@code ClassSelectionMenu} in B07.
 */
package rpg.platform.currency;

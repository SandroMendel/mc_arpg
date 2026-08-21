/**
 * B06 - experience and levels, independent of vanilla XP.
 *
 * <p><b>What this block owns.</b> The XP curve, the level a character has reached, what a level-up
 * does to the eight attributes, the party model and the rules for splitting experience. Nothing
 * else.
 *
 * <p><b>What it deliberately does not own</b> - each of these belongs to a block that has not been
 * built yet, and reaching into them from here would put the same decision in two places:
 *
 * <ul>
 *   <li><b>B07 classes</b>: per-class base values and growth curves. B06 ships one class-neutral
 *       growth table in configuration; B07 replaces the numbers, not the mechanism.
 *   <li><b>B08 abilities</b>: unlocking by level, coin upgrades, mana. B06 publishes the level-up
 *       event and answers the level query.
 *   <li><b>B09 zones</b>: zone geometry, access rules, zone objectives. B06 offers the level query
 *       and one entry point every further XP source uses.
 *   <li><b>B10 mobs</b>: what a mob <i>is</i>. B06 bridges the XP amount per mob type behind
 *       {@link rpg.core.progression.MobXpProvider} until then.
 *   <li><b>B11 items</b>: item definitions and their level requirements. B06 offers the query.
 *   <li><b>B12 statistics</b>: evaluation and leaderboards. B06 publishes the events.
 *   <li><b>B13 UI</b>: progress bar, party display, level-up message. B06 publishes bundled events
 *       and never a display object.
 *   <li><b>B14 commands</b>: {@code /party invite}, {@code /party kick}, {@code /xp set}. B06
 *       provides the contracts those commands call.
 * </ul>
 *
 * <p><b>No commands and no display live here.</b> Not one string a player sees is written in this
 * package; everything goes through {@link rpg.core.progression.ProgressionMessageKeys} (FR-037,
 * FR-038).
 *
 * <p><b>Two promises that shape every class here.</b> No database access per XP event - a gain
 * marks the character and nothing more (FR-054). And no scheduled work per player, character or
 * party - invite expiry and the bundling window are timestamps evaluated when something asks
 * (FR-061), the same pattern B05 uses for its attack window.
 */
package rpg.core.progression;

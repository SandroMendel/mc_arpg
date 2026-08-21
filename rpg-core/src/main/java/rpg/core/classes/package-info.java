/**
 * B07 - the three playable classes.
 *
 * <p>Two kinds of data live here, and keeping them apart is the point of this package:
 *
 * <ul>
 *   <li><b>Definition</b> - immutable, loaded from {@code classes.yml}, exactly <b>three</b> objects
 *       for the whole server. Never per player.
 *   <li><b>Progress</b> - mutable, per character, persisted: the reached armor tier and weapon tier.
 *       Two integers.
 * </ul>
 *
 * <p>Everything else is derived. In particular the worn items are <b>not</b> data - they are a
 * rendering of the progress. The direction is one-way: the tier produces the item, never the other
 * way round. That is what makes "tier reached but item missing" heal itself on the next load, and it
 * is what makes item tampering unable to grant a tier.
 *
 * <h2>Where the block ends</h2>
 *
 * <ul>
 *   <li><b>B08 Abilities</b> - this package <b>names</b> ability ids and unlock levels; it never
 *       resolves them. What an ability does, which hotbar slot it takes and what it costs is B08.
 *   <li><b>B11 Items</b> - the {@code cost} block of a tier is passed through <b>uninterpreted</b>.
 *       This package knows nothing about coins, materials or prices.
 *   <li><b>B13 HUD</b> - display names are message keys, never text. Titles and sounds are B13.
 * </ul>
 *
 * <h2>Why the values are base values and not modifiers</h2>
 *
 * <p>ADR-017 makes the class ladder the dominant stat source - roughly 70% of end power. The
 * modifier band from B04 is laid around the <b>effective</b> base value. Were the tier values FLAT
 * modifiers, the band would stay pinned at the level-1 base: a band of +-30% around 40 health would
 * never admit the 1400 a tier-5 warrior carries, and the value would be clamped unnoticed. So the
 * contribution goes through {@link rpg.core.stats.BaseStatContributor}, and
 * {@link rpg.core.stats.SourceKind#CLASS} stays <b>unused</b> - the same choice B06 made for
 * {@code SourceKind.LEVEL} (ADR-015).
 */
package rpg.core.classes;

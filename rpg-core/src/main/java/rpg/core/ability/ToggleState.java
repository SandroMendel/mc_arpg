package rpg.core.ability;

/**
 * The player's setting on a passive ability that may be switched off (FR-052d).
 *
 * <p>Exactly one ability needs this today - the mage's Rise &amp; Fall, whose double jump changes how
 * moving through the world feels. A player who wants the same movement as the other classes can turn
 * it off, or keep the jump without the slow fall.
 *
 * <p>Three states rather than a boolean, because {@link #PARTIAL} is not "half on": it is a
 * meaning the ability itself defines. What it does is written in that ability's definition, not here.
 */
public enum ToggleState {

    /** The ability works as designed. The default for every ability that has no setting. */
    ON,

    /** The player turned it off. It has no effect at all. */
    OFF,

    /** The ability's own middle setting - for Rise &amp; Fall: the jump, but no slow fall. */
    PARTIAL
}

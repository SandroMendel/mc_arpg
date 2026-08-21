package rpg.core.classes;

import java.util.Set;

import rpg.core.stats.Attribute;

/**
 * Which of the two ladders a tier belongs to.
 *
 * <p>Two ladders, not one, and each carries four of the eight attributes. Every attribute therefore
 * has exactly one ladder as its source. On a single ladder the second would be meaningless.
 */
public enum LadderSlot {

    /** Armour carries the defensive four. */
    ARMOR(
            Set.of(
                    Attribute.HEALTH,
                    Attribute.DEFENSE,
                    Attribute.MANA,
                    Attribute.MOVEMENT_SPEED)),

    /** The weapon carries the offensive four. */
    WEAPON(
            Set.of(
                    Attribute.PHYSICAL_DAMAGE,
                    Attribute.MAGIC_DAMAGE,
                    Attribute.ATTACK_SPEED,
                    Attribute.ABILITY_COOLDOWN));

    private final Set<Attribute> carried;

    LadderSlot(Set<Attribute> carried) {
        this.carried = carried;
    }

    /** The attributes a tier of this slot must define - all of them, none extra (FR-015). */
    public Set<Attribute> carried() {
        return carried;
    }

    /** The config key under a class block. */
    public String configKey() {
        return this == ARMOR ? "armor-ladder" : "weapon-ladder";
    }
}

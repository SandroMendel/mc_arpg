package rpg.platform.hud;

import java.util.Optional;
import java.util.UUID;

/**
 * The numbers a readout needs about a holder.
 *
 * <p>A reading, not the engine. B04's {@code StatEngine} has twenty-one methods, twenty of which a
 * status line has no business calling - and a display that could {@code apply} a modifier or remove a
 * holder is one refactor away from doing it by accident. This is the whole of what the action bar and
 * the target line consume.
 *
 * <p>The plugin adapts B04 to this. Both are equally happy with a mob or a player: the display decides
 * who is worth drawing for.
 */
public interface CombatStatusSource {

    /** Empty for anything outside the stat system - an ordinary animal, or an id nobody knows. */
    Optional<Status> statusOf(UUID holderId);

    /**
     * @param health what is left
     * @param maxHealth what it is measured against, from the same calculation round
     * @param mana what is left of it
     * @param maxMana what that is measured against, from the same calculation round
     * @param defense the mitigation attribute, shown as a number rather than a percentage because it
     *     is the value a player compares between two pieces of equipment
     */
    record Status(double health, double maxHealth, double mana, double maxMana, double defense) {

        /** In {@code [0, 1]}; zero for a maximum of zero, which no live holder has. */
        public double fraction() {
            return maxHealth <= 0.0 ? 0.0 : health / maxHealth;
        }

        /** Rounded, because a tenth of a hit point is noise at this scale. */
        public int percent() {
            return (int) Math.round(fraction() * 100.0);
        }

        /**
         * A mob has no mana worth showing.
         *
         * <p>Its maximum is zero, and the readout leaves the mana part out rather than printing
         * {@code 0/0} - a number that says nothing takes space from three that say something.
         */
        public boolean hasMana() {
            return maxMana > 0.0;
        }
    }
}

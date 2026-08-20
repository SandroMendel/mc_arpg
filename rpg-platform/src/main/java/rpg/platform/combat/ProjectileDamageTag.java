package rpg.platform.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The raw damage a projectile carries from the moment it was launched (FR-024b, research.md E3).
 *
 * <p>A single number on the projectile, not a snapshot in a map. The obvious alternative - a map
 * from projectile id to snapshot - leaks with certainty: an arrow that disappears in an unloaded
 * chunk never removes its entry, and cleaning that up would need exactly the sweeping task
 * Principle II avoids. A number on the projectile disappears with the projectile.
 */
public final class ProjectileDamageTag {

    private static NamespacedKey key;

    private ProjectileDamageTag() {}

    /** Called once at startup; the key needs the plugin instance. */
    public static void initialise(Plugin plugin) {
        key = new NamespacedKey(plugin, "combat_raw_damage");
    }

    /** Stores the raw damage worked out at launch. */
    public static void write(Projectile projectile, double rawDamage) {
        if (key != null) {
            projectile.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, rawDamage);
        }
    }

    /**
     * The stored raw damage, or {@code NaN} if this projectile is not one of ours.
     *
     * <p>A dispenser arrow has no tag, and that is a legitimate case: it is neutralised and applies
     * nothing, rather than being priced with values nobody computed.
     */
    public static double read(Projectile projectile) {
        if (key == null) {
            return Double.NaN;
        }
        Double stored =
                projectile.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return stored == null ? Double.NaN : stored;
    }
}

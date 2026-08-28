package rpg.platform.item;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;

import rpg.core.item.ItemTemplate;

/**
 * Welche Verbrauchbaren <b>geworfen</b> werden statt getrunken.
 *
 * <p><b>Das Material entscheidet, und zwar allein.</b> Ein {@code SPLASH_POTION} wird von Vanilla
 * geworfen — das ist keine Eigenschaft, die dieser Block erfinden müsste, sondern eine, die er
 * <em>lesen</em> kann. Ein zusätzlicher Schlüssel {@code splash: true} in {@code items.yml} wäre
 * eine zweite Wahrheit über dieselbe Sache, und die beiden könnten sich widersprechen: ein
 * {@code POTION} mit {@code splash: true} wäre ein Trank, den niemand werfen kann, und der Fehler
 * fiele erst im Spiel auf.
 *
 * <p><b>Und deshalb steht diese Klasse in der Plattformschicht.</b> {@code rpg-core} kennt das
 * Material nur als Zeichenkette und hat keinen Anlass, Vanilla-Namen zu deuten (Prinzip III). Wo die
 * Antwort im Kern gebraucht wird — bei der Frage, ob ein Trank überhaupt etwas bewirkte —, kommt sie
 * über die Naht {@code ConsumableUse.WouldDoSomething} herein.
 */
public final class ThrownConsumables {

    /**
     * Die beiden Vanilla-Materialien, die geworfen werden.
     *
     * <p>Der verweilende Trank ist mit dabei, obwohl dieser Block ihn heute nicht ausliefert: er
     * verhält sich beim Werfen genauso, und ein Betreiber, der ihn in {@code items.yml} einträgt,
     * soll keinen Trank bekommen, der beim Rechtsklick verschwindet.
     */
    private static final Set<Material> THROWN =
            Set.of(Material.SPLASH_POTION, Material.LINGERING_POTION);

    private ThrownConsumables() {}

    /** Ob diese Vorlage geworfen wird. Falsch für alles, dessen Material Vanilla nicht kennt. */
    public static boolean isThrown(ItemTemplate template) {
        return template != null && isThrown(materialOf(template));
    }

    public static boolean isThrown(Material material) {
        return material != null && THROWN.contains(material);
    }

    private static Material materialOf(ItemTemplate template) {
        return Material.matchMaterial(template.material().toUpperCase(Locale.ROOT));
    }
}

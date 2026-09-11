package rpg.platform.mob;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Was eine Kreatur dieses Blocks mit sich traegt.
 *
 * <p><b>Im Persistent Data Container, nicht im Namen.</b> Dieselbe Wahl, die {@code CoinPileTag} fuer
 * den Haufen und {@code BoundItemTag} in B07 getroffen haben, und aus demselben Grund: ein Name ist
 * Anzeige, und Anzeige ist etwas, worueber man einen Client belügen kann. Der Container ueberlebt
 * ausserdem ein Entladen des Chunks und einen Neustart.
 *
 * <p>Zwei Werte, und beide sind tragend:
 *
 * <ul>
 *   <li><b>kind</b> - welche Art. Der Schluessel, mit dem die drei uebernommenen Schnittstellen
 *       gefragt werden. Ohne ihn waeren vier Arten auf {@code ZOMBIE} vier Mal derselbe Schluessel,
 *       und genau daran ist die bisherige Loesung gescheitert.
 *   <li><b>zone</b> - aus welcher Region sie stammt. <em>Nicht</em>, in welcher sie steht: bewegt
 *       sie sich ueber die Grenze, bleibt sie dem Budget zugerechnet, aus dem sie kam (FR-017).
 *       Aus ihrer Position waere das nicht mehr zu erfahren, und ohne den Vermerk liesse sich das
 *       Budget umgehen, indem man Kreaturen hinausschiebt.
 * </ul>
 *
 * <p><b>Warum der Vermerk einen Chunk-Unload ueberleben muss</b>, wo FR-019 die Kreatur ohnehin
 * entfernt, sobald niemand da ist: die beiden Zeitpunkte fallen nicht zusammen. Ein Chunk kann
 * entladen sein, waehrend in der Zone noch Spieler stehen, und die Aufraeumfrist laeuft erst danach.
 */
public final class MobKindTag {

    /** Ein Schluessel je Wert, beide unter einem Namensraum. Fest, denn sie ueberleben einen Start. */
    static final NamespacedKey KIND =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:mob_kind"));

    static final NamespacedKey ZONE =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:mob_zone"));

    private MobKindTag() {}

    /** Heftet Art und Ursprungszone an. Nur beim Setzen; nichts anderes darf eine Kreatur zeichnen. */
    public static void mark(Entity entity, String kindKey, String zoneKey) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(kindKey, "kindKey");
        Objects.requireNonNull(zoneKey, "zoneKey");
        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.set(KIND, PersistentDataType.STRING, kindKey);
        container.set(ZONE, PersistentDataType.STRING, zoneKey);
    }

    /** Die Art dieser Kreatur, falls sie eine dieses Blocks ist. */
    public static Optional<String> kindOf(Entity entity) {
        return read(entity, KIND);
    }

    /** Die Ursprungszone, falls es eine gibt. */
    public static Optional<String> zoneOf(Entity entity) {
        return read(entity, ZONE);
    }

    /** Ob dieser Block diese Kreatur gesetzt hat. */
    public static boolean isOurs(Entity entity) {
        return kindOf(entity).isPresent();
    }

    /**
     * <b>Die eine Ableitung</b>, mit der die drei uebernommenen Schnittstellen gefuettert werden.
     *
     * <p>Der Vermerk, und wenn es keinen gibt, der Vanilla-Typname. Das loest zwei Dinge auf einmal:
     *
     * <p>Erstens die Verwechslung. Bis heute fuettern vier Aufrufstellen die Schnittstellen mit
     * {@code entity.getType().name()}, und damit sind vier Arten auf {@code ZOMBIE} nicht
     * auseinanderzuhalten - dieselben Werte, dieselbe Erfahrung, dieselben Coins. Mit dem Vermerk
     * sind sie vier verschiedene Schluessel.
     *
     * <p>Zweitens die Rueckwaertsvertraeglichkeit. Eine Kreatur ohne Vermerk antwortet weiterhin mit
     * ihrem Vanilla-Typnamen, also greifen die vorhandenen Eintraege in {@code combat.yml} und die
     * konfigurierten Standardwerte unveraendert (FR-009). Die Uebergangsregelung aus B05 hoert nicht
     * auf zu funktionieren - sie wird nur noch von dem bedient, was sie immer gemeint hat.
     *
     * <p><b>Diese Methode ist die einzige erlaubte Ableitung.</b> {@code NoRawTypeNameLeftTest}
     * haelt fest, dass kein Produktivcode mehr {@code getType().name()} in eine der drei
     * Schnittstellen fuehrt - sonst setzt der naechste Listener wieder den Vanilla-Namen ein, und
     * niemand merkt es.
     */
    public static String kindKeyOf(Entity entity) {
        if (entity == null) {
            return "UNKNOWN";
        }
        return kindOf(entity).orElseGet(() -> entity.getType().name());
    }

    private static Optional<String> read(Entity entity, NamespacedKey key) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                entity.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
}

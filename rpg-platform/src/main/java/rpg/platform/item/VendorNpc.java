package rpg.platform.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Der Händler einer Region — einer je Safe-Core, sechs insgesamt (FR-057).
 *
 * <p><b>Er ist kein Mob.</b> B10 liefert die Technik, mit der eine Entität gesetzt wird, und das ist
 * auch alles: er wird nie in {@code HordeRegistry} eingetragen und zählt deshalb nicht gegen das
 * Budget (FR-059, research.md R7). Das ist keine Ausnahme, die jemand pflegen muss — es folgt
 * daraus, dass B10 nur zählt, was B10 selbst gesetzt hat.
 *
 * <p><b>Unverwundbar, unbeweglich, still.</b> Ein Händler, den man umbringen kann, ist ein Händler,
 * der irgendwann weg ist; einer, der wegläuft, ist einer, den man sucht. Und Vanillas
 * Dorfbewohner-Handel wird abgeschaltet: was er anbietet, steht in {@code items.yml}, nicht in einer
 * zufällig erwürfelten Vanilla-Tabelle.
 *
 * <p><b>Der Vermerk sagt, zu welcher Region er gehört.</b> Daraus folgt sein Bestand — und dass zwei
 * NPCs verschiedener Regionen verschiedene Dinge führen, ohne dass jemand eine Liste von Positionen
 * pflegt.
 */
public final class VendorNpc {

    /** Fest, denn der Vermerk überlebt einen Neustart. */
    static final NamespacedKey ZONE =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:vendor_zone"));

    /** Wie weit um den Setzpunkt herum nach einem Vorgänger gesucht wird. */
    private static final double RADIUS = 4.0;

    private final Logger logger;

    /** Wo welcher Händler steht — damit ein Neustart keinen zweiten daneben setzt. */
    private final Map<String, UUID> placed = new HashMap<>();

    public VendorNpc(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Setzt den Händler einer Region.
     *
     * <p>Nach dem Muster von {@code PaperMobPlacer}: der Vermerk wird im Consumer gesetzt,
     * <b>bevor</b> das Spawn-Ereignis läuft. Danach wäre zu spät — ein Zuhörer hätte die Entität
     * dann schon als gewöhnlichen Dorfbewohner gesehen.
     *
     * @return die gesetzte Entität, oder leer, wenn die Welt sie nicht angenommen hat
     */
    public Optional<Entity> place(Location where, String zoneKey) {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(zoneKey, "zoneKey");
        if (where.getWorld() == null) {
            return Optional.empty();
        }
        removePrevious(where, zoneKey);
        try {
            Entity npc =
                    where.getWorld()
                            .spawn(
                                    where,
                                    Villager.class,
                                    entity ->
                                            entity.getPersistentDataContainer()
                                                    .set(ZONE, PersistentDataType.STRING, zoneKey));
            harden(npc);
            placed.put(zoneKey, npc.getUniqueId());
            return Optional.of(npc);
        } catch (RuntimeException failure) {
            // Ein Haendler, der nicht gesetzt werden konnte, darf den Start nicht anhalten - er
            // fehlt dann in einer Region, und das ist im Log zu sehen (Prinzip VI).
            logger.log(
                    Level.WARNING, "[item] could not place the vendor of " + zoneKey, failure);
            return Optional.empty();
        }
    }

    /**
     * Entfernt den Händler, der hier vom letzten Start noch steht.
     *
     * <p><b>Er ist persistent, und das muss er sein</b> — sonst wäre er nach dem ersten
     * Chunk-Entladen weg. Genau deshalb überlebt er aber auch einen Neustart, und ohne diese Zeile
     * stünden nach der zehnten Sitzung zehn Händler nebeneinander. Aufgeräumt wird vor dem Setzen,
     * nicht beim Herunterfahren: ein Absturz hat kein Herunterfahren.
     */
    private void removePrevious(Location where, String zoneKey) {
        try {
            for (Entity nearby : where.getWorld().getNearbyEntities(where, RADIUS, RADIUS, RADIUS)) {
                if (zoneOf(nearby).filter(zoneKey::equals).isPresent()) {
                    nearby.remove();
                }
            }
        } catch (RuntimeException failure) {
            // Ein Doppelgaenger ist unschoen; ein Start, der daran scheitert, ist schlimmer.
            logger.warning(
                    () -> "[item] vendor: could not clear the previous merchant of " + zoneKey);
        }
    }

    /** Zu welcher Region dieser NPC gehört, falls er einer von uns ist. */
    public static Optional<String> zoneOf(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        return Optional.ofNullable(container.get(ZONE, PersistentDataType.STRING));
    }

    /** Ob dieser Block diese Entität gesetzt hat. */
    public static boolean isVendor(Entity entity) {
        return zoneOf(entity).isPresent();
    }

    /** Wie viele Händler gesetzt wurden — für den Start und für Tests. */
    public int count() {
        return placed.size();
    }

    /**
     * Was einen Händler zu einem Händler macht und nicht zu einem Dorfbewohner.
     *
     * <p>Jede Zeile hier verhindert etwas, das auf einem echten Server sonst passiert: er stirbt,
     * er läuft weg, er wird geschoben, er verschwindet in der Ferne, oder er öffnet Vanillas
     * Handelsfenster statt unserem.
     *
     * <p><b>Jede für sich, und keine reißt die anderen mit.</b> Genau das lief hier zuerst falsch:
     * die Härtung stand im Spawn-Consumer und alles hing an einem einzigen {@code try}. Ein
     * Testdouble, das {@code setRemoveWhenFarAway} nicht kennt, ließ damit den <em>ganzen</em>
     * Händler ausfallen — und der Ausfall wurde als Warnung geloggt und sah aus wie eine Umgebung,
     * die eben nichts kann. {@code PaperMobPlacer.suppressVanillaDespawn} hatte dieselbe Lehre
     * schon aufgeschrieben; hier steht sie ein zweites Mal, weil sie ein zweites Mal gebraucht
     * wurde.
     */
    private void harden(Entity entity) {
        apply("invulnerable", () -> entity.setInvulnerable(true));
        apply("persistent", () -> entity.setPersistent(true));
        apply("silent", () -> entity.setSilent(true));
        if (entity instanceof Villager villager) {
            apply("ai", () -> villager.setAI(false));
            apply("collidable", () -> villager.setCollidable(false));
            // Vanillas Distanz-Despawn, dieselbe Sperre wie bei B10s Kreaturen (FR-022).
            apply("despawn", () -> villager.setRemoveWhenFarAway(false));
            // Vanillas Handel aus: was er anbietet, steht in items.yml. Ein leeres Angebot ist
            // die einzige Art, Bukkits Handelsfenster verlaesslich stumm zu stellen.
            apply("recipes", () -> villager.setRecipes(java.util.List.of()));
        }
    }

    /** Eine Eigenschaft setzen, und ihr Fehlschlag bleibt ihrer. */
    private void apply(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            logger.warning(() -> "[item] vendor: could not set " + what + ": " + failure);
        }
    }

    /** Vergisst, wo Händler standen — beim Herunterfahren. */
    public void clear() {
        placed.clear();
    }
}

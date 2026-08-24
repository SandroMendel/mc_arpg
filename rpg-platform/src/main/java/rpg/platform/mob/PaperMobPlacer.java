package rpg.platform.mob;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import rpg.core.mob.MobKind;

/**
 * Setzt eine Kreatur in die Welt und heftet ihr an, was sie ist.
 *
 * <p><b>Der Vermerk wird geschrieben, BEVOR die Entitaet erscheint.</b> Das ist der ganze Trick
 * dieser Klasse: {@code world.spawn(location, type, consumer)} laesst den Consumer laufen, bevor das
 * Spawn-Ereignis ausgeloest wird. Damit steht die Art schon fest, wenn B05s
 * {@code MobEquipmentListener} die Kreatur sieht - und der holt sich ihre Werte ueber
 * {@link MobKindTag#kindKeyOf} und damit ueber die Art statt ueber den Vanilla-Typ.
 *
 * <p><b>Deshalb setzt diese Klasse die Attribute nicht selbst.</b> Es gaebe zwei Wege, sie zu
 * setzen - hier und im Listener -, und zwei Wege sind einer zu viel: der eine wuerde beim naechsten
 * Umbau vergessen, und eine Kreatur haette dann Werte, die von woanders kommen als die der
 * naechsten. Was hier gesetzt wird, ist nur das, was der Listener nicht kennt: die
 * Zielsuchreichweite, denn die gehoert der Art und nicht dem Kampfmodell.
 *
 * <p><b>Kein oeffentlicher Weg fuer andere Bloecke.</b> Diese Klasse ist paketintern erreichbar und
 * steht in keinem Vertrag. Eine Methode, mit der ein anderer Block eine Kreatur erzeugen koennte,
 * waere eine Umgehung des Budgets mit unserem eigenen Segen.
 */
public final class PaperMobPlacer {

    private final Logger logger;

    public PaperMobPlacer(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Setzt eine Kreatur dieser Art an diese Stelle.
     *
     * @param kind was gesetzt wird
     * @param where wohin - muss im Servertakt und in einer geladenen Welt liegen
     * @param zoneKey die Ursprungszone, die sie dauerhaft traegt (FR-017)
     * @return die Entitaet, oder leer, wenn sie nicht entstehen konnte
     */
    public Optional<Entity> place(MobKind kind, Location where, String zoneKey) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(zoneKey, "zoneKey");

        Class<? extends Entity> type = entityClassOf(kind);
        if (type == null) {
            return Optional.empty();
        }
        try {
            Entity placed =
                    where.getWorld()
                            .spawn(
                                    where,
                                    type,
                                    entity -> {
                                        // Vor dem Spawn-Ereignis. Danach waere zu spaet: der
                                        // Listener von B05 haette die Kreatur dann schon mit ihrem
                                        // Vanilla-Typnamen bewertet.
                                        MobKindTag.mark(entity, kind.key(), zoneKey);
                                        applyFollowRange(entity, kind);
                                    });
            return Optional.of(placed);
        } catch (RuntimeException failure) {
            // Ein fehlgeschlagenes Setzen darf keinen Durchlauf beenden und keinen Spieler in einen
            // inkonsistenten Zustand bringen (FR-044, Prinzip VI). Der naechste Durchlauf versucht
            // es wieder; das Budget hat nichts verbucht, weil der Bestand erst danach eintraegt.
            logger.warning(
                    () -> "[mob] could not place " + kind.key() + " in " + zoneKey + ": " + failure);
            return Optional.empty();
        }
    }

    /**
     * Die Zielsuchreichweite der Art (FR-036).
     *
     * <p>Die Stellschraube mit dem groessten Hebel: die Zielsuche ist quadratisch im Radius, und
     * Vanillas Standard von 16 bis 48 Bloecken ist fuer eine Horde zu grosszuegig. Genau deshalb
     * bleibt Vanillas Pfadfindung stehen und wird nicht durch eigene AI ersetzt - eigenen Java-Code
     * gegen einen bereits optimierten Pfadfinder zu setzen ist eine Wette, die erst eine Messung
     * rechtfertigt, und die gehoert nach ADR-031 zu B15.
     */
    private void applyFollowRange(Entity entity, MobKind kind) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        try {
            var attribute = living.getAttribute(Attribute.FOLLOW_RANGE);
            if (attribute != null) {
                attribute.setBaseValue(kind.followRange());
            }
        } catch (RuntimeException failure) {
            // Eine Kreatur ohne gesetzte Reichweite ist teurer, aber nicht kaputt.
            logger.warning(
                    () -> "[mob] could not set the follow range of " + kind.key() + ": " + failure);
        }
    }

    /**
     * Der Entitaetstyp hinter dem Namen aus der Konfiguration.
     *
     * <p>Ein unbekannter Name bricht hier <b>nicht</b> den Start ab, sondern wird gemeldet und
     * uebersprungen: geprueft wird er beim Start durch {@link #verifyBasesExist}, und wenn er es bis
     * hierher geschafft hat, ist etwas anderes schiefgegangen als eine Konfiguration.
     */
    private Class<? extends Entity> entityClassOf(MobKind kind) {
        try {
            EntityType type = EntityType.valueOf(kind.base());
            Class<? extends Entity> clazz = type.getEntityClass();
            if (clazz == null) {
                logger.warning(() -> "[mob] " + kind.base() + " cannot be spawned directly");
                return null;
            }
            return clazz;
        } catch (IllegalArgumentException unknown) {
            logger.warning(() -> "[mob] no such entity type: " + kind.base());
            return null;
        }
    }

    /**
     * Prueft beim Start, dass jede Art eine Basis nennt, die es wirklich gibt (FR-002).
     *
     * <p>Kann nicht im Schema stehen: dort ist Bukkit nicht bekannt. Bricht den Start genauso ab wie
     * ein Schemafehler - der Unterschied ist nur, woher die Antwort kommt. Ohne diese Pruefung waere
     * ein Tippfehler in {@code base} eine Art, die nie erscheint, und das sieht aus wie ein kaputter
     * Spawn statt wie ein kaputter Buchstabe.
     */
    public static void verifyBasesExist(Iterable<MobKind> kinds) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (MobKind kind : kinds) {
            try {
                EntityType type = EntityType.valueOf(kind.base());
                if (type.getEntityClass() == null) {
                    missing.add(kind.key() + " -> " + kind.base() + " (cannot be spawned)");
                }
            } catch (IllegalArgumentException unknown) {
                missing.add(kind.key() + " -> " + kind.base() + " (no such entity type)");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "mobs.yml names base entity types this server does not have: "
                            + missing
                            + " (FR-002)");
        }
    }
}

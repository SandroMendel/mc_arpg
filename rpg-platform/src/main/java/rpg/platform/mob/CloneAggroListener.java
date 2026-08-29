package rpg.platform.mob;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import rpg.core.mob.MobConfig;
import rpg.core.mob.RetargetThrottle;

/**
 * B10s Anschluss an B08s letzte offene Zusage: solange ein Klon des Rogue steht, wenden sich
 * eigene Kreaturen ihm zu statt dem Spieler (FR-039 bis FR-041, research.md R9).
 *
 * <p><b>Umlenken, nicht {@code setTarget} gegen Vanilla setzen.</b> {@code Mob.setTarget} allein
 * reicht nicht: Vanilla waehlt im naechsten Durchlauf sofort wieder neu, und die Wahl gewinnt -
 * derselbe Fehler, an dem die Blockhaltung des Warriors gescheitert war, bevor sie an einem
 * Ereignis statt gegen Vanilla ansetzte. Die Loesung ist dieselbe: dieser Zuhoerer sieht {@link
 * EntityTargetLivingEntityEvent} in dem Moment, in dem Vanilla die Entscheidung selbst trifft, und
 * setzt nur das Ziel DIESES Ereignisses um.
 *
 * <p><b>Kostet nichts ohne Klon</b> (T096). {@link #onEntityTarget} fragt zuerst, ob {@code
 * activeClones} ueberhaupt einen Eintrag traegt - eine Karte, die fast immer leer ist, weil ein
 * Klon eine seltene, zeitlich begrenzte Faehigkeit ist und nicht der Normalfall.
 *
 * <p><b>Dieselbe Drosselung wie eine gewoehnliche Zielzuweisung</b> (FR-041). Jede Kreatur haelt
 * ihren eigenen {@link RetargetThrottle} - ein Umlenken zaehlt als Zuweisung im Sinn von FR-035, es
 * entsteht keine zweite, ungedrosselte Zielsuche neben der aus US6.
 */
public final class CloneAggroListener implements Listener {

    /** Grob, und das reicht (research.md-Stil): weiter als jede konfigurierte Reichweite. */
    private static final double SCAN_RADIUS = 64.0;

    private final Server server;
    private final Supplier<MobConfig> config;
    private final Clock clock;

    /** Wer gerade einen Klon stehen hat, und welche Entitaet er ist. Fast immer leer. */
    private final Map<UUID, UUID> activeClones = new ConcurrentHashMap<>();

    /** Die eigene Drosselung je Kreatur - dieselbe Bauart wie {@code BossState} je Zone. */
    private final Map<UUID, RetargetThrottle> throttles = new ConcurrentHashMap<>();

    public CloneAggroListener(Server server, Supplier<MobConfig> config, Clock clock) {
        this.server = Objects.requireNonNull(server, "server");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Installiert als {@code SummonEffect.AggressionRedirect} - der Klon steht, ab jetzt zieht er
     * an (FR-039).
     *
     * <p><b>Holt Kreaturen nach, die den Spieler schon VOR dem Klon als Ziel hatten.</b> FR-039
     * verlangt "solange ein Klon steht", nicht nur "beim naechsten Zuschlagen" - aber Vanilla feuert
     * {@link EntityTargetLivingEntityEvent} nur bei einer NEUEN Zielwahl, nicht mehr, solange das
     * aktuelle Ziel gueltig bleibt. Eine Kreatur, die den Spieler schon jagt, bekaeme dieses Ereignis
     * also nie wieder - der haeufigste Fall in der Praxis (erst angreifen, dann den Klon als Ablenkung
     * stellen). Dieser einmalige Nachtrag im Moment des Erscheinens holt genau diesen Fall nach; ab
     * dann uebernimmt wieder allein das Ereignis, wie {@link #onEntityTarget} es beschreibt.
     */
    public void registerClone(UUID summonerId, UUID creatureId) {
        Objects.requireNonNull(summonerId, "summonerId");
        Objects.requireNonNull(creatureId, "creatureId");
        activeClones.put(summonerId, creatureId);
        reclaimAlreadyTargeting(summonerId, creatureId);
    }

    /**
     * Ob diese Entität gerade ein Klon ist.
     *
     * <p>Öffentlich seit B11: der Verschleiß muss wissen, wer da eingesteckt hat, weil ein Klon
     * weder Waffe noch Rüstung seines Beschwörers abnutzt (FR-041a). Die Antwort steht hier, weil
     * hier ohnehin steht, welcher Klon zu wem gehört — eine zweite Liste dafür wäre eine zweite
     * Wahrheit, und die beiden liefen beim nächsten Umbau auseinander.
     */
    public boolean isClone(UUID entityId) {
        return entityId != null && activeClones.containsValue(entityId);
    }

    /**
     * Wem dieser Klon gehört — leer für alles, was keiner ist.
     *
     * <p><b>Öffentlich seit B12</b>, und aus demselben Grund wie {@link #isClone}: der Schaden
     * eines Klons zählt für seinen Beschwörer (ADR-047), und wer das wissen will, muss von der
     * Kreatur auf den Spieler kommen. Die Zuordnung steht hier; eine zweite Karte in B12 wäre eine
     * zweite Wahrheit über dieselbe Kreatur.
     *
     * <p><b>Dass hier gesucht statt nachgeschlagen wird, ist Absicht.</b> Ein zweiter Index wäre
     * schneller und müsste an jeder der drei Stellen mitgepflegt werden, an denen ein Klon
     * verschwindet — {@code activeClones} ist praktisch immer leer, weil ein Klon nur wenige
     * Sekunden lebt und ihn nur zwei Klassen überhaupt beschwören. Eine Schleife über null bis
     * zwei Einträge kostet weniger als die Gelegenheit, einen der drei Aufräumwege zu vergessen.
     */
    public java.util.Optional<UUID> summonerOf(UUID entityId) {
        if (entityId == null || activeClones.isEmpty()) {
            return java.util.Optional.empty();
        }
        for (Map.Entry<UUID, UUID> entry : activeClones.entrySet()) {
            if (entry.getValue().equals(entityId)) {
                return java.util.Optional.of(entry.getKey());
            }
        }
        return java.util.Optional.empty();
    }

    private void reclaimAlreadyTargeting(UUID summonerId, UUID creatureId) {
        Entity clone = server.getEntity(creatureId);
        if (!(clone instanceof LivingEntity cloneLiving) || !clone.isValid()) {
            return;
        }
        for (Entity nearby : clone.getNearbyEntities(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
            if (!(nearby instanceof Mob mob) || !MobKindTag.isOurs(mob)) {
                continue;
            }
            if (!(mob.getTarget() instanceof Player currentTarget)
                    || !currentTarget.getUniqueId().equals(summonerId)) {
                continue;
            }
            double range = followRangeOf(mob);
            if (clone.getLocation().distanceSquared(mob.getLocation()) > range * range) {
                continue;
            }
            if (!retarget(mob)) {
                continue;
            }
            mob.setTarget(cloneLiving);
        }
    }

    /**
     * Lenkt eine eigene Kreatur auf den Klon um, wenn sie gerade dessen Besitzer gewaehlt hat und
     * der Klon in ihrer Zielsuchreichweite steht.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (activeClones.isEmpty()) {
            // T096: kein Klon steht - nichts zu tun, und die Karte kostet einen leeren Blick.
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !MobKindTag.isOurs(mob)) {
            // Nur unsere eigenen Kreaturen (research.md R9) - Vanillas eigene Mobs sind nicht
            // dieses Blocks Sache.
            return;
        }
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        UUID cloneId = activeClones.get(player.getUniqueId());
        if (cloneId == null) {
            return;
        }
        Entity clone = mob.getServer().getEntity(cloneId);
        if (!(clone instanceof LivingEntity cloneLiving) || !clone.isValid()) {
            // Der Eintrag ist stehengeblieben, obwohl der Klon schon weg ist - selbstheilend statt
            // auf das Entfernungsereignis zu warten.
            activeClones.remove(player.getUniqueId());
            return;
        }
        double range = followRangeOf(mob);
        if (!clone.getWorld().equals(mob.getWorld())
                || clone.getLocation().distanceSquared(mob.getLocation()) > range * range) {
            return;
        }

        if (!retarget(mob)) {
            return;
        }
        event.setTarget(cloneLiving);
    }

    /** Fragt und merkt die Drosselung aus FR-041 in einem Schritt - derselbe Grund fuer beide Aufrufer. */
    private boolean retarget(Mob mob) {
        RetargetThrottle throttle =
                throttles.computeIfAbsent(mob.getUniqueId(), id -> new RetargetThrottle());
        Instant now = Instant.now(clock);
        if (!throttle.mayRetarget(now, config.get().retargetInterval())) {
            return false;
        }
        throttle.retargeted(now);
        return true;
    }

    /**
     * Ein Klon geht, gleich auf welchem Weg (FR-040): abgelaufen, zerstoert, oder das Plugin
     * schaltet ab. Die naechste Wahl trifft danach wieder den Spieler - ohne dass hier irgendetwas
     * weiter getan werden muss (research.md R9). Raeumt nebenbei die Drosselung einer entfernten
     * Kreatur mit ab, sonst wuechse {@code throttles} unbegrenzt.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityRemove(EntityRemoveEvent event) {
        UUID removedId = event.getEntity().getUniqueId();
        throttles.remove(removedId);
        if (!activeClones.isEmpty()) {
            activeClones.values().removeIf(cloneId -> cloneId.equals(removedId));
        }
    }

    private static double followRangeOf(Mob mob) {
        var attribute = mob.getAttribute(Attribute.FOLLOW_RANGE);
        return attribute != null ? attribute.getValue() : 16.0;
    }
}

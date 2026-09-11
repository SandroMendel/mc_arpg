package rpg.platform.item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.event.EventBus;
import rpg.core.event.Subscription;
import rpg.core.item.LootClaim;
import rpg.core.item.LootDroppedEvent;
import rpg.core.item.LootPlanner;
import rpg.core.progression.WorldPoint;
import rpg.platform.drop.OwnedDrops;

/**
 * Macht aus einem Tod die Gegenstände, die er hinterlässt (FR-018 bis FR-032).
 *
 * <p><b>Kein Bukkit-Listener.</b> Er hängt am Kern-Ereignisbus, wie {@code CoinDropListener} — und
 * aus demselben Grund: wer worauf Anspruch hat, entscheidet {@code rpg-core}, bukkit-frei und ohne
 * Server testbar (research.md R4). Bukkits {@code EntityDeathEvent} kommt hier nicht vor.
 *
 * <p><b>Vanillas eigene Drops sind bereits unterdrückt</b> — B05s {@code CombatDeathListener} räumt
 * {@code getDrops()} und setzt {@code setDroppedExp(0)}. FR-022 ist damit ohne eine Zeile Code
 * dieses Blocks erfüllt, und {@code VanillaDropsStaySuppressedTest} sichert die Zusage (R3).
 *
 * <p><b>Ortsgebunden gesetzt.</b> Der Gegenstand entsteht dort, wo die Kreatur starb — über
 * {@code runSyncAtLocation}, nicht {@code runSyncOnEntity}: ADR-035 hat gezeigt, dass die zweite
 * Variante aus Async-Kontext still scheitert, und die Kreatur ist zu diesem Zeitpunkt ohnehin dabei
 * zu verschwinden.
 *
 * <p><b>Ein Fehler hier darf B05s Todesbehandlung nicht mitreißen</b> (Constitution VI). Jede
 * Ausnahme wird lokal gefangen und protokolliert.
 */
public final class LootDropListener {

    private final Server server;
    private final LootPlanner planner;
    private final ItemStackFactory items;
    private final OwnedDrops drops;
    private final Function<UUID, Optional<Player>> playerOf;
    private final java.util.function.Predicate<String> isBossKind;
    private final EventBus events;
    private final Logger logger;

    private Subscription subscription;

    /**
     * @param playerOf welcher Spieler gerade diesen Charakter spielt; leer, wenn er offline ist —
     *     dann bleibt der Gegenstand unsichtbar und verfällt, was ein gewöhnlicher Ausgang ist
     * @param isBossKind ob eine Artkennung zu einem Boss gehört. <b>Als Prädikat von außen, nicht
     *     als Feld irgendwo:</b> B10 führt die Bosse in {@code mobs.yml} unter
     *     {@code hordes.<zone>.boss.kind}, und die Antwort gehört dorthin. Ein statischer Halter
     *     wäre globaler veränderlicher Zustand im Spielpfad, und den verbietet Prinzip I
     */
    public LootDropListener(
            Server server,
            LootPlanner planner,
            ItemStackFactory items,
            OwnedDrops drops,
            Function<UUID, Optional<Player>> playerOf,
            java.util.function.Predicate<String> isBossKind,
            EventBus events,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.items = Objects.requireNonNull(items, "items");
        this.drops = Objects.requireNonNull(drops, "drops");
        this.playerOf = Objects.requireNonNull(playerOf, "playerOf");
        this.isBossKind = Objects.requireNonNull(isBossKind, "isBossKind");
        this.events = Objects.requireNonNull(events, "events");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Abonniert den Kern-Bus. Einmal beim Start. */
    public void subscribeTo(EventBus bus) {
        Objects.requireNonNull(bus, "bus");
        subscription = bus.subscribe(CombatDeathEvent.class, this::onDeath);
    }

    public void unsubscribe() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    void onDeath(CombatDeathEvent death) {
        if (death.playerVictim()) {
            return;
        }
        try {
            Entity creature = server.getEntity(death.victimId());
            if (creature == null) {
                // Schon entfernt. Ohne Art und ohne Ort gibt es nichts fallen zu lassen, und
                // beides zu raten waere schlechter, als nichts fallen zu lassen.
                return;
            }
            // Die ART, nicht der Vanilla-Typ (B10, FR-020). Vier Arten auf ZOMBIE waeren sonst
            // vier Mal derselbe Schluessel und damit vier Mal dieselbe Beute.
            String kindKey = rpg.platform.mob.MobKindTag.kindKeyOf(creature);
            // Die URSPRUNGSzone, nicht die, in der sie steht (B10, FR-017).
            String zoneKey = rpg.platform.mob.MobKindTag.zoneOf(creature).orElse("");
            boolean boss = isBossKind.test(kindKey);
            Location where = creature.getLocation();

            List<LootClaim> claims =
                    planner.plan(death, kindKey, zoneKey, boss, pointOf(where));
            for (LootClaim claim : claims) {
                realise(claim, where, kindKey);
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[item] could not drop loot for a death", failure);
        }
    }

    private void realise(LootClaim claim, Location where, String kindKey) {
        Optional<ItemStack> stack = items.create(claim.templateKey(), claim.count());
        if (stack.isEmpty()) {
            // Die Vorlage ist zwischen Startpruefung und Tod verschwunden - ein Reload. Nichts
            // fallen zu lassen ist die richtige Antwort; ein Platzhalter waere schlimmer.
            return;
        }
        Player owner = playerOf.apply(claim.ownerCharacterId()).orElse(null);
        drops.drop(stack.get(), where, claim.ownerCharacterId(), owner);
        events.publish(
                new LootDroppedEvent(
                        claim.templateKey(), claim.count(), claim.ownerCharacterId(), kindKey));
    }

    private static WorldPoint pointOf(Location where) {
        if (where == null || where.getWorld() == null) {
            return null;
        }
        return new WorldPoint(
                where.getWorld().getUID(), where.getX(), where.getY(), where.getZ());
    }
}

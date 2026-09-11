package rpg.platform.hud;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import rpg.core.combat.CombatMessageKeys;
import rpg.core.event.EventBus;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.stats.ResourceChangedEvent;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatsRecalculatedEvent;

/**
 * What a creature is, and how much of it is left, floating over its head.
 *
 * <p><b>One line, not two.</b> Vanilla gives an entity exactly one name, and this project ships no
 * resource pack (ADR-005). A health bar <em>under</em> the name would mean a second entity per
 * creature - a text display riding every mob on the server - which is the kind of per-entity cost
 * Constitution II exists to refuse. Name and numbers therefore share the one line they have.
 *
 * <p><b>Only creatures.</b> A player already reads their own numbers off the action bar
 * ({@link StatusActionBar}), and putting a nameplate over them as well would say the same thing twice
 * and reveal it to everybody else besides.
 *
 * <p><b>The name comes from the entity type</b>, not from {@code Entity#getName()}. Once a custom
 * name is set, {@code getName} returns that - so reading it back would feed this line into itself and
 * grow "Zombie 20/20 18/20 15/20" one blow at a time.
 *
 * <p>Like the action bar, this is named for what it is rather than {@code HudRenderer}: Constitution
 * III reserves that name for B13, which will own the layout of all of it.
 *
 * <h2>B13 ist da — und hat diese Klasse NICHT angefasst</h2>
 *
 * <p>Anders als {@code StatusActionBar}, die hinter {@code HudRenderer} gezogen wurde: ein
 * Namensschild ist <b>keine der drei Flächen</b> (FR-001). Es steht über einer Kreatur, nicht auf
 * dem Bildschirm des Spielers, und geht deshalb weder durch den HUD-Takt noch durch die Naht.
 *
 * <p>Dasselbe gilt für {@code PaperVanillaAttributeBridge} (die Herzleiste, ADR-003) und
 * {@code ExperienceBar} (die Vanilla-XP-Leiste, FR-006). Alle drei bespielen Vanilla-Flächen, die
 * B13 nicht ordnet — und die XP-Leiste ist zusätzlich die eine benannte Ausnahme von FR-001
 * (FR-001a): sie zeigt Level und Erfahrung ein zweites Mal, obwohl die Sidebar sie trägt.
 */
public final class MobNameplate {

    private final Server server;
    private final StatEngine stats;
    private final CombatStatusSource status;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Logger logger;

    /**
     * B10s Artabfrage - fuer den Namen und das Level einer Kreatur, die eine Art traegt.
     *
     * <p>Eine Abfrage und nicht die Konfiguration: was diese Zeile braucht, sind zwei Werte je Art,
     * und alles darueber hinaus waere Zugriff auf das Innenleben eines anderen Blocks.
     */
    private final rpg.core.mob.MobKinds kinds;

    public MobNameplate(
            Server server,
            StatEngine stats,
            CombatStatusSource status,
            Scheduler scheduler,
            Messages messages,
            rpg.core.mob.MobKinds kinds,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.status = Objects.requireNonNull(status, "status");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Redraws whenever a creature's health moves or its stats are recalculated.
     *
     * <p>The second one is what puts the plate there in the first place: a mob gets its values once,
     * when B05 builds it, and that raises a recalculation before anything has hit it.
     */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(ResourceChangedEvent.class, event -> show(event.holderId()));
        eventBus.subscribe(StatsRecalculatedEvent.class, event -> show(event.holderId()));
    }

    /** Draws the line for one holder, if it is a creature with values. */
    public void show(UUID holderId) {
        Objects.requireNonNull(holderId, "holderId");
        if (stats.characterIdOf(holderId).isPresent()) {
            // A player character. Their numbers are on their own action bar, where only they see them.
            return;
        }
        Optional<CombatStatusSource.Status> current = status.statusOf(holderId);
        if (current.isEmpty()) {
            return;
        }
        scheduler.runSyncOnEntity(
                new EntityRef(holderId),
                () -> {
                    Entity entity = server.getEntity(holderId);
                    if (entity == null || entity instanceof Player) {
                        return;
                    }
                    try {
                        entity.customName(line(entity, current.get()));
                        entity.setCustomNameVisible(true);
                    } catch (RuntimeException failure) {
                        // A readout must never cost a tick (Constitution VI).
                        logger.warning(
                                "[hud] could not name " + holderId + ": " + failure);
                    }
                });
    }

    /**
     * The line itself, coloured by how much is left.
     *
     * <p>Same three thresholds as the action bar, and for the same reason: three states are readable
     * out of the corner of an eye during a fight, a smooth gradient is not.
     */
    private Component line(Entity entity, CombatStatusSource.Status current) {
        java.util.Optional<rpg.core.mob.MobKind> kind =
                rpg.platform.mob.MobKindTag.kindOf(entity).flatMap(kinds::find);

        Map<String, String> values = new java.util.HashMap<>();
        values.put("health", StatusActionBar.whole(current.health()));
        values.put("max", StatusActionBar.whole(current.maxHealth()));
        values.put("percent", Integer.toString(current.percent()));
        values.put("defense", StatusActionBar.whole(current.defense()));

        // Zwei Texte, und welcher gilt, folgt aus der Kreatur und nicht aus einem Schalter: eine
        // Art aus mobs.yml hat einen Namen und ein Level, ein gewoehnlicher Zombie hat beides
        // nicht. Ein Levelfeld, das bei ihm leer bliebe, waere ein Rest Formatierung ohne Zahl -
        // dieselbe Entscheidung wie beim Mana eines Mobs auf der Actionbar.
        rpg.core.message.MessageKey key;
        if (kind.isPresent()) {
            values.put("name", messages.get(kind.get().displayNameKey()));
            values.put("level", Integer.toString(kind.get().level()));
            key = rpg.core.mob.MobMessageKeys.NAMEPLATE;
        } else {
            values.put("name", prettyName(entity));
            key = CombatMessageKeys.MOB_NAMEPLATE;
        }
        return Component.text(messages.get(key, values)).color(colourFor(current.percent()));
    }

    /**
     * {@code CAVE_SPIDER} to {@code Cave Spider}.
     *
     * <p>Vanilla vocabulary, not this project's wording - the same status a material name in
     * {@code abilities.yml} has, so Constitution V is not in play. What surrounds it <em>is</em>
     * wording and lives in {@code messages.yml}.
     */
    static String prettyName(Entity entity) {
        String raw = entity.getType().name();
        StringBuilder out = new StringBuilder(raw.length());
        for (String word : raw.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.charAt(0))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static NamedTextColor colourFor(int percent) {
        if (percent <= 25) {
            return NamedTextColor.RED;
        }
        return percent <= 60 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
    }
}

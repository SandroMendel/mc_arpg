package rpg.platform.ui;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import rpg.core.combat.DamageDealtEvent;
import rpg.core.event.EventBus;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.WorldPosition;
import rpg.core.ui.DamageNumber;
import rpg.core.ui.UiConfig;

/**
 * Der zugefügte Schaden als kurzlebige Zahl am Trefferort (FR-040 bis FR-045).
 *
 * <h2>Die eine Stelle, an der dieser Block etwas in die Welt schreibt</h2>
 *
 * <p>Und sie schreibt es <b>bewusst flüchtig</b>. Drei Vorkehrungen, jede gegen einen anderen
 * Fehler:
 *
 * <ol>
 *   <li><b>{@code setPersistent(false)}</b> — was der Server nicht speichert, kann ein Absturz
 *       nicht zurücklassen (SC-009). Das ist die genaue Umkehrung von B12s Hologramm, das
 *       persistent sein <em>muss</em> und deshalb vor dem Setzen aufräumt. Eine Schadenszahl, die
 *       dasselbe täte, wäre bei 150 Spielern in Minuten tausendfacher Müll.
 *   <li><b>Nur der Verursacher sieht sie</b> (FR-042). Die Entität ist für alle unsichtbar, und
 *       genau ein Spieler bekommt sie gezeigt. Ohne das sähe jeder im Umkreis fremde Zahlen, und
 *       bei einem Bosskampf wäre der Bildschirm voll.
 *   <li><b>Die Entfernung wird beim Setzen eingeplant</b> (FR-044) — im selben Tick, in dem die
 *       Anzeige entsteht.
 * </ol>
 *
 * <h2>Die Falle aus T112 — und warum sie hier nicht auftritt</h2>
 *
 * <p>Aus einem <b>asynchronen</b> Kontext liefert {@code Scheduler.runSyncOnEntity} für alles außer
 * einem Spieler einen bereits abgebrochenen Handle: ohne Fehler, ohne Log, ohne dass ein Test rot
 * wird. <em>Der Fehler bleibt grün und zeichnet nur manchmal.</em> Genau daran hat B10 zwei Wochen
 * verloren.
 *
 * <p>Diese Klasse betritt die Falle <b>gar nicht erst</b>:
 *
 * <ul>
 *   <li>Sie hängt am {@code EventBus} und nicht am HUD-Takt — {@code DamageDealtEvent} kommt aus dem
 *       Tick, nicht aus dem asynchronen Durchlauf.
 *   <li>Der Sprung zum Setzen geht über {@code runSyncAtLocation} und nicht über eine Entität: eine
 *       Anzeige, die es noch nicht gibt, kann man nicht an ihr selbst festmachen.
 *   <li>Die Entfernung wird <b>innerhalb</b> des Ticks eingeplant, in dem die Anzeige entsteht —
 *       dort ist die Entität aufgelöst, und {@code runSyncOnEntityDelayed} greift auf ein Objekt,
 *       das wirklich da ist.
 * </ul>
 *
 * <p>Das ist der Unterschied zwischen „die Falle umgangen" und „sie gar nicht erst betreten".
 */
public final class DamageNumbers {

    /**
     * Womit eine Anzeige markiert wird — zum Wiedererkennen beim Aufräumen.
     *
     * <p>Nach dem Muster von B12s Hologramm. <b>Obwohl sie nicht persistent ist</b>: die Markierung
     * kostet nichts und macht ein Aufräumen von außen möglich, falls je eine übrig bleibt. Sie ist
     * die zweite Verteidigung hinter {@code setPersistent(false)}, nicht ihr Ersatz.
     */
    static final org.bukkit.NamespacedKey MARK =
            Objects.requireNonNull(org.bukkit.NamespacedKey.fromString("rpg:damage_number"));

    private final org.bukkit.plugin.Plugin plugin;
    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Supplier<UiConfig> config;
    private final Function<UUID, Optional<WorldPosition>> positionOf;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param positionOf wo das getroffene Ziel steht — B05 nennt im Ereignis nur seine Kennung
     */
    public DamageNumbers(
            org.bukkit.plugin.Plugin plugin,
            Server server,
            Scheduler scheduler,
            Messages messages,
            Supplier<UiConfig> config,
            Function<UUID, Optional<WorldPosition>> positionOf,
            Clock clock,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.config = Objects.requireNonNull(config, "config");
        this.positionOf = Objects.requireNonNull(positionOf, "positionOf");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Hört auf den gebündelten Schaden aus B05. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(DamageDealtEvent.class, this::onDamageDealt);
    }

    private void onDamageDealt(DamageDealtEvent event) {
        // ABGESCHALTET HEISST KOSTENLOS (FR-045): die Pruefung steht vor allem anderen, nicht erst
        // vor dem Setzen. Sonst entstuenden Position und Zahl fuer eine Anzeige, die niemand sieht.
        UiConfig current = config.get();
        if (!current.damageNumbers().enabled()) {
            return;
        }
        Optional<UUID> attacker = event.attacker();
        if (attacker.isEmpty()) {
            // Kein Verursacher, also niemand, dem man sie zeigen koennte (FR-042).
            return;
        }
        Optional<WorldPosition> at = positionOf.apply(event.targetId());
        if (at.isEmpty()) {
            // Das Ziel ist schon weg. Ein normaler Ausgang - der letzte Treffer trifft oft eine
            // Kreatur, die im selben Tick entfernt wird.
            return;
        }

        WorldPosition raised =
                new WorldPosition(
                        at.get().worldId(),
                        at.get().x(),
                        at.get().y() + current.damageNumbers().offset(),
                        at.get().z());
        DamageNumber number =
                new DamageNumber(
                        attacker.get(),
                        raised,
                        event.totalDamage(),
                        Math.max(1, event.hitCount()),
                        event.lethal(),
                        clock.instant().plus(current.damageNumbers().lifetime()));

        show(number);
    }

    /**
     * Setzt eine Anzeige und plant ihre Entfernung ein.
     *
     * <p><b>{@code runSyncAtLocation} und nicht {@code runSyncOnEntity}</b>: es gibt noch keine
     * Entität, an der man das festmachen könnte — und aus einem asynchronen Kontext wäre die
     * Entitätsform ein still abgebrochener Handle (R2).
     */
    public void show(DamageNumber number) {
        Objects.requireNonNull(number, "number");
        scheduler.runSyncAtLocation(number.position(), () -> place(number));
    }

    private void place(DamageNumber number) {
        try {
            World world = server.getWorld(number.position().worldId());
            if (world == null) {
                return;
            }
            Player viewer = server.getPlayer(number.viewerId());
            if (viewer == null) {
                return;
            }
            Location where =
                    new Location(
                            world,
                            number.position().x(),
                            number.position().y(),
                            number.position().z());

            TextDisplay display = world.spawn(where, TextDisplay.class, this::soften);
            display.text(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(messages.get(number.key(), number.values())));
            apply(
                    "mark",
                    () ->
                            display.getPersistentDataContainer()
                                    .set(
                                            MARK,
                                            org.bukkit.persistence.PersistentDataType.STRING,
                                            MARK.getKey()));

            // NUR der Verursacher (FR-042): fuer alle unsichtbar, und genau einer bekommt sie
            // gezeigt. Ohne das saehe jeder im Umkreis fremde Zahlen - bei einem Bosskampf waere
            // der Bildschirm voll.
            apply("show-to-dealer", () -> viewer.showEntity(plugin, display));

            // Die Entfernung HIER einplanen, im selben Tick, in dem die Anzeige entsteht (FR-044).
            // Jetzt ist die Entitaet aufgeloest - runSyncOnEntityDelayed greift auf ein Objekt, das
            // wirklich da ist. Aus dem asynchronen Takt heraus waere derselbe Aufruf ein still
            // abgebrochener Handle, und die Zahl bliebe stehen (R2, T112).
            scheduler.runSyncOnEntityDelayed(
                    new EntityRef(display.getUniqueId()),
                    number.remaining(clock.instant()),
                    () -> remove(display));
        } catch (RuntimeException failure) {
            // Eine Anzeige darf nie einen Tick kosten (Constitution VI).
            logger.warning(() -> "[ui] could not place a damage number: " + failure);
        }
    }

    /**
     * Das Gegenstück zu B12s {@code harden}: <b>weich</b>, nicht hart.
     *
     * <p>Jede Eigenschaft für sich, und keine reißt die anderen mit — daran ist B11 einmal
     * aufgelaufen: die Härtung hing an einem einzigen {@code try}, und ein Testdouble, das eine
     * Methode nicht kannte, ließ den <em>ganzen</em> Händler ausfallen.
     */
    private void soften(TextDisplay entity) {
        // DER wichtigste Aufruf dieser Klasse. Ohne ihn ueberlebt jede Zahl einen Neustart, und
        // SC-009 ist gebrochen - fuer jeden Treffer, den je jemand gelandet hat.
        apply("persistent", () -> entity.setPersistent(false));
        apply("invulnerable", () -> entity.setInvulnerable(true));
        apply("silent", () -> entity.setSilent(true));
        apply("gravity", () -> entity.setGravity(false));
        apply("billboard", () -> entity.setBillboard(Display.Billboard.CENTER));
        apply("see-through", () -> entity.setSeeThrough(false));
        apply("alignment", () -> entity.setAlignment(TextDisplay.TextAlignment.CENTER));
        // Fuer alle unsichtbar, bis genau einer sie gezeigt bekommt (FR-042).
        apply("visible-by-default", () -> entity.setVisibleByDefault(false));
    }

    private void remove(TextDisplay entity) {
        apply("remove", entity::remove);
    }

    private void apply(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            logger.warning(() -> "[ui] damage number: could not set " + what + ": " + failure);
        }
    }

    /**
     * Ob dieser Block diese Entität gesetzt hat — für ein Aufräumen von außen.
     *
     * <p>Sollte nie gebraucht werden: {@code setPersistent(false)} sorgt dafür, dass keine übrig
     * bleibt. Die Markierung ist die zweite Verteidigung, und B10 hat gelernt, warum es sie braucht
     * — dort umging der Vanilla-Despawn das eigene Aufräumen, und der Fehler war zwei Wochen lang
     * unsichtbar, weil ein zweiter ihn verdeckte.
     */
    public static boolean isDamageNumber(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer()
                                .get(MARK, org.bukkit.persistence.PersistentDataType.STRING)
                        != null;
    }
}

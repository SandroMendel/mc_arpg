package rpg.platform.statistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import rpg.core.message.Messages;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardEntry;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;
import rpg.platform.item.ItemText;

/**
 * Die Anzeige im Hub — <b>dieselbe Rangliste, ohne dass jemand ein Fenster öffnet</b> (FR-060).
 *
 * <h2>Sie liest denselben Speicherstand und fragt nichts</h2>
 *
 * <p>Kein eigener Takt, keine eigene Abfrage. Sie wird im <em>Auffrischungstakt</em> neu
 * beschriftet, aus demselben {@link Leaderboards}, das auch die Fenster lesen. Eine eigene Abfrage
 * wäre die teuerste von allen: sie liefe, ob jemand hinsieht oder nicht.
 *
 * <h2>Sie ist keine Kreatur</h2>
 *
 * <p>Nach dem Muster von {@code VendorNpc} (R8): sie wird <b>nie</b> in {@code HordeRegistry}
 * eingetragen und zählt deshalb nicht gegen B10s Budget (FR-062). Das ist keine Ausnahme, die
 * jemand pflegen muss — es folgt daraus, dass B10 nur zählt, was B10 selbst gesetzt hat.
 *
 * <h2>Vor dem Setzen aufräumen, nicht beim Herunterfahren</h2>
 *
 * <p>Sie ist persistent und muss es sein, sonst wäre sie nach dem ersten Chunk-Entladen weg. Genau
 * deshalb überlebt sie aber auch einen Neustart, und ohne das Aufräumen stünden nach der zehnten
 * Sitzung zehn Anzeigen ineinander (FR-061, SC-010). Aufgeräumt wird <b>vor</b> dem Setzen: ein
 * Absturz hat kein Herunterfahren.
 */
public final class LeaderboardHologram {

    /** Fest, denn der Vermerk überlebt einen Neustart. */
    static final NamespacedKey MARK =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:leaderboard_hologram"));

    /** Wie weit um den Setzpunkt herum nach einem Vorgänger gesucht wird. */
    private static final double RADIUS = 4.0;

    private final Leaderboards leaderboards;
    private final Messages messages;
    private final Logger logger;

    /** Die gesetzte Anzeige, solange eine steht. */
    private TextDisplay display;

    /** Was sie zeigt — gemerkt, damit die Auffrischung nicht die Konfiguration nachladen muss. */
    private StatisticsConfig.Hologram settings;

    public LeaderboardHologram(Leaderboards leaderboards, Messages messages, Logger logger) {
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Setzt die Anzeige an die konfigurierte Stelle.
     *
     * <p><b>Eine unerreichbare Stelle schaltet sie ab, sie hält den Start nicht auf</b> (FR-064).
     * Ein Server, der wegen einer falsch geschriebenen Welt in {@code statistics.yml} nicht
     * hochkommt, wäre die teuerste denkbare Antwort auf einen Tippfehler — und die Anzeige ist
     * Zierde, nicht Spielmechanik.
     *
     * @return die gesetzte Anzeige, oder leer, wenn keine gesetzt werden konnte
     */
    public Optional<Entity> place(StatisticsConfig.Hologram settings, Instant now) {
        Objects.requireNonNull(settings, "settings");

        World world = Bukkit.getWorld(settings.world());
        if (world == null) {
            logger.warning(
                    () ->
                            "[statistics] hologram disabled: world '"
                                    + settings.world()
                                    + "' is not loaded");
            return Optional.empty();
        }

        Location where = new Location(world, settings.x(), settings.y(), settings.z());
        removePrevious(where);

        try {
            TextDisplay placed =
                    world.spawn(
                            where,
                            TextDisplay.class,
                            // Der Vermerk wird im Consumer gesetzt, BEVOR das Spawn-Ereignis
                            // laeuft - nach dem Muster von PaperMobPlacer. Danach waere zu spaet:
                            // ein Zuhoerer haette die Entitaet dann schon ohne ihn gesehen.
                            entity ->
                                    entity.getPersistentDataContainer()
                                            .set(MARK, PersistentDataType.STRING, settings.board().key()));
            harden(placed);
            this.display = placed;
            this.settings = settings;
            refresh(now);
            return Optional.of(placed);
        } catch (RuntimeException failure) {
            // Eine Anzeige, die nicht gesetzt werden konnte, darf den Start nicht anhalten - sie
            // fehlt dann, und das ist im Log zu sehen (Prinzip VI).
            logger.log(Level.WARNING, "[statistics] could not place the leaderboard hologram", failure);
            return Optional.empty();
        }
    }

    /**
     * Beschriftet die Anzeige neu — <b>aus dem Speicherstand, ohne eine einzige Abfrage</b>
     * (FR-060).
     *
     * <p>Läuft im Auffrischungstakt und ist deshalb billig genug, um jedes Mal zu laufen: ein
     * Kartenzugriff und ein Text.
     */
    public void refresh(Instant now) {
        if (display == null || settings == null) {
            return;
        }
        Component text = render(now);
        apply("text", () -> display.text(text));
    }

    /**
     * Der Inhalt: Kopfzeile, dann die ersten N.
     *
     * <p>Vor der ersten Auffrischung steht dort die Kopfzeile allein. Eine Anzeige, die dann gar
     * nichts sagt, sähe aus wie eine kaputte — und eine, die „niemand dabei" behauptete, wäre eine
     * Lüge über einen Stand, den es noch nicht gibt (FR-035).
     */
    private Component render(Instant now) {
        Optional<Leaderboard> standing = leaderboards.board(settings.board(), settings.period());

        List<Component> lines = new ArrayList<>();
        lines.add(
                ItemText.of(messages,
                        StatisticsMessageKeys.HOLOGRAM_HEADER,
                        Map.of(
                                "board",
                                        messages.get(
                                                StatisticsMessageKeys.boardName(settings.board())),
                                "period",
                                        messages.get(
                                                StatisticsMessageKeys.periodName(
                                                        settings.period())))));

        standing.ifPresent(
                list -> {
                    int shown = 0;
                    for (LeaderboardEntry entry : list.top()) {
                        if (shown++ >= settings.places()) {
                            break;
                        }
                        lines.add(
                                ItemText.of(messages,
                                        StatisticsMessageKeys.HOLOGRAM_LINE,
                                        Map.of(
                                                "rank", String.valueOf(entry.rank()),
                                                "player", entry.displayName(),
                                                "value", String.valueOf(entry.value()))));
                    }
                });

        // Das Alter steht auch hier dabei (FR-032) - und hier ist es am noetigsten: an einem
        // Fenster sieht man, dass man es geoeffnet hat, eine Anzeige im Hub steht einfach da.
        // Ohne diese Zeile ist eine Rangliste, die einen gerade erzielten Wert noch nicht zeigt,
        // aus Sicht des Spielers eine kaputte.
        leaderboards
                .refreshedAt()
                .ifPresent(
                        at ->
                                lines.add(
                                        ItemText.of(
                                                messages,
                                                StatisticsMessageKeys.LEADERBOARD_AS_OF,
                                                Map.of("age", LeaderboardMenu.age(at, now)))));

        Component text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text = text.append(Component.newline());
            }
            text = text.append(lines.get(i));
        }
        return text;
    }

    /**
     * Entfernt die Anzeige, die hier vom letzten Start noch steht (FR-061).
     *
     * <p>Erkannt am Vermerk, nicht an der Entitätsart: eine gewöhnliche Textanzeige, die ein
     * Betreiber dorthin gestellt hat, gehört nicht uns und wird nicht angefasst.
     */
    private void removePrevious(Location where) {
        try {
            for (Entity nearby : where.getWorld().getNearbyEntities(where, RADIUS, RADIUS, RADIUS)) {
                if (isHologram(nearby)) {
                    nearby.remove();
                }
            }
        } catch (RuntimeException failure) {
            // Eine doppelte Anzeige ist unschoen; ein Start, der daran scheitert, ist schlimmer.
            logger.warning(() -> "[statistics] could not clear the previous hologram: " + failure);
        }
    }

    /**
     * Unverwundbar, unbeweglich, kein Ziel, kein Distanz-Despawn (FR-063).
     *
     * <p><b>Jede Eigenschaft für sich, und keine reißt die anderen mit.</b> Genau daran ist B11
     * einmal aufgelaufen: die Härtung hing an einem einzigen {@code try}, und ein Testdouble, das
     * eine Methode nicht kannte, ließ den <em>ganzen</em> Händler ausfallen — geloggt als Warnung,
     * die wie eine Umgebung aussah, die eben nichts kann.
     */
    private void harden(TextDisplay entity) {
        apply("invulnerable", () -> entity.setInvulnerable(true));
        apply("persistent", () -> entity.setPersistent(true));
        apply("silent", () -> entity.setSilent(true));
        apply("gravity", () -> entity.setGravity(false));
        apply("billboard", () -> entity.setBillboard(Display.Billboard.CENTER));
        apply("see-through", () -> entity.setSeeThrough(false));
        apply("alignment", () -> entity.setAlignment(TextDisplay.TextAlignment.CENTER));
    }

    /** Eine Eigenschaft setzen, und ihr Fehlschlag bleibt ihrer. */
    private void apply(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            logger.warning(() -> "[statistics] hologram: could not set " + what + ": " + failure);
        }
    }

    /** Ob dieser Block diese Entität gesetzt hat. */
    public static boolean isHologram(Entity entity) {
        if (entity == null) {
            return false;
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        return container.get(MARK, PersistentDataType.STRING) != null;
    }

    /** Ob gerade eine Anzeige steht — für den Start und für Tests. */
    public boolean isPlaced() {
        return display != null;
    }

    /** Die Kennung der stehenden Anzeige, falls eine steht. */
    public Optional<UUID> placedId() {
        return display == null ? Optional.empty() : Optional.of(display.getUniqueId());
    }

    /** Vergisst die Anzeige — beim Herunterfahren. Entfernt wird beim nächsten Setzen. */
    public void clear() {
        display = null;
        settings = null;
    }
}

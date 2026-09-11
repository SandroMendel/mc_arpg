package rpg.platform.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.HudSurface;
import rpg.core.ui.SidebarLines;

/**
 * Die Vanilla-Umsetzung von {@link HudRenderer}.
 *
 * <h2>Sie ist die Stelle, die in den Tick springt</h2>
 *
 * <p>Der HUD-Takt läuft asynchron (Constitution I.3: 200 Spieler zu zeichnen gehört nicht in den
 * Tick), aber die Paper-API darf <b>nur im Tick</b> angefasst werden (Constitution I.1). Der Sprung
 * passiert hier und nicht in den drei Flächenklassen — sonst stünde er dreimal da, und beim vierten
 * Mal fehlte er.
 *
 * <p><b>{@code runSyncOnEntity} ist hier richtig, weil das Ziel ein Spieler ist</b> (R2). Für alles
 * andere — Kreaturen, Orte, Schadensanzeigen — liefert er aus einem asynchronen Kontext einen
 * bereits abgebrochenen Handle, ohne Fehler und ohne Log. Dort gehört {@code runSyncAtLocation}
 * hin. Das ist die Falle aus T112: <em>der Fehler bleibt grün und zeichnet nur manchmal.</em>
 *
 * <h2>Kein Aufruf bricht einen Aufrufer ab</h2>
 *
 * <p>Vertrag {@code HudRenderer}, Zusage 1. Diese Methoden liegen in Pfaden, die jedes Ereignis
 * berühren; ein Spieler, der gerade gegangen ist, ein Fehler beim Senden — alles davon ist ein
 * normaler Ausgang. {@code StatusActionBar} fängt heute schon lokal und protokolliert, und das gilt
 * für jede Fläche hier.
 *
 * <h2>Der Text kommt aus der Sprachdatei, die Farbcodes auch</h2>
 *
 * <p>{@code &amp;a}, {@code &amp;7} und so weiter werden hier zu Adventure-Komponenten. Damit
 * entscheidet der Betreiber über Farbe und Wortlaut in derselben Zeile — und nicht über zwei
 * Dateien, von denen eine Code ist.
 */
public final class PaperHudRenderer implements HudRenderer {

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final PaperBossBar bossBar;
    private final PaperSidebar sidebar;
    private final Logger logger;

    public PaperHudRenderer(
            Server server,
            Scheduler scheduler,
            Messages messages,
            PaperBossBar bossBar,
            PaperSidebar sidebar,
            Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
        this.sidebar = Objects.requireNonNull(sidebar, "sidebar");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void show(
            UUID playerId, HudSurface surface, MessageKey key, Map<String, String> values) {
        inTick(
                playerId,
                "show " + surface,
                player -> {
                    switch (surface) {
                        case ACTION_BAR -> player.sendActionBar(render(key, values));
                        case BOSS_BAR ->
                                bossBar.show(playerId, BossBarOccasion.ZONE_NAME, key, values, 1.0);
                        case SIDEBAR ->
                                sidebar.show(
                                        playerId,
                                        key,
                                        List.of(new SidebarLines.Line(key, values)));
                    }
                });
    }

    @Override
    public void showLines(
            UUID playerId, HudSurface surface, MessageKey titleKey, List<SidebarLines.Line> lines) {
        if (surface != HudSurface.SIDEBAR) {
            // Mehrere Zeilen ergeben nur auf dem Scoreboard einen Sinn: eine Actionbar ist eine
            // Zeile, eine Bossbar auch. Still das Falsche zu tun waere schlechter als es zu sagen.
            logger.warning("[ui] showLines is only meaningful for the sidebar, not " + surface);
            return;
        }
        inTick(playerId, "show sidebar", player -> sidebar.show(playerId, titleKey, lines));
    }

    @Override
    public void clear(UUID playerId, HudSurface surface) {
        inTick(
                playerId,
                "clear " + surface,
                player -> {
                    switch (surface) {
                        // Eine leere Actionbar ist die einzige Art, sie zu raeumen - sie blendet
                        // von selbst aus, und ein leerer Text beschleunigt das nur.
                        case ACTION_BAR -> player.sendActionBar(Component.empty());
                        case BOSS_BAR -> bossBar.clear(playerId);
                        case SIDEBAR -> sidebar.clear(playerId);
                    }
                });
    }

    @Override
    public void bar(
            UUID playerId,
            BossBarOccasion occasion,
            MessageKey key,
            Map<String, String> values,
            double fraction) {
        inTick(
                playerId,
                "bar " + occasion,
                player -> bossBar.show(playerId, occasion, key, values, fraction));
    }

    @Override
    public void clearBar(UUID playerId, BossBarOccasion occasion) {
        inTick(playerId, "clear bar", player -> bossBar.clear(playerId));
    }

    /**
     * Räumt alles, was dieser Spieler hatte — beim Abmelden (FR-004c).
     *
     * <p>Ohne Sprung in den Tick: hier wird nur die Zuordnung geleert, nicht die Paper-API
     * angefasst. Der Spieler ist ohnehin weg.
     */
    public void forget(UUID playerId) {
        bossBar.forget(playerId);
        sidebar.forget(playerId);
    }

    /**
     * Führt {@code body} im Tick aus, für diesen Spieler.
     *
     * <p>Der eine Sprung, und die eine Stelle, an der ein Zeichenfehler abgefangen wird.
     */
    private void inTick(UUID playerId, String what, java.util.function.Consumer<Player> body) {
        scheduler.runSyncOnEntity(
                new EntityRef(playerId),
                () -> {
                    Player player = server.getPlayer(playerId);
                    if (player == null) {
                        // Gerade gegangen. Normaler Ausgang, keine Ausnahme (Zusage 1).
                        return;
                    }
                    try {
                        body.accept(player);
                    } catch (RuntimeException failure) {
                        // Eine Anzeige darf nie einen Tick kosten (Constitution VI).
                        logger.log(
                                Level.WARNING,
                                "[ui] could not " + what + " for " + playerId,
                                failure);
                    }
                });
    }

    private Component render(MessageKey key, Map<String, String> values) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(messages.get(key, values));
    }
}

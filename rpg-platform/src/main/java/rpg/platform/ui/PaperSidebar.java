package rpg.platform.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.ui.SidebarLines;

/**
 * Das Scoreboard als Sidebar: Level, Erfahrung, Coins, Zone (FR-005).
 *
 * <h2>Die Fläche, die bis B13 brachlag</h2>
 *
 * <p>Zusammen mit der Bossbar war sie eine von zwei unbenutzten Vanilla-Flächen, während die
 * Actionbar alles trug. Sie in Dienst zu nehmen ist der Grund, aus dem dieser Block existiert.
 *
 * <h2>Ein eigenes Scoreboard je Spieler</h2>
 *
 * <p>Nicht das Hauptscoreboard des Servers: das teilen sich alle, und vier Zeilen mit den Werten
 * <em>eines</em> Spielers wären dann für alle sichtbar. Jeder bekommt sein eigenes, und es geht mit
 * ihm.
 *
 * <h2>Warum die Zeilen als Teams und nicht als Score-Namen stehen</h2>
 *
 * <p>Ein Scoreboard-Eintrag ist ein Spielername und höchstens 32 Zeichen lang — eine Zeile wie
 * {@code XP: 340/1000} passt gerade so, {@code Zone: Ashen Reach Outskirts} nicht mehr. Vanilla löst
 * das seit jeher über Teams: der Eintrag ist ein kurzer, unsichtbarer Platzhalter, und der lesbare
 * Text steht im Präfix des Teams.
 *
 * <p><b>Das ist kein Trick, sondern die einzige Bauart, die lange Zeilen zulässt</b> — und der
 * Grund, aus dem hier mehr Code steht, als vier Zeilen vermuten lassen.
 *
 * <h2>Die Zahl links ist die Reihenfolge, nicht ein Wert</h2>
 *
 * <p>Minecraft sortiert absteigend nach Score. Die Zeilen bekommen deshalb {@code n, n-1, ...}, und
 * die Zahlen selbst werden ausgeblendet — sie bedeuten nichts, sie ordnen nur.
 */
public final class PaperSidebar {

    /** So heißt das Objective. Sichtbar wird davon nur der Anzeigename. */
    private static final String OBJECTIVE = "rpg-sidebar";

    private final Server server;
    private final Messages messages;

    /** Welches Scoreboard zu wem gehört. Geht beim Abmelden mit dem Spieler. */
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public PaperSidebar(Server server, Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Setzt die Sidebar dieses Spielers auf diese Zeilen.
     *
     * <p><b>Nur im Tick aufrufen</b> (Constitution I.1). Der Sprung dorthin passiert in
     * {@link PaperHudRenderer}.
     */
    public void show(UUID playerId, MessageKey titleKey, List<SidebarLines.Line> lines) {
        Player player = server.getPlayer(playerId);
        if (player == null) {
            return;
        }
        Scoreboard board = boards.computeIfAbsent(playerId, id -> newBoard());
        if (board == null) {
            return;
        }

        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, "dummy", render(titleKey, Map.of()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(render(titleKey, Map.of()));
        }

        // Das Ganze neu setzen und nicht zeilenweise nachfuehren: welche Zeile sich geaendert hat,
        // weiss HudRefresh bereits, und es sendet nur, wenn ueberhaupt etwas anders ist (FR-013).
        // Ein zweiter Vergleich hier waere derselbe Vergleich ein zweites Mal.
        clearEntries(board);

        int score = lines.size();
        for (SidebarLines.Line line : lines) {
            setLine(board, objective, score, score, render(line.key(), line.values()));
            score--;
        }

        player.setScoreboard(board);
    }

    /** Nimmt die Sidebar weg — geräumt, nicht leer beschrieben. */
    public void clear(UUID playerId) {
        boards.remove(playerId);
        Player player = server.getPlayer(playerId);
        if (player == null) {
            return;
        }
        Scoreboard main = mainBoard();
        if (main != null) {
            player.setScoreboard(main);
        }
    }

    /** Räumt beim Abmelden — Board und Eintrag, damit nichts zurückbleibt. */
    public void forget(UUID playerId) {
        boards.remove(playerId);
    }

    /** Wie viele Spieler gerade ein Board haben — für den Test gegen ein Leck. */
    int tracked() {
        return boards.size();
    }

    /**
     * Eine Zeile: ein unsichtbarer Platzhalter als Eintrag, der lesbare Text im Team-Präfix.
     *
     * <p>Der Platzhalter muss je Zeile verschieden und stabil sein — {@code §0}, {@code §1}, … sind
     * Farbcodes, die nichts anzeigen und die Vanilla als verschiedene Einträge zählt.
     */
    private void setLine(
            Scoreboard board, Objective objective, int slot, int score, Component text) {
        String entry = "§" + Integer.toHexString(slot % 16);
        String teamName = "rpg-line-" + slot;

        org.bukkit.scoreboard.Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        team.prefix(text);
        objective.getScore(entry).setScore(score);
    }

    /** Nimmt die Einträge des letzten Durchlaufs weg, bevor die neuen kommen. */
    private void clearEntries(Scoreboard board) {
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
    }

    /**
     * Ein frisches Scoreboard.
     *
     * <p>Gibt {@code null} zurück, wenn der Server keinen Manager hat. Das ist kein Fehlerfall im
     * Betrieb, aber einer im Test: MockBukkit meldet Nicht-Implementiertes, und ein Wurf hier
     * risse den ganzen Durchlauf mit, statt nur diese Fläche auszulassen.
     */
    private Scoreboard newBoard() {
        return server.getScoreboardManager() == null
                ? null
                : server.getScoreboardManager().getNewScoreboard();
    }

    private Scoreboard mainBoard() {
        return server.getScoreboardManager() == null
                ? null
                : server.getScoreboardManager().getMainScoreboard();
    }

    private Component render(MessageKey key, Map<String, String> values) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(messages.get(key, values));
    }
}

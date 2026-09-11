package rpg.platform.ui;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.ui.BossBarOccasion;

/**
 * <b>Eine</b> Bossbar je Spieler (FR-004b).
 *
 * <h2>Wiederverwendet, nicht gestapelt</h2>
 *
 * <p>Adventure erlaubt beliebig viele Leisten je Spieler, und sie stapeln sich am oberen Bildrand.
 * Bei drei Anlässen wären das drei Balken übereinander, von denen zwei veraltet sind. Diese Klasse
 * hält deshalb <b>genau eine</b> {@link BossBar} je Spieler und ändert ihren Text und Füllstand,
 * statt eine neue zu zeigen.
 *
 * <p><b>Das ist auch billiger.</b> Eine bestehende Leiste umzuschreiben ist ein Paket; sie zu
 * verstecken und eine neue zu zeigen sind zwei — je Sekunde und je Spieler.
 *
 * <h2>Sie hängt am Spieler, nicht global</h2>
 *
 * <p>Constitution I.6: kein globaler veränderlicher Zustand im Gameplay-Pfad. Die Zuordnung ist
 * eine {@link ConcurrentHashMap} je Spieler, und sie wird beim Abmelden geräumt (FR-004c) — beides,
 * die Leiste <em>und</em> ihr Eintrag. Eine entfernte Leiste, deren Eintrag stehen bleibt, ist ein
 * Leck, das erst nach Stunden auffällt.
 *
 * <h2>Die Farbe folgt dem Anlass</h2>
 *
 * <p>Drei Anlässe, drei Farben — der Spieler soll ohne zu lesen wissen, warum die Leiste da ist. Sie
 * steht im Code und nicht in der Konfiguration, weil sie zur Bedeutung gehört und nicht zum
 * Geschmack: eine rote Kanalisierung und ein blauer Boss wären nicht falsch konfiguriert, sondern
 * unverständlich.
 */
public final class PaperBossBar {

    private final Server server;
    private final Messages messages;

    /** Die eine Leiste je Spieler. Nie zwei, nie eine ohne Eintrag. */
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public PaperBossBar(Server server, Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Setzt die Leiste dieses Spielers auf diesen Anlass.
     *
     * <p><b>Nur im Tick aufrufen</b> (Constitution I.1). Der HUD-Takt läuft asynchron; der Sprung in
     * den Tick passiert in {@link PaperHudRenderer}, nicht hier.
     */
    public void show(
            UUID playerId,
            BossBarOccasion occasion,
            MessageKey key,
            Map<String, String> values,
            double fraction) {
        Player player = server.getPlayer(playerId);
        if (player == null) {
            // Ein Spieler, der gerade gegangen ist, ist ein normaler Ausgang und keine Ausnahme
            // (HudRenderer, Zusage 1).
            return;
        }
        Component text =
                LegacyComponentSerializer.legacyAmpersand().deserialize(messages.get(key, values));
        float progress = (float) Math.clamp(fraction, 0.0, 1.0);

        BossBar bar = bars.get(playerId);
        if (bar == null) {
            bar = BossBar.bossBar(text, progress, colourOf(occasion), BossBar.Overlay.PROGRESS);
            bars.put(playerId, bar);
            player.showBossBar(bar);
            return;
        }
        // Wiederverwenden statt neu zeigen: sonst stapeln sie sich, und der Spieler sieht drei
        // Balken, von denen zwei veraltet sind.
        bar.name(text);
        bar.progress(progress);
        bar.color(colourOf(occasion));
    }

    /**
     * Nimmt die Leiste dieses Spielers weg.
     *
     * <p><b>Nicht dasselbe wie {@code show(..., 0.0)}</b> (Vertrag {@code HudRenderer}, Zusage 3):
     * ein leerer Balken steht noch da, eine geräumte Leiste ist weg.
     */
    public void clear(UUID playerId) {
        BossBar bar = bars.remove(playerId);
        if (bar == null) {
            return;
        }
        Player player = server.getPlayer(playerId);
        if (player != null) {
            player.hideBossBar(bar);
        }
    }

    /**
     * Räumt beim Abmelden (FR-004c).
     *
     * <p>Beides: die Leiste <b>und</b> ihren Eintrag. Beim nächsten Anmelden steht dann keine alte
     * Leiste — weder ein Zonenname von gestern noch ein Bosskampf, der längst entschieden ist.
     */
    public void forget(UUID playerId) {
        clear(playerId);
    }

    /** Wie viele Spieler gerade eine Leiste haben — für den Test gegen ein Leck. */
    int tracked() {
        return bars.size();
    }

    /**
     * Die Farbe zum Anlass.
     *
     * <p>Im Code und nicht in der Konfiguration: sie gehört zur Bedeutung und nicht zum Geschmack.
     */
    private static BossBar.Color colourOf(BossBarOccasion occasion) {
        return switch (occasion) {
            case CHANNELLING -> BossBar.Color.BLUE;
            case BOSS_FIGHT -> BossBar.Color.RED;
            case ZONE_NAME -> BossBar.Color.WHITE;
        };
    }
}

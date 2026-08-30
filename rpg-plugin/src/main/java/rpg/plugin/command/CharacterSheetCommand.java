package rpg.plugin.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import rpg.core.message.Messages;
import rpg.core.ui.CharacterSheet;
import rpg.core.ui.CharacterSheets;
import rpg.core.ui.UiMessageKeys;
import rpg.platform.ui.CharacterSheetListener;
import rpg.platform.ui.CharacterSheetMenu;

/**
 * {@code /char} — öffnet die Übersicht des <b>aktiven</b> Charakters.
 *
 * <h2>Vorläufig, und ausdrücklich so gemeint</h2>
 *
 * <p>Rechtebaum, Tab-Completion und die einheitliche Kommandostruktur bleiben <b>B14</b> (FR-058).
 * Dieser Block legt genau <em>einen</em> Eintrag an — den, den sein eigenes Fenster braucht — und
 * sammelt keine fremden ein: {@code /coins} bleibt, wo es ist (FR-061a).
 *
 * <p>Die Präzedenz ist ADR-028 ({@code /coins}) und B12 ({@code /stats}, {@code /top}), und die
 * Begründung ist dieselbe: <b>ein Fenster ohne Aufrufweg ist für den Spieler nicht vorhanden</b>.
 * Ein eigener ADR nach demselben Muster gehört dazu (FR-024a-Nachbarschaft, T143).
 *
 * <h2>Kein Argument</h2>
 *
 * <p>Es gibt nichts zu wählen: die Übersicht zeigt den aktiven Charakter, und eine Summe über
 * mehrere bildet sie nicht (FR-053). Ein Argument wäre die Einladung, genau das zu erwarten.
 *
 * <h2>Wie wenig hier steht, ist das Maß dieser Klasse</h2>
 *
 * <p>Sie prüft, ob ein Spieler tippt, holt die Übersicht und öffnet sie. Jede Regel — welche
 * Attribute es gibt, was ein Zustand ist, wann der Stand veraltet — liegt in {@code rpg-core} und
 * ist ohne Server geprüft. Was hier eine Regel bekommt, ist im falschen Modul.
 */
public final class CharacterSheetCommand implements CommandExecutor, TabCompleter {

    /**
     * Das Recht.
     *
     * <p>{@code default: true} in {@code plugin.yml}: die eigene Übersicht anzusehen ist nichts,
     * wofür ein Betreiber erst etwas freischalten sollte.
     */
    public static final String PERMISSION = "rpg.ui.character";

    private final Function<UUID, Optional<UUID>> characterOfPlayer;
    private final CharacterSheets sheets;
    private final CharacterSheetMenu menu;
    private final CharacterSheetListener listener;
    private final Messages messages;

    public CharacterSheetCommand(
            Function<UUID, Optional<UUID>> characterOfPlayer,
            CharacterSheets sheets,
            CharacterSheetMenu menu,
            CharacterSheetListener listener,
            Messages messages) {
        this.characterOfPlayer = Objects.requireNonNull(characterOfPlayer, "characterOfPlayer");
        this.sheets = Objects.requireNonNull(sheets, "sheets");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // Von der Konsole ergibt es keinen Sinn: ein Inventar braucht jemanden, dem man es
            // zeigen kann.
            sender.sendMessage(messages.get(UiMessageKeys.SHEET_NO_CHARACTER, Map.of()));
            return true;
        }
        Optional<UUID> characterId = characterOfPlayer.apply(player.getUniqueId());
        if (characterId.isEmpty()) {
            player.sendMessage(messages.get(UiMessageKeys.SHEET_NO_CHARACTER, Map.of()));
            return true;
        }
        Optional<CharacterSheet> sheet = sheets.of(characterId.get());
        if (sheet.isEmpty()) {
            // Charakter gewaehlt, aber B04 oder B07 fuehren ihn noch nicht - ein Zwischenzustand
            // und kein Fehler. Dieselbe Antwort wie ohne Charakter: Nullen, die wie echte Werte
            // aussehen, waeren schlimmer.
            player.sendMessage(messages.get(UiMessageKeys.SHEET_NO_CHARACTER, Map.of()));
            return true;
        }
        player.openInventory(menu.open(player.getUniqueId(), sheet.get()));
        listener.opened(player.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        // Leer und nicht null: null laesst Bukkit auf die Spielernamen zurueckfallen, und die
        // waeren hier eine Vervollstaendigung fuer ein Argument, das es nicht gibt.
        return List.of();
    }
}

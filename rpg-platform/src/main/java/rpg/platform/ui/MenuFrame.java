package rpg.platform.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Der gemeinsame Rahmen für die <b>drei</b> Fenster, die B13 in der Hand hat.
 *
 * <h2>Welche drei — und welche zwei ausdrücklich nicht</h2>
 *
 * <p>{@code CharacterSheetMenu} (neu), {@code WaypointMenu} und {@code CurrencyMenu} (übernommen
 * nach ADR-032 und ADR-028). <b>Nicht</b> {@code ClassSelectionMenu} aus B07 und <b>nicht</b> B12s
 * {@code StatisticsMenu}/{@code LeaderboardMenu} (FR-070, FR-071).
 *
 * <p>Der Grund steht in research.md R10: die vorhandenen Fenster teilen sich <b>nichts</b> — es sind
 * drei unabhängige {@code final class}. Ihnen nachträglich einen gemeinsamen Rahmen aufzuzwingen
 * wäre ein Umbau an laufendem, abgenommenem Code für keinen sichtbaren Unterschied. Genau dieselbe
 * Begründung, mit der die Skill-Leiste unangetastet bleibt.
 *
 * <h2>Er kennt keine Regeln</h2>
 *
 * <p>Titel, Größe, Rahmenplätze, Schließen — mehr nicht. Was in einem Fenster steht, entscheidet
 * das Fenster; was ein Klick tut, sein Listener. Ein Rahmen, der beides mitentschiede, wäre die
 * Stelle, an der drei Fenster anfangen, sich gegenseitig zu beschränken.
 *
 * <h2>Der Rahmen ist ein Gegenstand ohne Namen</h2>
 *
 * <p>Graue Glasscheiben mit leerem Anzeigenamen. Ein Name dort wäre ein Spielertext, der nichts
 * sagt, und der Wächter aus FR-015 fände ihn zu Recht.
 */
public final class MenuFrame {

    /** Womit der Rand gefüllt wird. Vanilla-Material, kein Resource Pack (ADR-005, FR-056). */
    private static final Material BORDER = Material.GRAY_STAINED_GLASS_PANE;

    private final Messages messages;

    public MenuFrame(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Ein leeres Fenster mit Titel und Rand.
     *
     * @param rows Zeilen zu neun Plätzen; erlaubt sind eins bis sechs
     */
    public Inventory build(MessageKey titleKey, Map<String, String> titleValues, int rows) {
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(titleValues, "titleValues");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException(
                    "rows ist " + rows + " - ein Vanilla-Fenster hat eins bis sechs");
        }
        Inventory inventory =
                Bukkit.createInventory(null, rows * 9, render(titleKey, titleValues));
        fillBorder(inventory, rows);
        return inventory;
    }

    /**
     * Ein leeres Fenster mit Titel und <b>ohne</b> Rand.
     *
     * <h2>Warum es diese zweite Form gibt</h2>
     *
     * <p>Die zwei übernommenen Fenster ({@code WaypointMenu}, {@code CurrencyMenu}) hatten vor dem
     * Umzug keinen Rand. Ihnen einen zu geben wäre eine <b>sichtbare</b> Änderung — und FR-060 und
     * FR-061 sagen beide: „Verhalten für den Spieler unverändert."
     *
     * <p>T122 verlangt, sie auf {@link MenuFrame} umzustellen; die Begründung dort ist Einheitlichkeit
     * („derselbe Rahmen wie die Charakterübersicht"). <b>Eine Anforderung schlägt eine Aufgabe</b>:
     * sie teilen sich jetzt die Titel- und Textbehandlung, aber nicht das Aussehen. Was sie gewinnen,
     * ist die eine Stelle für Farbcodes und Kursivschrift; was sie behalten, ist ihr Anblick.
     *
     * <p>Der Umzug war ausdrücklich ein Umzug und keine Überarbeitung. Ein Rand, den vorher niemand
     * gesehen hat, wäre genau die stille Änderung, die einen Spieler denken lässt, es sei etwas
     * kaputt.
     */
    public Inventory buildPlain(MessageKey titleKey, Map<String, String> titleValues, int rows) {
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(titleValues, "titleValues");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException(
                    "rows ist " + rows + " - ein Vanilla-Fenster hat eins bis sechs");
        }
        return Bukkit.createInventory(null, rows * 9, render(titleKey, titleValues));
    }

    /**
     * Legt einen beschrifteten Gegenstand auf einen Platz.
     *
     * <p><b>Kursiv wird ausgeschaltet.</b> Vanilla schreibt jeden gesetzten Anzeigenamen kursiv, und
     * das liest sich wie ein verzauberter Gegenstand statt wie eine Zeile. Dieselbe Korrektur, die
     * {@code CurrencyMenu} und B12s Fenster an derselben Stelle machen.
     */
    public void put(
            Inventory inventory,
            int slot,
            Material material,
            MessageKey key,
            Map<String, String> values,
            List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(render(key, values).decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) {
                meta.lore(
                        lore.stream()
                                .map(line -> line.decoration(TextDecoration.ITALIC, false))
                                .toList());
            }
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    /** Ein Text aus der Sprachdatei, mit Farbcodes. */
    public Component render(MessageKey key, Map<String, String> values) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(messages.get(key, values));
    }

    /**
     * Ob dieser Platz zum Rand gehört.
     *
     * <p>Ein Listener fragt das, bevor er einen Klick auswertet — ein Klick auf den Rand ist keine
     * Auswahl, sondern ein Fehlgriff, und still nichts zu tun ist dort das Richtige.
     */
    public static boolean isBorder(int slot, int rows) {
        int columns = 9;
        int row = slot / columns;
        int column = slot % columns;
        return row == 0 || row == rows - 1 || column == 0 || column == columns - 1;
    }

    private void fillBorder(Inventory inventory, int rows) {
        ItemStack pane = new ItemStack(BORDER);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            // Leerer Name statt gar keinem: ohne ihn zeigt Vanilla "Gray Stained Glass Pane" an,
            // und das ist ein Spielertext, den niemand gewaehlt hat.
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < rows * 9; slot++) {
            if (isBorder(slot, rows)) {
                inventory.setItem(slot, pane);
            }
        }
    }
}

package rpg.platform.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.MetricVisibility;
import rpg.core.statistics.ProfileSnapshot;
import rpg.core.statistics.StatisticsMessageKeys;
import rpg.platform.item.ItemText;

/**
 * Das Profilfenster (FR-041, FR-042) — <b>das eigene, und ein fremdes ohne die drei privaten
 * Werte.</b>
 *
 * <h2>Ein Fenster für beide Fälle</h2>
 *
 * <p>Zwei Fenster wären die naheliegende Aufteilung und hätten bedeutet, dass zwei Klassen
 * dieselbe Regel durchsetzen müssen — die zweite falsch. Hier entscheidet der
 * {@link ProfileSnapshot}: er <em>trägt</em> die privaten Werte nur, wenn der Betrachter das Konto
 * selbst ist. Dieses Fenster kann sie also gar nicht falsch anzeigen; es hat sie schlicht nicht.
 *
 * <p>Die Zeile {@link StatisticsMessageKeys#PROFILE_PRIVATE_OMITTED} steht im fremden Profil
 * trotzdem da: eine Lücke ohne Erklärung liest sich wie ein fehlender Wert, und der nächste
 * Spieler fragt, warum bei ihm etwas fehlt.
 */
public final class StatisticsMenu {

    private static final int SIZE = 45;

    /** Die öffentlichen Ranglisten stehen in der ersten Reihe. */
    private static final int FIRST_PUBLIC_SLOT = 0;

    /** Die drei privaten Werte in der dritten. */
    private static final int SLOT_DEATHS = 18;

    private static final int SLOT_PLAYTIME_ZONES = 19;

    private static final int SLOT_ONLINE = 20;

    /** Der Hinweis im fremden Profil. */
    public static final int SLOT_PRIVATE_NOTE = 18;

    private final Messages messages;

    public StatisticsMenu(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Baut das Fenster aus einem fertig geladenen Schnappschuss. */
    public Inventory build(ProfileSnapshot profile, String ownerName) {
        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SIZE,
                        profile.ownProfile()
                                ? ItemText.of(messages, StatisticsMessageKeys.PROFILE_TITLE, Map.of())
                                : ItemText.of(
                                        messages,
                                        StatisticsMessageKeys.PROFILE_TITLE_OTHER,
                                        Map.of("player", ownerName)));

        int slot = FIRST_PUBLIC_SLOT;
        for (Aggregation board : Aggregation.values()) {
            if (board.visibility() != MetricVisibility.PUBLIC) {
                continue;
            }
            inventory.setItem(slot++, publicLine(profile, board));
        }

        if (profile.ownProfile()) {
            addPrivateLines(inventory, profile);
        } else {
            inventory.setItem(
                    SLOT_PRIVATE_NOTE,
                    line(
                            Material.BARRIER,
                            StatisticsMessageKeys.PROFILE_PRIVATE_OMITTED,
                            Map.of(),
                            List.of()));
        }
        return inventory;
    }

    /** Eine öffentliche Zeile: Wert und die eigene Platzierung. */
    private ItemStack publicLine(ProfileSnapshot profile, Aggregation board) {
        List<Component> lore = new ArrayList<>();
        OptionalInt rank = profile.rankOf(board);
        if (rank.isPresent()) {
            lore.add(
                    ItemText.onItem(
                            ItemText.of(
                                    messages,
                                    StatisticsMessageKeys.LEADERBOARD_YOUR_RANK,
                                    Map.of(
                                            "rank", String.valueOf(rank.getAsInt()),
                                            "value", String.valueOf(profile.valueOf(board))))));
        } else {
            lore.add(
                    ItemText.onItem(
                            ItemText.of(
                                    messages, StatisticsMessageKeys.LEADERBOARD_UNRANKED, Map.of())));
        }
        lore.add(ItemText.onItem(ItemText.of(messages, StatisticsMessageKeys.boardHint(board), Map.of())));

        return line(
                Material.PAPER,
                StatisticsMessageKeys.PROFILE_LINE,
                Map.of(
                        "label", messages.get(StatisticsMessageKeys.boardName(board)),
                        "value", String.valueOf(profile.valueOf(board))),
                lore);
    }

    /**
     * Die drei privaten Werte (FR-038).
     *
     * <p>Sie stehen <b>nur</b> hier: die Aufschlüsselung der Tode nach Verursacher, die Aufteilung
     * der Spielzeit nach Zonen und die gesamte Onlinezeit. Wer sein eigenes Profil öffnet, ist der
     * Einzige, der sie je zu sehen bekommt (ADR-043).
     */
    private void addPrivateLines(Inventory inventory, ProfileSnapshot profile) {
        inventory.setItem(
                SLOT_DEATHS,
                line(
                        Material.SKELETON_SKULL,
                        StatisticsMessageKeys.PROFILE_LINE,
                        Map.of(
                                "label",
                                messages.get(StatisticsMessageKeys.boardName(Aggregation.DEATHS)),
                                "value",
                                String.valueOf(total(profile.deathsByCause()))),
                        breakdownLore(profile.deathsByCause())));

        inventory.setItem(
                SLOT_PLAYTIME_ZONES,
                line(
                        Material.MAP,
                        StatisticsMessageKeys.PROFILE_LINE,
                        Map.of(
                                "label",
                                messages.get(
                                        StatisticsMessageKeys.boardName(Aggregation.PLAYTIME_ACTIVE)),
                                "value",
                                String.valueOf(total(profile.playtimeByZone()))),
                        breakdownLore(profile.playtimeByZone())));

        inventory.setItem(
                SLOT_ONLINE,
                line(
                        Material.CLOCK,
                        StatisticsMessageKeys.PROFILE_LINE,
                        Map.of(
                                "label",
                                messages.get(
                                        StatisticsMessageKeys.boardName(Aggregation.PLAYTIME_ONLINE)),
                                "value",
                                String.valueOf(Math.max(0L, profile.onlineSeconds()))),
                        List.of(
                                ItemText.onItem(
                                        ItemText.of(
                                                messages,
                                                StatisticsMessageKeys.boardHint(
                                                        Aggregation.PLAYTIME_ONLINE),
                                                Map.of())))));
    }

    /** Die Einzelposten als Lore — bei einem leeren Tag bleibt sie leer, und das ist richtig. */
    private List<Component> breakdownLore(Map<String, Long> byDimension) {
        List<Component> lore = new ArrayList<>();
        byDimension.forEach(
                (dimension, value) ->
                        lore.add(
                                ItemText.onItem(
                                        ItemText.of(
                                                messages,
                                                StatisticsMessageKeys.PROFILE_LINE,
                                                Map.of(
                                                        "label", dimension,
                                                        "value", String.valueOf(value))))));
        return lore;
    }

    private ItemStack line(
            Material material, MessageKey key, Map<String, String> placeholders, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemText.onItem(ItemText.of(messages, key, placeholders)));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static long total(Map<String, Long> byDimension) {
        return byDimension.values().stream().mapToLong(Long::longValue).sum();
    }
}

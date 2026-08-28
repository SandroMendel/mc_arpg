package rpg.platform.item;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import rpg.core.classes.LadderSlot;
import rpg.core.event.EventBus;
import rpg.core.item.GearConditionChangedEvent;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.WearCurve;
import rpg.core.message.Messages;
import rpg.platform.classes.BoundItemTag;

/**
 * Was der Spieler vom Verschleiß sieht (FR-050, FR-051).
 *
 * <p><b>Der Haltbarkeitsbalken, und er ist eine Anzeige.</b> Das Item bleibt unzerstörbar — B07
 * setzt das Flag, und dieser Block hebt es nicht auf (FR-038). Vanillas Balken wird hier nur
 * <em>beschriftet</em>: er ist das eine Anzeigemittel, das jeder Minecraft-Spieler ohne Erklärung
 * liest, und es kostet keinen Platz im Bild.
 *
 * <p><b>Abgeleitet bei jedem Zeichnen, nie gespeichert</b> — dieselbe Regel wie für Name und Lore
 * (FR-002). Der Zustand steht in der Datenbank, der Balken folgt daraus. Andersherum wäre der Balken
 * eine zweite Wahrheit, und nach dem ersten Reload eine falsche.
 *
 * <p><b>Vorläufig bis B13</b> (ADR-028). Wenn die Anzeige einen eigenen Block bekommt, zieht das
 * hier um; der Message-Schlüssel bleibt derselbe.
 */
public final class GearConditionDisplay {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /**
     * Welchen Bindungsvermerk B07 für diesen Charakter und diese Leiter erwartet.
     *
     * <p><b>Gefragt, nicht selbst zusammengesetzt</b> (FR-079). Der Vermerk hat ein Format, und es
     * gehört B07; ihn hier zu zerlegen, um zu erkennen, welches Stück zu welcher Leiter gehört, wäre
     * eine zweite Lesart desselben Strings — und die beiden liefen beim nächsten Umbau auseinander.
     */
    @FunctionalInterface
    public interface ExpectedTag {
        Optional<String> of(UUID characterId, LadderSlot slot);
    }

    private final Messages messages;
    private final Function<UUID, Optional<Player>> onlinePlayerOf;
    private final ExpectedTag expectedTag;

    public GearConditionDisplay(
            Messages messages,
            Function<UUID, Optional<Player>> onlinePlayerOf,
            ExpectedTag expectedTag) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayerOf = Objects.requireNonNull(onlinePlayerOf, "onlinePlayerOf");
        this.expectedTag = Objects.requireNonNull(expectedTag, "expectedTag");
    }

    /** Hört auf den Zustand und sagt Bescheid, wenn es eng wird. */
    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events")
                .subscribe(GearConditionChangedEvent.class, this::onChanged);
    }

    void onChanged(GearConditionChangedEvent event) {
        Optional<Player> player = onlinePlayerOf.apply(event.characterId());
        if (player.isEmpty()) {
            return;
        }
        repaint(player.get(), event.characterId(), event.slot(), event.after());

        // Die Entscheidung "jetzt sagen" ist schon gefallen - sie steckt im Ereignis (FR-051).
        // Sie hier ein zweites Mal zu treffen waere die zuverlaessigste Art, zwei Meldungen fuer
        // einen Treffer zu erzeugen.
        if (event.crossedWarning().isEmpty() || !messages.contains(ItemMessageKeys.WEAR_WARNING)) {
            return;
        }
        player.get()
                .sendMessage(
                        LEGACY.deserialize(
                                messages.get(
                                        ItemMessageKeys.WEAR_WARNING,
                                        Map.of(
                                                "condition",
                                                format(event.after()),
                                                "slot",
                                                event.slot().configKey()))));
    }

    /**
     * Zeichnet den Balken auf jedem getragenen Stück dieser Leiter neu.
     *
     * <p><b>Welches Stück zu welcher Leiter gehört, sagt B07</b> — über den erwarteten Vermerk. Vier
     * Rüstungsteile teilen sich einen Vermerk und einen Zustand (FR-039), also bekommen alle vier
     * denselben Balken, und das ist richtig: sie sind <em>eine</em> Stufe.
     */
    void repaint(Player player, UUID characterId, LadderSlot slot, double condition) {
        Optional<String> tag = expectedTag.of(characterId, slot);
        if (tag.isEmpty()) {
            return;
        }
        for (ItemStack worn : player.getInventory().getContents()) {
            if (worn == null) {
                continue;
            }
            if (BoundItemTag.tagOf(worn).filter(tag.get()::equals).isPresent()) {
                paint(worn, condition);
            }
        }
    }

    /**
     * Schreibt den Zustand auf den Haltbarkeitsbalken eines Ausrüstungsstücks.
     *
     * <p>Tut nichts für einen Gegenstand ohne Balken und nichts für einen, dessen Material keinen
     * kennt. Und es macht ihn nicht zerstörbar: das Unzerstörbar-Flag aus B07 bleibt, wo es ist.
     */
    public void paint(ItemStack stack, double condition) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int max = stack.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        double clamped = Math.min(Math.max(condition, 0.0), WearCurve.FULL);
        // Vanillas Zaehlung laeuft andersherum: 0 heisst neu.
        damageable.setDamage((int) Math.round(max * (1.0 - clamped / WearCurve.FULL)));
        stack.setItemMeta(meta);
    }

    /** Welchen Zustand dieser Slot gerade hat, als ganze Prozent für die Meldung. */
    static String format(double condition) {
        return String.valueOf((int) Math.floor(condition));
    }
}

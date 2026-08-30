package rpg.platform.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import rpg.core.classes.LadderSlot;
import rpg.core.event.EventBus;
import rpg.core.item.GearConditionChangedEvent;
import rpg.core.item.GearConditions;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.WearCurve;
import rpg.core.message.Messages;
import rpg.platform.classes.BoundItemTag;

/**
 * Was der Spieler vom Verschleiß sieht (FR-050, FR-051).
 *
 * <p><b>Zwei Anzeigen, und beide sind nur Anzeigen.</b> Der Haltbarkeitsbalken zeigt, <em>dass</em>
 * etwas abgenutzt ist; die Lore-Zeile zeigt, wie viel — und dass die Zahl etwas bedeutet, nämlich
 * den Anteil der Werte, der noch ankommt. Das Item bleibt unzerstörbar: B07 setzt das Flag, und
 * dieser Block hebt es nicht auf (FR-038).
 *
 * <p><b>Abgeleitet bei jedem Zeichnen, nie gespeichert</b> — dieselbe Regel wie für Name und Lore
 * (FR-002). Der Zustand steht in der Datenbank, Balken und Zeile folgen daraus. Andersherum wären
 * sie eine zweite Wahrheit, und nach dem ersten Reload eine falsche.
 *
 * <p><b>Zwei Anlässe zum Zeichnen, und beide werden gebraucht.</b> Beim Anlegen der Ausrüstung
 * ({@link #refresh}) und bei jeder Änderung des Zustands ({@link #subscribeTo}). Nur das zweite
 * hieße: wer sich einloggt, sähe seine Rüstung bis zum ersten Treffer als unbeschädigt — und das ist
 * genau der Moment, in dem er entscheidet, ob er zum Händler geht.
 *
 * <p><b>B13 ist da — und diese Klasse ist geblieben.</b> Der Vermerk hier lautete „Vorläufig bis
 * B13" (ADR-028); umgezogen ist nach ADR-028 die <em>Anzeige</em>, und das war das Kontofenster.
 * Der Zustandsbalken auf einem Gegenstand ist keines: er gehört zu dem, was ein Gegenstand
 * <em>ist</em>, und das entscheidet B11.
 *
 * <p><b>B13 benutzt sie stattdessen.</b> {@code PaperItemRenderer} reicht sowohl für Vorlagen als
 * auch für getragene Ausrüstung an {@link #paint} durch, statt die Zeile ein zweites Mal zu bauen
 * (FR-021a) — zwei Renderer für denselben Gegenstand driften auseinander, und der Fehler zeigt sich
 * zuerst dem Spieler.
 */
public final class GearConditionDisplay {

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
    private final GearConditions conditions;
    private final Function<UUID, Optional<Player>> onlinePlayerOf;
    private final ExpectedTag expectedTag;

    public GearConditionDisplay(
            Messages messages,
            GearConditions conditions,
            Function<UUID, Optional<Player>> onlinePlayerOf,
            ExpectedTag expectedTag) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.onlinePlayerOf = Objects.requireNonNull(onlinePlayerOf, "onlinePlayerOf");
        this.expectedTag = Objects.requireNonNull(expectedTag, "expectedTag");
    }

    /** Hört auf den Zustand und zeichnet neu, wenn er sich ändert. */
    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events")
                .subscribe(GearConditionChangedEvent.class, this::onChanged);
    }

    /**
     * Zeichnet beide Leitern neu — nach dem Anlegen der Ausrüstung.
     *
     * <p>Aufgerufen an denselben zwei Stellen, an denen {@code ClassEquipmentApplier.apply} läuft:
     * beim Eintritt und nach einem Stufenaufstieg. Beide Male sind die Stücke gerade frisch gebaut
     * und wissen nichts von einem Zustand.
     */
    public void refresh(Player player, UUID characterId) {
        for (LadderSlot slot : LadderSlot.values()) {
            repaint(player, characterId, slot, conditions.conditionOf(characterId, slot));
        }
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
        if (event.crossedWarning().isEmpty()) {
            return;
        }
        Component warning =
                ItemText.of(
                        messages,
                        ItemMessageKeys.WEAR_WARNING,
                        Map.of(
                                "condition", percent(event.after()),
                                "slot", event.slot().configKey()));
        if (warning != null) {
            player.get().sendMessage(warning);
        }
    }

    /**
     * Zeichnet Balken und Zeile auf jedem getragenen Stück dieser Leiter.
     *
     * <p><b>Welches Stück zu welcher Leiter gehört, sagt B07</b> — über den erwarteten Vermerk. Vier
     * Rüstungsteile teilen sich einen Vermerk und einen Zustand (FR-039), also bekommen alle vier
     * dieselbe Anzeige, und das ist richtig: sie sind <em>eine</em> Stufe.
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
     * Schreibt den Zustand auf Balken und Lore eines Ausrüstungsstücks.
     *
     * <p>Die Zeile wird an einer festen Stelle geführt — als <b>letzte</b> — und beim nächsten Mal
     * ersetzt statt angehängt. Ohne das wüchse die Lore mit jedem Treffer, und nach einem Kampf
     * stünden fünfzig Zeilen darunter.
     *
     * <p>Und es macht das Stück nicht zerstörbar: das Unzerstörbar-Flag aus B07 bleibt, wo es ist.
     */
    public void paint(ItemStack stack, double condition) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        double clamped = Math.min(Math.max(condition, 0.0), WearCurve.FULL);

        if (meta instanceof Damageable damageable) {
            int max = stack.getType().getMaxDurability();
            if (max > 0) {
                // Vanillas Zaehlung laeuft andersherum: 0 heisst neu.
                damageable.setDamage((int) Math.round(max * (1.0 - clamped / WearCurve.FULL)));
            }
        }

        Component line =
                ItemText.of(
                        messages,
                        ItemMessageKeys.GEAR_CONDITION_LORE,
                        Map.of(
                                "condition", percent(clamped),
                                "factor", percent(factorPercent(clamped))));
        if (line != null) {
            meta.lore(withConditionLine(meta.lore(), ItemText.onItem(line)));
        }
        stack.setItemMeta(meta);
    }

    /**
     * Wie viel Prozent des Stufenbeitrags bei diesem Zustand ankommen.
     *
     * <p>Das ist die Zahl, die den Zustand <em>erklärt</em>. „68 % Haltbarkeit" allein sagt einem
     * Spieler nichts darüber, was es ihn kostet; „Werte bei 87 %" schon. Und bei null bleiben es
     * zwanzig — die Ausrüstung wird schwächer, sie fällt nicht aus (FR-038).
     *
     * <p><b>Gefragt und nicht nachgerechnet.</b> Es gibt genau eine Verschleißkurve, und sie gehört
     * {@code rpg-core}. Eine zweite hier wäre nach dem ersten Balancing eine falsche Anzeige zu
     * richtigen Werten — der unangenehmste Fehler, weil ihn niemand dem Anzeigecode zuordnet.
     */
    private double factorPercent(double condition) {
        return WearCurve.FULL * conditions.factorForCondition(condition);
    }

    /**
     * Die vorhandene Lore plus unsere Zeile — und ohne die vorige.
     *
     * <p>Ein Stück wird bei jedem Eintritt neu gebaut, aber zwischen zwei Treffern nicht; ohne das
     * Ersetzen wüchse die Lore mit jedem Schlag.
     */
    private static List<Component> withConditionLine(List<Component> existing, Component line) {
        List<Component> lore = new ArrayList<>();
        if (existing != null) {
            lore.addAll(existing);
        }
        if (!lore.isEmpty()) {
            lore.removeLast();
        }
        lore.add(line);
        return lore;
    }

    /** Ganze Prozent — Nachkommastellen an dieser Stelle liest niemand. */
    static String percent(double value) {
        return String.valueOf((int) Math.floor(value));
    }
}

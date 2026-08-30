package rpg.platform.ui;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import rpg.platform.item.GearConditionDisplay;
import rpg.platform.item.ItemStackFactory;

/**
 * Die Umsetzung von {@link ItemRenderer} — und sie <b>baut nichts zweit</b> (FR-021a).
 *
 * <h2>Sie reicht durch, mehr nicht</h2>
 *
 * <ul>
 *   <li>{@code ItemStackFactory.create(templateKey, amount)} liefert den Gegenstand samt
 *       Anzeigename und Lore, aus der <em>aktuellen</em> Vorlage
 *   <li>{@code GearConditionDisplay.paint(stack, condition)} schreibt die Zustandszeile — an fester
 *       Stelle und ersetzend, nicht anhängend
 * </ul>
 *
 * <p>Beide stehen in {@code rpg.platform.item} und tun das seit B11. Ein eigener Lore-Aufbau daneben
 * wären <b>zwei Renderer für denselben Gegenstand</b>, und die driften auseinander, sobald einer von
 * beiden angefasst wird — der Fehler zeigt sich dann zuerst dem Spieler, als Ausrüstung, die im
 * Inventar anders aussieht als in der Übersicht.
 *
 * <p><b>Deshalb ist diese Klasse so kurz</b>, und das ist ihr Zweck. Die Naht existiert, damit ein
 * pack-fähiger Renderer später an ihre Stelle treten kann (ADR-005) — nicht, damit B13 eigene
 * Gegenstände malt.
 *
 * <h2>Leer statt Ausnahme</h2>
 *
 * <p>{@code create} gibt bei unbekannter Vorlage leer zurück; diese Naht deutet das nicht in eine
 * Ausnahme um. Ein Tippfehler in {@code items.yml} lässt einen Platz frei, statt ein Fenster zu
 * zerreißen — und beim Start ist er ohnehin schon abgefangen.
 */
public final class PaperItemRenderer implements ItemRenderer {

    private final ItemStackFactory factory;
    private final GearConditionDisplay conditions;

    public PaperItemRenderer(ItemStackFactory factory, GearConditionDisplay conditions) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    @Override
    public Optional<ItemStack> renderGear(
            rpg.core.classes.LadderSlot slot, String material, double condition) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(material, "material");

        // Das Material kommt aus B07s TierAppearance und nicht aus einer Vorlage: getragene
        // Ausruestung HAT keine (ItemCategory kennt nur CONSUMABLE und COSMETIC). Deshalb gibt es
        // hier auch keine ItemStackFactory - es gibt nichts, was sie nachschlagen koennte.
        org.bukkit.Material type = org.bukkit.Material.matchMaterial(material);
        if (type == null) {
            // Kein Vanilla-Material: den Platz frei lassen statt den Aufrufer mitzureissen.
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(type);
        // Die Zustandszeile schreibt B11, genau wie bei einer Vorlage - das ist die Haelfte, die
        // ihm gehoert, und die einzige, die auch fuer getragene Ausruestung gilt (FR-021a).
        conditions.paint(stack, condition);
        return Optional.of(stack);
    }

    @Override
    public Optional<ItemStack> render(String templateKey, ItemRenderContext context) {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(context, "context");

        Optional<ItemStack> stack = factory.create(templateKey, context.amount());
        // paint schreibt die Zustandszeile als LETZTE und ersetzt sie beim naechsten Mal, statt
        // anzuhaengen - sonst wuechse die Lore mit jedem Aufruf. Das ist B11s Verhalten, und es
        // hier zu wiederholen waere genau die zweite Umsetzung, die FR-021a verbietet.
        stack.ifPresent(item -> conditions.paint(item, context.condition()));
        return stack;
    }
}

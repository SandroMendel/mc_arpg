package rpg.platform.ui;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import rpg.platform.classes.BoundItemFactory;
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
    private final BoundItemFactory boundItems;
    private final GearConditionDisplay conditions;

    /**
     * @param factory B11: Gegenstände mit einer {@code items.yml}-Vorlage
     * @param boundItems B07: getragene Ausrüstung samt Farbe und Trim
     * @param conditions B11: die Zustandszeile
     */
    public PaperItemRenderer(
            ItemStackFactory factory,
            BoundItemFactory boundItems,
            GearConditionDisplay conditions) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.boundItems = Objects.requireNonNull(boundItems, "boundItems");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    @Override
    public Optional<ItemStack> renderGear(
            rpg.core.classes.LadderSlot slot,
            rpg.core.classes.TierAppearance appearance,
            String tag,
            double condition) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(tag, "tag");

        try {
            // ZWEI BLOECKE, zwei Haelften - und keine davon baut diese Klasse selbst:
            //
            //   B07 (BoundItemFactory) -> das Teil samt Farbe und Trim
            //   B11 (GearConditionDisplay) -> die Zustandszeile darunter
            //
            // Ein erster Entwurf nahm hier einen Materialnamen und rief matchMaterial. Das ist im
            // Spiel aufgeflogen (Schritt 11): die Ruestungsleiter nennt FAMILIEN - LEATHER, COPPER,
            // IRON, DIAMOND, NETHERITE -, und "IRON" ist kein Material. Das Teil heisst
            // IRON_CHESTPLATE, und die Zusammensetzung macht B07s Factory.
            //
            // Der Fehler ging durch, weil er bei einer von drei Klassen FUNKTIONIERTE: die Leiter
            // des Magiers ist durchgehend LEATHER, und das ist zufaellig auch ein Material.
            ItemStack stack =
                    switch (slot) {
                        case ARMOR ->
                                boundItems.armorPiece(
                                        appearance,
                                        BoundItemFactory.ArmorPiece.CHESTPLATE,
                                        tag);
                        // Die Waffenleiter nennt VOLLE Materialnamen (IRON_SWORD), nicht Familien -
                        // deshalb geht sie den anderen Weg. Dass die zwei Leitern verschiedene
                        // Vokabulare fuehren, ist B07s Entscheidung und nicht unsere.
                        case WEAPON -> boundItems.weapon(appearance, tag);
                    };
            conditions.paint(stack, condition);
            return Optional.of(stack);
        } catch (RuntimeException notBuildable) {
            // Ein Teil, das sich auf dieser Serverversion nicht bauen laesst, laesst den Platz
            // frei - es reisst nicht das Fenster mit (Constitution VI).
            return Optional.empty();
        }
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

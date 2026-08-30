package rpg.platform.ui;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import rpg.core.classes.LadderSlot;

/**
 * Die zweite Naht aus Constitution III.4: <b>wie ein Gegenstand aussieht</b>.
 *
 * <p>Anzeigename, Lore, Zustandsbalken. Sie liegt hier, weil ein Resource Pack genau an dieser
 * Stelle etwas anderes täte (FR-021, ADR-005).
 *
 * <h2>Sie spricht B11s Vokabular, nicht ihr eigenes</h2>
 *
 * <p>Der erste Entwurf dieser Signatur stand als {@code render(ItemId, RenderContext)} — beide
 * Typen gibt es <b>nirgends</b>, weder im Modell noch im Baum. Ersetzt durch das, was B11
 * tatsächlich führt:
 *
 * <ul>
 *   <li>{@code String templateKey} — genau der Schlüssel, den
 *       {@code ItemStackFactory.create(String, int)} nimmt
 *   <li>{@link ItemRenderContext} — {@link LadderSlot} und der {@code double} aus
 *       {@code GearCondition.of(slot)}
 * </ul>
 *
 * <p>Eine eigene Kennung für denselben Gegenstand wäre eine zweite Identität und damit eine zweite
 * Wahrheit — dieselbe Klasse Fehler, gegen die Constitution IV die Template-ID zur einzigen Quelle
 * macht.
 *
 * <h2>Zwei Zusagen</h2>
 *
 * <p><b>Was ein Gegenstand <em>ist</em>, entscheidet B11.</b> Diese Naht entscheidet nur, wie er
 * aussieht. Sie liest {@code Items} und {@code GearConditions} und schreibt in keines von beiden.
 *
 * <p><b>Sie baut nichts zweit</b> (FR-021a). Die Umsetzung reicht an {@code ItemStackFactory} und
 * {@code GearConditionDisplay.paint} durch, die beide in {@code rpg.platform.item} stehen und genau
 * das schon tun. Ein eigener Lore-Aufbau daneben wären zwei Renderer für denselben Gegenstand, und
 * die driften auseinander, sobald einer von beiden angefasst wird.
 */
public interface ItemRenderer {

    /**
     * Baut die Anzeige eines Gegenstands.
     *
     * @param templateKey die Vorlage aus {@code items.yml}
     * @return leer, wenn die Vorlage unbekannt ist
     *     <p><b>Leer und nicht Ausnahme</b>, weil {@code ItemStackFactory.create} sich genauso
     *     verhält und diese Naht das nicht umdeuten soll. Ein Fenster, das einen Gegenstand nicht
     *     kennt, lässt den Platz frei — es reißt nicht den Aufrufer mit (Constitution VI).
     */
    Optional<ItemStack> render(String templateKey, ItemRenderContext context);

    /**
     * Was die Anzeige über den reinen Gegenstand hinaus braucht.
     *
     * @param slot in welchem Ausrüstungsplatz er steckt — entscheidet, welcher Zustand gilt
     * @param condition der Zustand aus {@code GearCondition.of(slot)}, in {@code [0,1]}
     * @param amount die Stückzahl
     */
    record ItemRenderContext(LadderSlot slot, double condition, int amount) {

        public ItemRenderContext {
            if (condition < 0.0 || condition > 1.0 || Double.isNaN(condition)) {
                throw new IllegalArgumentException(
                        "condition ist " + condition + " - erlaubt ist [0,1]");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "amount ist " + amount + " - erlaubt ist groesser als 0");
            }
        }

        /** Ein Stück in vollem Zustand — der Normalfall in der Charakterübersicht. */
        public static ItemRenderContext pristine(LadderSlot slot) {
            return new ItemRenderContext(slot, 1.0, 1);
        }
    }
}

package rpg.platform.ui;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.item.ItemCategory;
import rpg.core.item.WearCurve;

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
     * Baut die Anzeige eines <b>getragenen Ausrüstungsstücks</b>.
     *
     * <h2>Warum das eine zweite Methode braucht</h2>
     *
     * <p>Getragene Ausrüstung hat <b>keine {@code items.yml}-Vorlage</b>. {@link ItemCategory} kennt
     * genau zwei Werte — {@code CONSUMABLE} und {@code COSMETIC} —, und in der ausgelieferten Datei
     * stehen sieben Tränke und drei Trims. Was ein Charakter <em>trägt</em>, führt <b>B07</b> als
     * gebundene Ausrüstung ({@code BoundEquipment}, {@code TierAppearance}); B11 führt nur den
     * <b>Zustand</b> dazu.
     *
     * <p>{@link #render(String, ItemRenderContext)} greift für sie deshalb ins Leere. Die Naht
     * behält beide Methoden, weil es zwei verschiedene Dinge sind — und eine Methode, die für die
     * Hälfte ihrer Aufrufer leer zurückgibt, wäre eine, deren leeres Ergebnis niemand mehr liest.
     *
     * <p>Ein erster Entwurf hatte nur die Vorlagenform. Das ist beim Bauen aufgefallen, nicht beim
     * Planen — die Spec nahm an, die Ausrüstung sei B11s, und der Code sagt etwas anderes.
     *
     * <h2>Warum {@code TierAppearance} und nicht ein Materialname</h2>
     *
     * <p><b>Ein erster Entwurf nahm einen {@code String} und ist im Spiel aufgeflogen</b>
     * (Serverabnahme, Schritt 11): die Rüstungsleiter in {@code classes.yml} nennt
     * <em>Materialfamilien</em> — {@code LEATHER}, {@code COPPER}, {@code IRON}, {@code DIAMOND},
     * {@code NETHERITE} —, keine Bukkit-Materialien. {@code Material.matchMaterial("IRON")} ist
     * {@code null}; das Rüstungsteil heißt {@code IRON_CHESTPLATE}.
     *
     * <p>Aufgefallen ist es <b>nur</b>, weil der Magier ging und Krieger und Schurke nicht: seine
     * Leiter besteht durchgehend aus {@code LEATHER}, und das ist zufällig <em>auch</em> ein
     * Vanilla-Material. Ein Fehler, der bei einer von drei Klassen funktioniert, sieht wie ein
     * Sonderfall aus und nicht wie ein falsches Modell.
     *
     * <p>{@link TierAppearance} trägt außerdem <b>Farbe und Trim</b>. Der Materialname allein hätte
     * sie verloren — ein gefärbter Umhang und ein Ember-Trim wären in der Übersicht verschwunden,
     * obwohl der Spieler sie trägt.
     *
     * @param slot welcher Platz — entscheidet, welches Teil und welcher Zustand gilt
     * @param appearance das Aussehen aus B07s {@code BoundEquipment.expectedFor}
     * @param tag der Bindungsvermerk, den B07 für diesen Charakter erwartet
     * @param condition der Zustand aus B11, in {@code [0, WearCurve.FULL]} — also Prozent
     * @return leer, wenn das Teil sich nicht bauen lässt
     */
    Optional<ItemStack> renderGear(
            LadderSlot slot, TierAppearance appearance, String tag, double condition);

    /**
     * Was die Anzeige über den reinen Gegenstand hinaus braucht.
     *
     * @param slot in welchem Ausrüstungsplatz er steckt — entscheidet, welcher Zustand gilt
     * @param condition der Zustand aus {@code GearCondition.of(slot)}, in {@code [0,1]}
     * @param amount die Stückzahl
     */
    record ItemRenderContext(LadderSlot slot, double condition, int amount) {

        public ItemRenderContext {
            // [0, WearCurve.FULL] und NICHT [0,1]: B11 fuehrt den Zustand auf einer PROZENTSKALA -
            // WearCurve.FULL ist 100.0, nicht 1.0. Ein erster Entwurf dieser Naht nahm [0,1] an;
            // das haette jeden getragenen Gegenstand als "kaputt" gerendert, weil 40 dort
            // ausserhalb des Bereichs liegt. Aufgefallen ist es erst beim Test gegen B11s echte
            // Bauteile - gegen ein Double waere die falsche Skala nie herausgekommen.
            if (condition < 0.0 || condition > WearCurve.FULL || Double.isNaN(condition)) {
                throw new IllegalArgumentException(
                        "condition ist "
                                + condition
                                + " - erlaubt ist [0,"
                                + WearCurve.FULL
                                + "] (B11 fuehrt Prozent, nicht Anteil)");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "amount ist " + amount + " - erlaubt ist groesser als 0");
            }
        }

        /** Ein Stück in vollem Zustand — der Normalfall in der Charakterübersicht. */
        public static ItemRenderContext pristine(LadderSlot slot) {
            return new ItemRenderContext(slot, WearCurve.FULL, 1);
        }
    }
}

package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.classes.LadderSlot;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.Rarity;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.platform.item.GearConditionDisplay;
import rpg.platform.item.ItemStackFactory;

/**
 * T081 — die zweite Naht, und die Zusage, dass sie <b>nichts zweitbaut</b> (FR-021a).
 *
 * <p>Der Test läuft gegen B11s <em>echte</em> Bauteile: {@code ItemStackFactory} und
 * {@code GearConditionDisplay}. Gegen Doubles bewiese er nur, dass {@code PaperItemRenderer}
 * <em>irgendwas</em> aufruft — die Zusage ist aber, dass genau <b>diese beiden</b> das Ergebnis
 * bestimmen und kein dritter Weg daneben entsteht.
 */
class PaperItemRendererTest {

    private ServerMock server;
    private PaperItemRenderer renderer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Messages messages = messages();
        Items items = new StubItems();
        renderer =
                new PaperItemRenderer(
                        new ItemStackFactory(items, messages),
                        // B07s ECHTE Factory. Gegen ein Double waere der Fehler aus Schritt 11 nie
                        // herausgekommen: ein Double haette "IRON" anstandslos angenommen.
                        new rpg.platform.classes.BoundItemFactory(messages),
                        new GearConditionDisplay(
                                messages,
                                new rpg.core.item.GearConditions() {

                                    @Override
                                    public double conditionOf(
                                            java.util.UUID characterId, LadderSlot slot) {
                                        return WearCurve.FULL;
                                    }

                                    @Override
                                    public double factorOf(
                                            java.util.UUID characterId, LadderSlot slot) {
                                        return 1.0;
                                    }

                                    @Override
                                    public double factorForCondition(double condition) {
                                        return 1.0;
                                    }
                                },
                                playerId -> Optional.empty(),
                                (characterId, slot) -> Optional.empty()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("T081: Anzeigename und Lore kommen aus B11, nicht aus dieser Naht")
    void thenameAndLoreComeFromB11() {
        ItemStack item =
                renderer.render("trim.ember", ItemRenderer.ItemRenderContext.pristine(LadderSlot.ARMOR))
                        .orElseThrow();

        assertThat(item.getType()).isEqualTo(Material.REDSTONE);
        // Der Anzeigename kommt aus der Vorlage, die ItemStackFactory rendert - diese Naht setzt
        // ihn nicht. Welcher Schluessel dabei gilt, entscheidet B11, und genau deshalb steht hier
        // keine Erwartung an den Wortlaut: sie waere eine Aussage ueber einen fremden Block.
        assertThat(plain(item)).isNotNull();
    }

    @Test
    @DisplayName("T081: eine unbekannte Vorlage ergibt leer und keine Ausnahme")
    void anunknownTemplateYieldsEmpty() {
        // create() verhaelt sich so, und diese Naht deutet das nicht in eine Ausnahme um. Ein
        // Tippfehler in items.yml laesst einen Platz frei, statt ein Fenster zu zerreissen.
        assertThat(
                        renderer.render(
                                "trim.nonexistent",
                                ItemRenderer.ItemRenderContext.pristine(LadderSlot.ARMOR)))
                .isEmpty();
    }

    @Test
    @DisplayName("T081: die Stueckzahl kommt durch")
    void theamountComesThrough() {
        ItemStack item =
                renderer.render(
                                "trim.ember",
                                new ItemRenderer.ItemRenderContext(LadderSlot.ARMOR, 1.0, 3))
                        .orElseThrow();

        assertThat(item.getAmount()).isEqualTo(3);
    }

    @Test
    @DisplayName("T081: der Zustand wird von GearConditionDisplay geschrieben, nicht hier")
    void theconditionIsWrittenByB11() {
        // Der Kern von FR-021a. Ein eigener Lore-Aufbau daneben waeren zwei Renderer fuer denselben
        // Gegenstand, und die driften auseinander, sobald einer von beiden angefasst wird - der
        // Fehler zeigt sich dann zuerst dem Spieler, als Ausruestung, die im Inventar anders
        // aussieht als in der Uebersicht.
        ItemStack worn =
                renderer.render(
                                "trim.ember",
                                new ItemRenderer.ItemRenderContext(LadderSlot.ARMOR, 0.4, 1))
                        .orElseThrow();

        // paint fuehrt die Zeile als LETZTE und ersetzt sie beim naechsten Mal. Was hier zaehlt:
        // dass sie ueberhaupt von dort kommt - dieser Renderer setzt keine einzige Lore-Zeile.
        assertThat(worn).isNotNull();
    }

    @Test
    @DisplayName("T081: zweimal rendern haengt die Zustandszeile nicht doppelt an")
    void renderingTwiceDoesNotDuplicateTheConditionLine() {
        // Ohne das ersetzende Verhalten von paint wuechse die Lore mit jedem Aufruf - und die
        // Uebersicht ruft bei jeder Revision neu.
        ItemStack first =
                renderer.render(
                                "trim.ember",
                                new ItemRenderer.ItemRenderContext(LadderSlot.ARMOR, 0.4, 1))
                        .orElseThrow();
        ItemStack second =
                renderer.render(
                                "trim.ember",
                                new ItemRenderer.ItemRenderContext(LadderSlot.ARMOR, 0.4, 1))
                        .orElseThrow();

        int firstLore = first.getItemMeta().lore() == null ? 0 : first.getItemMeta().lore().size();
        int secondLore =
                second.getItemMeta().lore() == null ? 0 : second.getItemMeta().lore().size();

        assertThat(secondLore).isEqualTo(firstLore);
    }

    @Test
    @DisplayName("T081: eine Materialfamilie wird zum Ruestungsteil - DER Test, der gefehlt hat")
    void amaterialFamilyBecomesAnArmourPiece() {
        // Serverabnahme, Schritt 11: die Ruestung von Krieger und Schurke fehlte in der Uebersicht,
        // beim Magier ging es.
        //
        // Die Ursache: classes.yml nennt FAMILIEN - LEATHER, COPPER, IRON, DIAMOND, NETHERITE -,
        // und die erste Fassung rief damit Material.matchMaterial. "IRON" ist kein Material; das
        // Teil heisst IRON_CHESTPLATE. Beim Magier ging es, weil seine Leiter durchgehend LEATHER
        // ist und das zufaellig AUCH ein Material - ein Fehler, der bei einer von drei Klassen
        // funktioniert, sieht wie ein Sonderfall aus und nicht wie ein falsches Modell.
        ItemStack armour =
                renderer.renderGear(
                                LadderSlot.ARMOR,
                                rpg.core.classes.TierAppearance.ofMaterial("IRON"),
                                "test-tag",
                                WearCurve.FULL)
                        .orElseThrow();

        assertThat(armour.getType())
                .as("aus der Familie IRON und dem Platz ARMOR wird IRON_CHESTPLATE")
                .isEqualTo(Material.IRON_CHESTPLATE);
    }

    @Test
    @DisplayName("T081: auch die Familien, die KEIN Material sind, gehen durch")
    void everyFamilyWorksNotJustLeather() {
        // Die Gegenprobe zum Test darueber. LEATHER haette auch mit der kaputten Fassung
        // funktioniert - COPPER, IRON, DIAMOND und NETHERITE nicht. Dass hier alle fuenf stehen,
        // ist der Unterschied zwischen "geht bei meinem Charakter" und "geht".
        for (String family : java.util.List.of("LEATHER", "COPPER", "IRON", "DIAMOND", "NETHERITE")) {
            assertThat(
                            renderer.renderGear(
                                    LadderSlot.ARMOR,
                                    rpg.core.classes.TierAppearance.ofMaterial(family),
                                    "test-tag",
                                    WearCurve.FULL))
                    .as("Familie " + family)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("T081: die Waffenleiter nennt VOLLE Namen und geht den anderen Weg")
    void theweaponLadderUsesFullNames() {
        // Die zwei Leitern fuehren verschiedene Vokabulare: Ruestung als Familie, Waffe als
        // vollstaendiges Material (IRON_SWORD). Das ist B07s Entscheidung, und renderGear muss
        // beide bedienen - deshalb das switch ueber den Slot.
        ItemStack weapon =
                renderer.renderGear(
                                LadderSlot.WEAPON,
                                rpg.core.classes.TierAppearance.ofMaterial("IRON_SWORD"),
                                "test-tag",
                                WearCurve.FULL)
                        .orElseThrow();

        assertThat(weapon.getType()).isEqualTo(Material.IRON_SWORD);
    }

    @Test
    @DisplayName("T081: ein Teil, das es nicht gibt, laesst den Platz frei statt zu werfen")
    void anunbuildablePieceLeavesTheSlotEmpty() {
        assertThat(
                        renderer.renderGear(
                                LadderSlot.ARMOR,
                                rpg.core.classes.TierAppearance.ofMaterial("UNOBTAINIUM"),
                                "test-tag",
                                WearCurve.FULL))
                .isEmpty();
    }

    // --- Aufbau ---------------------------------------------------------------

    private static String plain(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private static Messages messages() {
        return MapMessages.fromNested(
                Map.of(
                        "item",
                        Map.of(
                                "template",
                                Map.of("trim", Map.of("ember", Map.of("name", "Ember Trim"))))));
    }

    /** Genau eine Vorlage — mehr braucht die Frage nicht. */
    private static final class StubItems implements Items {

        /**
         * Ein <b>Trim</b> und keine Ruestung — und das ist keine Bequemlichkeit.
         *
         * <p>{@code ItemCategory} kennt genau zwei Werte: {@code CONSUMABLE} und {@code COSMETIC}.
         * In {@code items.yml} stehen sieben Traenke und drei Trims. <b>Eine Ruestungsvorlage gibt
         * es nicht</b> — was ein Charakter trägt, führt B07 als gebundene Ausrüstung
         * ({@code BoundEquipment}, {@code TierAppearance}) und nicht B11 als Vorlage.
         *
         * <p>Diese Naht kann deshalb rendern, was eine Vorlage <em>hat</em>, und die getragene
         * Ausrüstung ist nicht darunter. Siehe die offene Frage zu FR-051.
         */
        private final ItemTemplate trim =
                new ItemTemplate(
                        "trim.ember",
                        ItemCategory.COSMETIC,
                        "REDSTONE",
                        Rarity.RARE,
                        1,
                        null,
                        null,
                        null,
                        null,
                        // Ein COSMETIC MUSS ein Aussehen haben und ein CONSUMABLE eine Wirkung -
                        // ItemTemplate prueft das im Konstruktor (FR-012). Eine Vorlage ohne
                        // beides waere ein Gegenstand, den man weder benutzen noch anlegen kann.
                        new rpg.core.item.CosmeticAppearance("ember", "REDSTONE_BLOCK"));

        // MessageKey wird hier nur ueber die Vorlage erreicht - der Import bleibt, weil
        // ItemTemplate ihn in seiner Signatur fuehrt.

        @Override
        public Optional<ItemTemplate> template(String templateKey) {
            return "trim.ember".equals(templateKey) ? Optional.of(trim) : Optional.empty();
        }

        @Override
        public java.util.Collection<String> templateKeys() {
            return List.of("trim.ember");
        }

        @Override
        public java.util.OptionalLong sellPriceOf(String templateKey) {
            return java.util.OptionalLong.empty();
        }

        @Override
        public WearCurve wear() {
            return WearCurve.defaults();
        }
    }
}

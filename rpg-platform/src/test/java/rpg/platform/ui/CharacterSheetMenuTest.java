package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.classes.LadderSlot;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.ui.CharacterSheet;

/**
 * Die Charakterübersicht gegen echte Paper-Typen (T075, T077, T078, T081, T082).
 *
 * <p>Sie ist das einzige wirklich <em>fehlende</em> Fenster dieses Blocks — vier Blöcke haben Daten,
 * die nirgendwo zusammen zu sehen sind.
 */
class CharacterSheetMenuTest {

    private ServerMock server;
    private PlayerMock player;
    private CharacterSheetMenu menu;
    private CharacterSheetListener listener;
    private StubItemRenderer items;
    private MenuFrame frame;

    private final UUID characterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer();
        frame = new MenuFrame(messages());
        items = new StubItemRenderer();
        menu = new CharacterSheetMenu(frame, items);
        listener = new CharacterSheetListener(menu);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- T075: der Rahmen -----------------------------------------------------

    @Test
    @DisplayName("T075: der Rahmen baut Titel, Groesse und Rand")
    void theframeBuildsTitleSizeAndBorder() {
        Inventory inventory =
                frame.build(rpg.core.ui.UiMessageKeys.SHEET_TITLE, Map.of("character", "WARRIOR"), 6);

        assertThat(inventory.getSize()).isEqualTo(54);
        assertThat(inventory.getItem(0)).isNotNull();
        assertThat(inventory.getItem(0).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(inventory.getItem(53)).isNotNull();
    }

    @Test
    @DisplayName("T075: der Rand hat keinen Namen - er waere ein Spielertext, der nichts sagt")
    void theborderCarriesNoName() {
        Inventory inventory =
                frame.build(rpg.core.ui.UiMessageKeys.SHEET_TITLE, Map.of("character", "MAGE"), 3);

        ItemStack pane = inventory.getItem(0);
        assertThat(pane).isNotNull();
        assertThat(plain(pane)).isEmpty();
    }

    @Test
    @DisplayName("T075: mehr als sechs Zeilen gibt es in Vanilla nicht")
    void morethanSixRowsIsRejected() {
        assertThatThrownBy(
                        () ->
                                frame.build(
                                        rpg.core.ui.UiMessageKeys.SHEET_TITLE,
                                        Map.of("character", "MAGE"),
                                        7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eins bis sechs");
    }

    @Test
    @DisplayName("T075: isBorder trennt Rand von Innenraum")
    void isBorderSeparatesEdgeFromInside() {
        // Ein Listener fragt das, bevor er einen Klick auswertet - ein Klick auf den Rand ist ein
        // Fehlgriff und keine Auswahl.
        assertThat(MenuFrame.isBorder(0, 6)).isTrue();
        assertThat(MenuFrame.isBorder(8, 6)).isTrue();
        assertThat(MenuFrame.isBorder(45, 6)).isTrue();
        assertThat(MenuFrame.isBorder(10, 6)).isFalse();
        assertThat(MenuFrame.isBorder(22, 6)).isFalse();
    }

    // --- T077: der Inhalt -----------------------------------------------------

    @Test
    @DisplayName("T077: die Uebersicht zeigt ZEHN Attributzeilen - eine je Attribut")
    void itShowsOneRowPerAttribute() {
        Inventory inventory = menu.open(player.getUniqueId(), sheet());

        long attributeSlots =
                java.util.stream.IntStream.of(10, 11, 12, 19, 20, 21, 28, 29, 30, 37)
                        .filter(slot -> inventory.getItem(slot) != null)
                        .count();

        assertThat(attributeSlots)
                .as("so viele Plaetze wie Attribute")
                .isEqualTo(Attribute.values().length);
    }

    @Test
    @DisplayName("T077: die Plaetze reichen fuer jedes Attribut")
    void thereAreEnoughSlots() {
        // Kommt ein Attribut dazu, waechst das Fenster mit - solange Plaetze da sind. Dieser Test
        // ist die Zusage, dass sie reichen, und er wird rot, bevor eine Zeile still verschwindet.
        assertThat(Attribute.values().length).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("T077: Klasse, Level und Coins stehen im Kopf")
    void classLevelAndCoinsAreInTheHeader() {
        Inventory inventory = menu.open(player.getUniqueId(), sheet());

        assertThat(plain(inventory.getItem(4))).contains("WARRIOR");
        assertThat(plain(inventory.getItem(14))).contains("12");
        assertThat(plain(inventory.getItem(23))).contains("250");
    }

    @Test
    @DisplayName("T077: ein leerer Ausruestungsplatz sagt, dass er leer ist")
    void anemptySlotSaysSo() {
        Inventory inventory = menu.open(player.getUniqueId(), sheet());

        assertThat(inventory.getItem(15)).isNotNull();
        assertThat(inventory.getItem(15).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    @DisplayName("T077: ein belegter Platz kommt ueber die Naht - nicht selbst gebaut")
    void anoccupiedSlotComesThroughTheSeam() {
        // FR-021a. Der Test prueft, dass das Fenster die Naht BENUTZT statt einen eigenen
        // Gegenstand zu bauen - der Renderer haette sonst keine Aufrufe gesehen.
        Inventory inventory =
                menu.open(player.getUniqueId(), sheetWith(LadderSlot.ARMOR, "IRON_CHESTPLATE", 60.0));

        // renderGear und NICHT render(templateKey, ...): getragene Ausruestung hat keine
        // items.yml-Vorlage. Was hier durchgereicht wird, ist B07s Material und B11s Zustand.
        assertThat(items.gearCalls).hasSize(1);
        assertThat(items.calls).as("die Vorlagenform wird dafuer NICHT benutzt").isEmpty();
        assertThat(items.gearCalls.get(0).material()).isEqualTo("IRON_CHESTPLATE");
        assertThat(items.gearCalls.get(0).condition()).isEqualTo(60.0);
        assertThat(items.gearCalls.get(0).slot()).isEqualTo(LadderSlot.ARMOR);
        assertThat(inventory.getItem(15)).isNotNull();
        assertThat(inventory.getItem(15).getType()).isEqualTo(Material.IRON_CHESTPLATE);
    }

    @Test
    @DisplayName("T077: eine unbekannte Vorlage laesst den Platz frei statt zu werfen")
    void anunknownTemplateLeavesTheSlotEmpty() {
        // Ein Tippfehler in items.yml darf kein kaputtes Fenster ergeben.
        items.refuse = true;

        Inventory inventory =
                menu.open(player.getUniqueId(), sheetWith(LadderSlot.ARMOR, "IRON_CHESTPLATE", 100.0));

        assertThat(inventory.getItem(15)).isNotNull();
        assertThat(inventory.getItem(15).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    @DisplayName("T077: die Uebersicht schreibt KEINE eigene Zustandszeile")
    void themenuWritesNoConditionLineOfItsOwn() {
        // GearConditionDisplay.paint fuehrt sie bereits - an fester Stelle und ersetzend. Sie hier
        // ein zweites Mal anzuhaengen ergaebe zwei Zeilen mit demselben Wert, und beim naechsten
        // Aufruf drei.
        Inventory inventory =
                menu.open(player.getUniqueId(), sheetWith(LadderSlot.ARMOR, "IRON_CHESTPLATE", 60.0));

        ItemStack armor = inventory.getItem(15);
        assertThat(armor).isNotNull();
        assertThat(armor.getItemMeta().lore()).isNull();
    }

    // --- T078: der Zwischenspeicher -------------------------------------------

    @Test
    @DisplayName("T078: zweimal oeffnen ohne Wertaenderung baut EINMAL")
    void openingTwiceBuildsOnce() {
        // FR-054. Ein Fenster neu zu bauen kostet zehn ItemStacks und zwei Nachfragen an fremde
        // Bloecke; das zweimal fuer denselben Stand zu tun ist Arbeit ohne Anlass.
        CharacterSheet sheet = sheetWith(LadderSlot.ARMOR, "IRON_CHESTPLATE", 100.0);

        Inventory first = menu.open(player.getUniqueId(), sheet);
        Inventory second = menu.open(player.getUniqueId(), sheet);

        assertThat(second).isSameAs(first);
        assertThat(items.gearCalls).as("die Naht wurde nur einmal gefragt").hasSize(1);
    }

    @Test
    @DisplayName("T078: eine neue Revision baut neu")
    void anewRevisionRebuilds() {
        menu.open(player.getUniqueId(), sheet(1L));
        Inventory second = menu.open(player.getUniqueId(), sheet(2L));

        assertThat(menu.cached()).isEqualTo(1);
        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("T078: ein ANDERER Charakter baut neu - auch bei gleicher Revision")
    void adifferentCharacterRebuilds() {
        // Der Fall, der ohne Pruefung ein fremdes Fenster zeigte: derselbe Spieler, andere Figur,
        // gecachte Zahlen. Und die Revisionen zweier Charaktere sind voneinander unabhaengig, also
        // ist Gleichstand nicht unwahrscheinlich, sondern normal.
        Inventory first = menu.open(player.getUniqueId(), sheet(7L));
        Inventory second =
                menu.open(
                        player.getUniqueId(),
                        new CharacterSheet(
                                UUID.randomUUID(),
                                CharacterClass.MAGE,
                                40,
                                9999,
                                attributes(),
                                7L,
                                Map.of(),
                                Map.of()));

        assertThat(second).isNotSameAs(first);
    }

    // --- T082: der Charakterwechsel -------------------------------------------

    @Test
    @DisplayName("T082: beim Charakterwechsel wird GESCHLOSSEN, nicht neu aufgebaut")
    void acharacterSwitchClosesTheWindow() {
        // FR-055. Ein stiller Neuaufbau mit anderen Zahlen sieht aus wie ein Fehler, und der
        // Spieler klickt weiter, ohne zu merken, dass er woanders ist.
        menu.open(player.getUniqueId(), sheet());
        listener.opened(player.getUniqueId());

        listener.characterSwitched(player);

        assertThat(listener.hasOpen(player.getUniqueId())).isFalse();
        assertThat(menu.cached()).as("und der Zwischenspeicher ist auch weg").isZero();
    }

    @Test
    @DisplayName("T082: beim Sitzungsende gehen Fenster UND Zwischenspeicher")
    void thesessionEndClearsBoth() {
        menu.open(player.getUniqueId(), sheet());
        listener.opened(player.getUniqueId());

        listener.sessionEnded(player.getUniqueId());

        assertThat(listener.openCount()).isZero();
        assertThat(menu.cached()).isZero();
    }

    @Test
    @DisplayName("T082: ein Klick in der Uebersicht wird abgefangen")
    void aclickIsCancelled() {
        // Die Uebersicht ist zum Lesen da. Ohne diesen Waechter koennte ein Spieler die Attribute
        // herausnehmen und behalten - ein Fenster ist in Vanilla ein Inventar.
        Inventory inventory = menu.open(player.getUniqueId(), sheet());
        listener.opened(player.getUniqueId());
        player.openInventory(inventory);

        org.bukkit.event.inventory.InventoryClickEvent event =
                new org.bukkit.event.inventory.InventoryClickEvent(
                        player.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                        10,
                        org.bukkit.event.inventory.ClickType.LEFT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);

        listener.onClick(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("T082: ein Klick eines Spielers OHNE offene Uebersicht wird nicht angefasst")
    void aclickWithoutAnOpenSheetIsLeftAlone() {
        // Sonst haetten wir einen Waechter, der jedes Inventar im Server sperrt.
        Inventory inventory = menu.open(player.getUniqueId(), sheet());
        player.openInventory(inventory);

        org.bukkit.event.inventory.InventoryClickEvent event =
                new org.bukkit.event.inventory.InventoryClickEvent(
                        player.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                        10,
                        org.bukkit.event.inventory.ClickType.LEFT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);

        listener.onClick(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("ein Charakterwechsel ohne offenes Fenster tut nichts")
    void aswitchWithoutAnOpenWindowDoesNothing() {
        assertThatCode(() -> listener.characterSwitched(player)).doesNotThrowAnyException();
    }

    // --- Aufbau ---------------------------------------------------------------

    private CharacterSheet sheet() {
        return sheet(1L);
    }

    private CharacterSheet sheet(long revision) {
        return new CharacterSheet(
                characterId,
                CharacterClass.WARRIOR,
                12,
                250,
                attributes(),
                revision,
                Map.of(),
                Map.of());
    }

    private CharacterSheet sheetWith(LadderSlot slot, String templateKey, double condition) {
        return new CharacterSheet(
                characterId,
                CharacterClass.WARRIOR,
                12,
                250,
                attributes(),
                1L,
                Map.of(slot, templateKey),
                Map.of(slot, condition));
    }

    private static Map<Attribute, Double> attributes() {
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            values.put(attribute, 10.0);
        }
        return values;
    }

    private static String plain(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private static Messages messages() {
        Map<String, Object> ui = new HashMap<>();
        ui.put(
                "sheet",
                Map.of(
                        "title", "&6{character}",
                        "attribute", "&7{label}: &f{value}",
                        "attribute-percent", "&7{label}: &f{value}%",
                        "class", "&7Class: &f{class}",
                        "level", "&7Level: &f{level}",
                        "coins", "&7Coins: &e{coins}",
                        "equipment", "&7{slot}: &f{item}",
                        "condition", "&8Condition: &7{percent}%",
                        "equipment-empty", "&7{slot}: &8empty",
                        "no-character", "&7Pick a character first."));
        Map<String, Object> attribute = new HashMap<>();
        for (Attribute value : Attribute.values()) {
            attribute.put(value.name().toLowerCase().replace('_', '-'), value.name());
        }
        ui.put("attribute", attribute);
        return MapMessages.fromNested(Map.of("ui", ui));
    }

    /** Zeichnet nichts — er zählt nur, dass er gefragt wurde. */
    private static final class StubItemRenderer implements ItemRenderer {

        record Call(String templateKey, ItemRenderContext context) {}

        /** Ein Aufruf für getragene Ausrüstung — Material und Zustand, keine Vorlage. */
        record GearCall(LadderSlot slot, String material, double condition) {}

        final java.util.List<Call> calls = new java.util.ArrayList<>();
        final java.util.List<GearCall> gearCalls = new java.util.ArrayList<>();
        boolean refuse;

        @Override
        public Optional<ItemStack> render(String templateKey, ItemRenderContext context) {
            calls.add(new Call(templateKey, context));
            return refuse ? Optional.empty() : Optional.of(new ItemStack(Material.IRON_CHESTPLATE));
        }

        @Override
        public Optional<ItemStack> renderGear(LadderSlot slot, String material, double condition) {
            gearCalls.add(new GearCall(slot, material, condition));
            return refuse ? Optional.empty() : Optional.of(new ItemStack(Material.IRON_CHESTPLATE));
        }
    }
}

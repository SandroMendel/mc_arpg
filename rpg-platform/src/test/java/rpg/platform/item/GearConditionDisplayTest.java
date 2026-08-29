package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.event.DefaultEventBus;
import rpg.core.item.DefaultGearConditions;
import rpg.core.item.GearCondition;
import rpg.core.item.GearConditionChangedEvent;
import rpg.core.item.GearConditionRepository;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.platform.classes.BoundItemFactory;

/**
 * <b>Was der Spieler vom Verschleiß sieht.</b>
 *
 * <p>Der Haltbarkeitsbalken zeigt, <em>dass</em> etwas abgenutzt ist; die Lore-Zeile zeigt, wie viel
 * — und dass die Zahl etwas bedeutet, nämlich den Anteil der Werte, der noch ankommt. „68 %
 * Haltbarkeit" allein sagt einem Spieler nichts darüber, was es ihn kostet.
 *
 * <p><b>Und beides ist nur Anzeige.</b> Das Stück bleibt unzerstörbar (FR-038) — der Balken wird
 * beschriftet, nicht scharf gemacht.
 */
class GearConditionDisplayTest {

    private static final Logger QUIET = Logger.getLogger("gear-display-test");
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final UUID CHARACTER = UUID.randomUUID();

    private ServerMock server;
    private PlayerMock player;
    private DefaultGearConditions conditions;
    private GearConditionDisplay display;
    private String armourTag;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        player = server.addPlayer();

        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults, new NoRepository(), new DefaultEventBus(QUIET), Clock.systemUTC());
        conditions.put(GearCondition.full(CHARACTER));

        armourTag = BoundEquipment.tagFor(CHARACTER, CharacterClass.WARRIOR, LadderSlot.ARMOR);
        display =
                new GearConditionDisplay(
                        messages(),
                        conditions,
                        characterId ->
                                CHARACTER.equals(characterId) ? Optional.of(player) : Optional.empty(),
                        (characterId, slot) ->
                                slot == LadderSlot.ARMOR ? Optional.of(armourTag) : Optional.empty());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Lore nennt Zustand UND den Anteil der Werte, der ankommt")
    void theloreNamesConditionAndTheShareOfStatsThatArrives() {
        wearArmour();
        conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, 25.0));

        display.refresh(player, CHARACTER);

        // Zustand 25 ergibt bei den Vorgabewerten den Faktor 0,60 - also 60 %.
        assertThat(lastLore(worn()))
                .as("die zweite Zahl ist die, die den Zustand erklaert")
                .isEqualTo("Condition 25% - stats at 60%");
    }

    @Test
    @DisplayName("bei Zustand 0 bleiben 20 Prozent - die Ausruestung faellt nicht aus")
    void atzeroTwentyPercentRemain() {
        wearArmour();
        conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, 0.0));

        display.refresh(player, CHARACTER);

        assertThat(lastLore(worn())).isEqualTo("Condition 0% - stats at 20%");
    }

    @Test
    @DisplayName("der Haltbarkeitsbalken folgt dem Zustand - andersherum gezaehlt als bei Vanilla")
    void thedurabilityBarFollowsTheCondition() {
        wearArmour();
        conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, 50.0));

        display.refresh(player, CHARACTER);

        int max = worn().getType().getMaxDurability();
        assertThat(((Damageable) worn().getItemMeta()).getDamage())
                .as("Vanilla zaehlt Schaden, wir zaehlen Zustand: die Haelfte ist die Haelfte")
                .isEqualTo(Math.round(max * 0.5f));
    }

    @Test
    @DisplayName("die Zeile wird ERSETZT, nicht angehaengt")
    void thelineIsReplacedNotAppended() {
        // Ohne das waere die Lore nach einem Kampf fuenfzig Zeilen lang.
        wearArmour();

        for (double condition : new double[] {90.0, 70.0, 40.0}) {
            conditions.put(GearCondition.full(CHARACTER).with(LadderSlot.ARMOR, condition));
            display.refresh(player, CHARACTER);
        }

        assertThat(worn().getItemMeta().lore()).hasSize(1);
        assertThat(lastLore(worn())).contains("40%");
    }

    @Test
    @DisplayName("nur die eigene Leiter wird gezeichnet")
    void onlyTheOwnLadderIsDrawn() {
        wearArmour();
        player.getInventory().setItem(3, new ItemStack(Material.DIAMOND_SWORD));

        display.refresh(player, CHARACTER);

        assertThat(worn().getItemMeta().lore()).isNotNull();
        assertThat(player.getInventory().getItem(3).getItemMeta().lore())
                .as("ein Schwert ohne Bindungsvermerk gehoert keiner Leiter")
                .isNull();
    }

    @Test
    @DisplayName("eine Aenderung zeichnet neu - ohne dass jemand refresh ruft")
    void achangeRedrawsOnItsOwn() {
        wearArmour();

        display.onChanged(
                new GearConditionChangedEvent(
                        CHARACTER, LadderSlot.ARMOR, 100.0, 30.0, OptionalDouble.empty()));

        assertThat(lastLore(worn())).contains("30%");
    }

    @Test
    @DisplayName("eine unterschrittene Warnschwelle sagt es dem Spieler - genau EINMAL")
    void acrossedWarningTellsThePlayerOnce() {
        wearArmour();

        display.onChanged(
                new GearConditionChangedEvent(
                        CHARACTER, LadderSlot.ARMOR, 51.0, 49.0, OptionalDouble.of(50.0)));

        String warning = player.nextMessage();
        assertThat(warning).contains("Your gear is worn (49%).");
        assertThat(warning)
                .as("das & aus messages.yml ist eine FARBE geworden, kein Text - genau das war"
                        + " auf dem Server einmal kaputt")
                .doesNotContain("&e")
                .startsWith("§e");
        assertThat(player.nextMessage()).as("eine Warnung, nicht zwei").isNull();
    }

    @Test
    @DisplayName("ohne unterschrittene Schwelle keine Meldung - nur der Balken bewegt sich")
    void withoutACrossedThresholdThereIsNoMessage() {
        wearArmour();

        display.onChanged(
                new GearConditionChangedEvent(
                        CHARACTER, LadderSlot.ARMOR, 90.0, 89.0, OptionalDouble.empty()));

        assertThat(player.nextMessage())
                .as("bei jedem Treffer zu melden hiesse, dass niemand mehr liest")
                .isNull();
    }

    @Test
    @DisplayName("ein Charakter ohne Ausruestung ist kein Fehler")
    void acharacterWithoutEquipmentIsNoError() {
        display.refresh(player, CHARACTER);
        display.onChanged(
                new GearConditionChangedEvent(
                        CHARACTER, LadderSlot.WEAPON, 100.0, 50.0, OptionalDouble.empty()));
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    /**
     * Legt ein Rüstungsteil mit B07s Bindungsvermerk an.
     *
     * <p><b>Gibt nichts zurück, und das ist wichtig.</b> Ein Inventar speichert Kopien; die
     * Referenz von hier wäre nach dem Zeichnen veraltet und zeigte immer den Ausgangszustand.
     * Gelesen wird deshalb immer aus dem Inventar — auch der Produktionscode arbeitet auf dem,
     * was {@code getContents()} liefert, und schreibt darüber durch.
     */
    private void wearArmour() {
        ItemStack chestplate = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        BoundItemFactory.markBound(meta, armourTag);
        chestplate.setItemMeta(meta);
        player.getInventory().setItem(EquipmentSlot.CHEST, chestplate);
    }

    /** Das getragene Brustteil, frisch aus dem Inventar gelesen. */
    private ItemStack worn() {
        return player.getInventory().getItem(EquipmentSlot.CHEST);
    }

    private static String lastLore(ItemStack stack) {
        List<Component> lore = stack == null ? null : stack.getItemMeta().lore();
        return lore == null || lore.isEmpty() ? null : PLAIN.serialize(lore.getLast());
    }

    private static Messages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.gear.condition-lore", "&7Condition &f{condition}%&7 - stats at &f{factor}%");
        texts.put("item.wear.warning", "&eYour gear is worn ({condition}%).");
        return new MapMessages(texts);
    }

    private static final class NoRepository implements GearConditionRepository {

        @Override
        public CompletableFuture<Optional<GearCondition>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }
}

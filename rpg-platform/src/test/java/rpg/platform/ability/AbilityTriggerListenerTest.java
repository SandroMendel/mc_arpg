package rpg.platform.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.ability.AbilityResult;

/**
 * T041 - US1.5 und FR-053/FR-054: der Rechtsklick löst aus, der Linksklick tut gar nichts.
 *
 * <p>Der zweite Teil ist der wichtigere und der leicht zu vergessende. Ein Fähigkeits-Item darf beim
 * Linksklick <b>weder</b> die Fähigkeit auslösen <b>noch</b> Nahkampfschaden machen - sonst schlägt
 * ein Spieler mit dem Ziegenhorn zu und richtet Waffenschaden an, was er nur durch Zufall
 * herausfände.
 */
class AbilityTriggerListenerTest {

    private ServerMock server;
    private PlayerMock player;
    private AbilityTriggerListener listener;
    private final List<String> triggered = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();

    private AbilityResult nextResult = AbilityResult.TRIGGERED;

    /** Die Werte, die der Runtime zu dieser Ablehnung mitgibt. */
    private java.util.Map<String, String> nextValues = java.util.Map.of();

    /** Was der Melder tatsächlich zu SEHEN bekam - Schlüssel und Werte. */
    private final List<java.util.Map<String, String>> notifiedValues = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("AbilityProbe");
        player = server.addPlayer();
        Logger logger = Logger.getLogger(AbilityTriggerListenerTest.class.getName());
        logger.setLevel(Level.OFF);
        listener =
                new AbilityTriggerListener(
                        (who, abilityId) -> {
                            triggered.add(abilityId);
                            return new AbilityTriggerListener.Outcome(nextResult, nextValues);
                        },
                        (who, key, values) -> {
                            notified.add(key.value());
                            notifiedValues.add(values);
                        },
                        logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Rechtsklick mit einem Fähigkeits-Item löst genau diese Fähigkeit aus")
    void aRightClickTriggers() {
        ItemStack item = abilityItem("probe.strike");

        listener.onInteract(interact(item, Action.RIGHT_CLICK_AIR));

        assertThat(triggered).containsExactly("probe.strike");
    }

    @Test
    @DisplayName("FR-054: ein Linksklick löst die Fähigkeit NICHT aus")
    void aLeftClickDoesNotTrigger() {
        ItemStack item = abilityItem("probe.strike");

        PlayerInteractEvent event = interact(item, Action.LEFT_CLICK_AIR);
        listener.onInteract(event);

        assertThat(triggered).isEmpty();
        assertThat(event.useItemInHand())
                .as("auch keine Item-Benutzung - das Item ist reine Eingabe")
                .isEqualTo(org.bukkit.event.Event.Result.DENY);
    }

    @Test
    @DisplayName("ein gewöhnliches Item wird nicht angefasst")
    void anOrdinaryItemIsUntouched() {
        // Nicht gegen isCancelled() geprüft: bei RIGHT_CLICK_AIR ohne Block ist das schon von Haus aus
        // wahr, und der Test prüfte dann Bukkits Vorgabe statt dieses Listeners. Verglichen wird
        // deshalb mit einem Kontrollereignis, das der Listener nie gesehen hat.
        PlayerInteractEvent untouched =
                interact(new ItemStack(Material.STONE), Action.RIGHT_CLICK_AIR);
        PlayerInteractEvent seen = interact(new ItemStack(Material.STONE), Action.RIGHT_CLICK_AIR);

        listener.onInteract(seen);

        assertThat(triggered).isEmpty();
        assertThat(seen.useItemInHand()).isEqualTo(untouched.useItemInHand());
    }

    @Test
    @DisplayName("die Offhand feuert kein zweites Mal - ein Druck ist eine Auslösung")
    void theOffHandDoesNotTriggerASecondTime() {
        ItemStack item = abilityItem("probe.strike");

        listener.onInteract(
                new PlayerInteractEvent(
                        player, Action.RIGHT_CLICK_AIR, item, null, null, EquipmentSlot.OFF_HAND));

        assertThat(triggered).isEmpty();
    }

    @Test
    @DisplayName("eine Ablehnung erreicht den Spieler mit ihrem Message-Schlüssel")
    void aRejectionReachesThePlayer() {
        nextResult = AbilityResult.ON_COOLDOWN;

        listener.onInteract(interact(abilityItem("probe.strike"), Action.RIGHT_CLICK_AIR));

        assertThat(notified).containsExactly("ability.rejected.on-cooldown");
    }

    @Test
    @DisplayName("die Ablehnung bringt ihre Werte mit - sonst liest der Spieler die Klammern")
    void aRejectionCarriesItsValues() {
        // Der Melder bekam frueher NUR den Schluessel. Der Text dahinter lautet "still on cooldown
        // for {seconds}s", und genau so stand er im Chat: mit den geschweiften Klammern. Ein
        // Platzhalter ohne Wert ist kein Schoenheitsfehler, sondern eine Zahl, die dem Spieler fehlt.
        nextResult = AbilityResult.ON_COOLDOWN;
        nextValues = java.util.Map.of("seconds", "3");

        listener.onInteract(interact(abilityItem("probe.strike"), Action.RIGHT_CLICK_AIR));

        assertThat(notifiedValues).containsExactly(java.util.Map.of("seconds", "3"));
    }

    @Test
    @DisplayName("ein Erfolg meldet nichts - auch nicht mit Werten")
    void aSuccessNotifiesNothing() {
        nextResult = AbilityResult.TRIGGERED;
        nextValues = java.util.Map.of("seconds", "3");

        listener.onInteract(interact(abilityItem("probe.strike"), Action.RIGHT_CLICK_AIR));

        assertThat(notified).isEmpty();
        assertThat(notifiedValues).isEmpty();
    }

    @Test
    @DisplayName("ein Erfolg meldet nichts - eine Fähigkeit, die wirkt, kommentiert sich nicht")
    void aSuccessSaysNothing() {
        listener.onInteract(interact(abilityItem("probe.strike"), Action.RIGHT_CLICK_AIR));

        assertThat(notified).isEmpty();
    }

    @Test
    @DisplayName("eine Ausnahme beim Auslösen wird eingefangen und nimmt die Sitzung nicht mit")
    void anExceptionIsContained() {
        AbilityTriggerListener failing =
                new AbilityTriggerListener(
                        (who, abilityId) -> {
                            throw new IllegalStateException("probe");
                        },
                        (who, key, values) -> notified.add(key.value()),
                        quietLogger());

        // Kein Wurf nach draußen: Prinzip VI verlangt, dass ein Fehler den Spieler nicht in einen
        // inkonsistenten Zustand versetzt.
        failing.onInteract(interact(abilityItem("probe.strike"), Action.RIGHT_CLICK_AIR));

        assertThat(notified).isEmpty();
    }

    // --- helpers ---

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(AbilityTriggerListenerTest.class.getName() + ".quiet");
        logger.setLevel(Level.OFF);
        return logger;
    }

    private PlayerInteractEvent interact(ItemStack item, Action action) {
        return new PlayerInteractEvent(player, action, item, null, null, EquipmentSlot.HAND);
    }

    private static ItemStack abilityItem(String abilityId) {
        ItemStack item = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = item.getItemMeta();
        AbilityItemTag.write(meta, abilityId);
        item.setItemMeta(meta);
        return item;
    }
}

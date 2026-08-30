package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.LadderSlot;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatSnapshot;
import rpg.core.ui.CharacterSheets;
import rpg.platform.ui.CharacterSheetListener;
import rpg.platform.ui.CharacterSheetMenu;
import rpg.platform.ui.ItemRenderer;
import rpg.platform.ui.MenuFrame;

/**
 * {@code /char} (T089) — der Aufrufweg, ohne den das Fenster für den Spieler nicht vorhanden wäre.
 *
 * <p>Vorläufig, wie {@code /coins}, {@code /stats} und {@code /top} (ADR-051). Rechtebaum,
 * Tab-Completion und die einheitliche Kommandostruktur bleiben B14.
 */
class CharacterSheetCommandTest {

    private ServerMock server;
    private PlayerMock player;
    private CharacterSheetCommand command;
    private CharacterSheetListener listener;

    private final UUID characterId = UUID.randomUUID();
    private volatile boolean hasCharacter = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer();

        Messages messages = messages();
        MenuFrame frame = new MenuFrame(messages);
        CharacterSheetMenu menu = new CharacterSheetMenu(frame, new StubRenderer());
        listener = new CharacterSheetListener(menu);
        command =
                new CharacterSheetCommand(
                        playerId -> hasCharacter ? Optional.of(characterId) : Optional.empty(),
                        sheets(),
                        menu,
                        listener,
                        messages);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("T089: /char ohne Argument oeffnet die Uebersicht")
    void charWithoutAnArgumentOpensTheSheet() {
        boolean handled = command.onCommand(player, null, "char", new String[0]);

        assertThat(handled).isTrue();
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(54);
        assertThat(listener.hasOpen(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("T089: von der Konsole tut es nichts - ein Inventar braucht einen Empfaenger")
    void fromTheConsoleItDoesNothing() {
        boolean handled =
                command.onCommand(server.getConsoleSender(), null, "char", new String[0]);

        assertThat(handled).as("behandelt, aber ohne Fenster").isTrue();
    }

    @Test
    @DisplayName("T089: ohne gewaehlten Charakter kommt eine Meldung statt Nullen")
    void withoutACharacterItSaysSo() {
        // FR-008: Nullen, die wie echte Werte aussehen, sind schlimmer als nichts - und in einem
        // Fenster, das man ausdruecklich oeffnet, noch mehr als auf einer Flaeche, die man streift.
        hasCharacter = false;

        command.onCommand(player, null, "char", new String[0]);

        assertThat(player.nextMessage()).contains("Pick a character");
        assertThat(listener.hasOpen(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("T089: Tab-Completion gibt LEER zurueck, nicht null")
    void tabCompletionIsEmptyNotNull() {
        // null laesst Bukkit auf die Spielernamen zurueckfallen - eine Vervollstaendigung fuer ein
        // Argument, das es nicht gibt. Und es gibt keines: die Uebersicht zeigt den AKTIVEN
        // Charakter, eine Summe ueber mehrere bildet sie nicht (FR-053).
        assertThat(command.onTabComplete(player, null, "char", new String[] {""})).isEmpty();
    }

    @Test
    @DisplayName("T089: ein zweiter Aufruf gibt dasselbe Fenster - der Zwischenspeicher greift")
    void asecondCallReusesTheWindow() {
        command.onCommand(player, null, "char", new String[0]);
        int firstSize = player.getOpenInventory().getTopInventory().getSize();
        player.closeInventory();

        command.onCommand(player, null, "char", new String[0]);

        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(firstSize);
    }

    // --- Aufbau ---------------------------------------------------------------

    private CharacterSheets sheets() {
        return new CharacterSheets(
                id -> Optional.of(new StatSnapshot(new double[Attribute.values().length], 1L)),
                new CharacterSheets.EquipmentSource() {

                    @Override
                    public Map<LadderSlot, rpg.core.classes.TierAppearance> equipmentOf(UUID id) {
                        return Map.of();
                    }

                    @Override
                    public Optional<String> tagOf(UUID id, LadderSlot slot) {
                        return Optional.of("test-tag");
                    }

                    @Override
                    public double conditionOf(UUID id, LadderSlot slot) {
                        return rpg.core.item.WearCurve.FULL;
                    }
                },
                id -> Optional.of(CharacterClass.WARRIOR),
                id -> 12,
                id -> 250L);
    }

    /** Zeichnet nichts — das Fenster ist hier nicht das Prüfobjekt. */
    private static final class StubRenderer implements ItemRenderer {

        @Override
        public Optional<org.bukkit.inventory.ItemStack> render(
                String templateKey, ItemRenderContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<org.bukkit.inventory.ItemStack> renderGear(
                LadderSlot slot,
                rpg.core.classes.TierAppearance appearance,
                String tag,
                double condition) {
            return Optional.empty();
        }
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
                        "no-character", "Pick a character first."));
        Map<String, Object> attribute = new HashMap<>();
        for (Attribute value : Attribute.values()) {
            attribute.put(value.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                    value.name());
        }
        ui.put("attribute", attribute);
        return MapMessages.fromNested(Map.of("ui", ui));
    }
}

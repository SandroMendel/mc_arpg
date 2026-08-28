package rpg.platform.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.currency.BookingReason;
import rpg.core.currency.LedgerEntry;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;

/**
 * T092b - das Fenster (US3 Szenarien 1a und 1b, US6 Szenario 3; FR-046a, FR-046b).
 *
 * <p>Der erste Test ist der, um den es geht: die Auswahl zeigt <b>drei Staende nebeneinander</b> und
 * keine Summe. Eine Summe ueber drei Geldbeutel waere eine Zahl, die es im Spiel nirgends gibt.
 */
class CurrencyMenuTest {

    private ServerMock server;
    private CurrencyMenu menu;

    private final UUID playerId = UUID.randomUUID();
    private PlayerCharacter warrior;
    private PlayerCharacter rogue;
    private PlayerCharacter mage;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        menu = new CurrencyMenu(messages(), 45);
        warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, Instant.EPOCH);
        rogue = PlayerCharacter.create(playerId, CharacterClass.ROGUE, Instant.EPOCH);
        mage = PlayerCharacter.create(playerId, CharacterClass.MAGE, Instant.EPOCH);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Auswahl zeigt drei Staende nebeneinander - NIE eine Summe (FR-046b)")
    void threeBalancesNeverASum() {
        Inventory selection =
                menu.buildSelection(
                        List.of(warrior, rogue, mage),
                        Map.of(
                                warrior.characterId(), 100L,
                                rogue.characterId(), 200L,
                                mage.characterId(), 300L));

        List<String> shown = displayNames(selection);

        assertThat(shown).hasSize(3);
        assertThat(shown).anyMatch(name -> name.contains("100"));
        assertThat(shown).anyMatch(name -> name.contains("200"));
        assertThat(shown).anyMatch(name -> name.contains("300"));
        assertThat(shown)
                .as("600 waere eine Zahl, die es im Spiel nicht gibt")
                .noneMatch(name -> name.contains("600"));
    }

    @Test
    @DisplayName("ein Klick auf einen Platz meint genau den Charakter, der dort steht")
    void aClickMeansThatCharacter() {
        List<PlayerCharacter> characters = List.of(warrior, rogue, mage);

        assertThat(menu.characterAt(characters, CurrencyMenu.CHARACTER_SLOTS[0])).hasValue(warrior);
        assertThat(menu.characterAt(characters, CurrencyMenu.CHARACTER_SLOTS[2])).hasValue(mage);
        assertThat(menu.characterAt(characters, 0))
                .as("ein leerer Platz meint niemanden")
                .isEmpty();
    }

    @Test
    @DisplayName("ein Charakter ohne Buchung bekommt eine Erklaerung, kein leeres Fenster")
    void anEmptyHistoryExplainsItself() {
        Inventory history = menu.buildHistory(warrior, List.of(), 0, 0L);

        assertThat(displayNames(history))
                .singleElement()
                .asString()
                .contains("No bookings yet");
    }

    @Test
    @DisplayName("auf der ersten Seite gibt es kein Zurueck")
    void thereIsNoBackOnTheFirstPage() {
        Inventory history = menu.buildHistory(warrior, entries(45), 0, 100L);

        assertThat(history.getItem(CurrencyMenu.PREVIOUS_SLOT)).isNull();
        assertThat(history.getItem(CurrencyMenu.NEXT_SLOT))
                .as("aber ein Vor, denn es gibt 100 Eintraege")
                .isNotNull();
    }

    @Test
    @DisplayName("auf der letzten Seite gibt es kein Vor")
    void thereIsNoForwardOnTheLastPage() {
        // 100 Eintraege, 45 je Seite: Seite 2 ist die letzte (90..99).
        Inventory history = menu.buildHistory(warrior, entries(10), 2, 100L);

        assertThat(history.getItem(CurrencyMenu.NEXT_SLOT)).isNull();
        assertThat(history.getItem(CurrencyMenu.PREVIOUS_SLOT)).isNotNull();
    }

    @Test
    @DisplayName("bei genau einer Seite gibt es gar keine Knoepfe")
    void asinglePageHasNoButtonsAtAll() {
        Inventory history = menu.buildHistory(warrior, entries(3), 0, 3L);

        assertThat(history.getItem(CurrencyMenu.PREVIOUS_SLOT)).isNull();
        assertThat(history.getItem(CurrencyMenu.NEXT_SLOT)).isNull();
    }

    @Test
    @DisplayName("die Eintraege stehen oberhalb der Navigationsreihe")
    void entriesStayAboveTheNavigationRow() {
        Inventory history = menu.buildHistory(warrior, entries(45), 0, 100L);

        for (int slot = 0; slot < CurrencyMenu.NAVIGATION_ROW; slot++) {
            assertThat(history.getItem(slot)).as("Platz " + slot).isNotNull();
        }
        for (int slot = CurrencyMenu.NAVIGATION_ROW + 1; slot < CurrencyMenu.NEXT_SLOT; slot++) {
            assertThat(history.getItem(slot))
                    .as("die Navigationsreihe traegt nur Knoepfe, Platz " + slot)
                    .isNull();
        }
    }

    @Test
    @DisplayName("ein Eintrag zeigt Stand davor und danach - das braucht der Betreiber")
    void anEntryShowsBothBalances() {
        LedgerEntry entry =
                new LedgerEntry(
                        1L,
                        warrior.characterId(),
                        Instant.parse("2026-08-22T12:00:00Z"),
                        40L,
                        LedgerEntry.Direction.DEBIT,
                        BookingReason.EQUIPMENT_TIER,
                        100L,
                        60L,
                        Optional.of("Sandro"));

        Inventory history = menu.buildHistory(warrior, List.of(entry), 0, 1L);
        var meta = history.getItem(0).getItemMeta();

        assertThat(meta.lore()).isNotNull();
        String lore =
                meta.lore().stream()
                        .map(
                                component ->
                                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                                .plainText()
                                                .serialize(component))
                        .reduce("", (a, b) -> a + " | " + b);
        assertThat(lore).contains("100 -> 60");
        assertThat(lore).as("und wer es war").contains("Sandro");
    }

    @Test
    @DisplayName("eine page-size, die die Navigationsreihe ueberschriebe, wird abgelehnt")
    void anOversizedPageSizeIsRefused() {
        assertThatThrownBy(() -> new CurrencyMenu(messages(), 54))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paging buttons");
    }

    // --- Hilfsmittel -----------------------------------------------------

    private List<LedgerEntry> entries(int count) {
        List<LedgerEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(
                    new LedgerEntry(
                            i + 1,
                            warrior.characterId(),
                            Instant.parse("2026-08-22T12:00:00Z"),
                            i + 1,
                            LedgerEntry.Direction.CREDIT,
                            BookingReason.PILE_PICKED_UP,
                            0L,
                            i + 1,
                            Optional.empty()));
        }
        return entries;
    }

    private static List<String> displayNames(Inventory inventory) {
        List<String> names = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (item == null || item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) {
                continue;
            }
            names.add(
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText()
                            .serialize(item.getItemMeta().displayName()));
        }
        return names;
    }

    private static Messages messages() {
        return new MapMessages(
                Map.of(
                        "currency.menu.title-characters", "Coins",
                        "currency.menu.title-history", "Coins - {character}",
                        "currency.menu.character-entry", "{character} - {amount} coins",
                        "currency.menu.history-entry", "{direction}{amount} - {reason}",
                        "currency.menu.page-next", "Next page",
                        "currency.menu.page-previous", "Previous page",
                        "currency.menu.empty", "No bookings yet."));
    }
}

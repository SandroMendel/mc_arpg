package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Period;
import rpg.core.statistics.ProfileSnapshot;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * <b>Ein Spieler, der heute nichts getan hat, sieht Nullen — keine Fehlermeldung und keine leere
 * Fläche.</b>
 *
 * <p>Der Fall trifft <em>jeden</em> Spieler jeden Tag mindestens einmal: beim ersten Anmelden ist
 * die Tagesstatistik leer. Wer dann eine Fehlermeldung sieht, hält den Block für kaputt; wer ein
 * leeres Fenster sieht, hält ihn für nicht fertig. Nullen sind die einzige Antwort, die stimmt und
 * auch so aussieht.
 *
 * <p>Und derselbe Test hält die Gegenrichtung fest: ein <b>fremdes</b> Profil trägt die drei
 * privaten Werte gar nicht erst — es kann sie also nicht versehentlich zeigen (FR-037).
 */
class EmptyDayShowsZerosTest {

    private Messages messages;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        messages = messages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein leerer Tag zeigt Nullen, keine Fehlermeldung")
    void anemptyDayShowsZeros() {
        ProfileSnapshot empty =
                new ProfileSnapshot.Builder().own(UUID.randomUUID(), Period.DAY);

        Inventory window = new StatisticsMenu(messages).build(empty, "Sandro");

        ItemStack firstLine = window.getItem(0);
        assertThat(firstLine).as("die Zeile ist da").isNotNull();
        assertThat(nameOf(firstLine)).contains(": 0");
    }

    @Test
    @DisplayName("jede oeffentliche Rangliste hat ihre Zeile, auch ohne Wert")
    void everyPublicBoardHasItsLine() {
        ProfileSnapshot empty =
                new ProfileSnapshot.Builder().own(UUID.randomUUID(), Period.DAY);

        Inventory window = new StatisticsMenu(messages).build(empty, "Sandro");

        long publicBoards =
                java.util.Arrays.stream(Aggregation.values())
                        .filter(board -> board.visibility() == rpg.core.statistics.MetricVisibility.PUBLIC)
                        .count();

        for (int slot = 0; slot < publicBoards; slot++) {
            assertThat(window.getItem(slot)).as("Zeile %d", slot).isNotNull();
        }
    }

    @Test
    @DisplayName("wer nirgends steht, liest das - statt einer leeren Zeile")
    void someoneUnrankedReadsSoInstead() {
        ProfileSnapshot empty =
                new ProfileSnapshot.Builder().own(UUID.randomUUID(), Period.DAY);

        Inventory window = new StatisticsMenu(messages).build(empty, "Sandro");

        assertThat(loreOf(window.getItem(0))).contains("not on this board yet");
    }

    @Test
    @DisplayName("FR-037 - ein fremdes Profil KANN die privaten Werte gar nicht tragen")
    void aforeignProfileCannotEvenCarryThePrivateValues() {
        // Der Riegel sitzt im Konstruktor des Schnappschusses, nicht in der Ansicht: schon der
        // Versuch, ein fremdes Profil mit privaten Werten zu bauen, scheitert.
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProfileSnapshot(
                                        UUID.randomUUID(),
                                        Period.DAY,
                                        Map.of(),
                                        Map.of(),
                                        Map.of("void", 3L),
                                        Map.of(),
                                        -1L,
                                        false))
                .withMessageContaining("FR-037");
    }

    @Test
    @DisplayName("FR-037 - und das fremde Fenster erklaert die Luecke, statt sie offen zu lassen")
    void andTheForeignWindowExplainsTheGap() {
        Inventory window =
                new StatisticsMenu(messages)
                        .build(ProfileSnapshot.foreign(UUID.randomUUID(), Period.DAY), "Jemand");

        assertThat(nameOf(window.getItem(StatisticsMenu.SLOT_PRIVATE_NOTE)))
                .as("eine Luecke ohne Erklaerung liest sich wie ein fehlender Wert")
                .contains("only visible to the player themselves");
    }

    private static String nameOf(ItemStack stack) {
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
    }

    private static String loreOf(ItemStack stack) {
        StringBuilder text = new StringBuilder();
        stack.getItemMeta()
                .lore()
                .forEach(line -> text.append(PlainTextComponentSerializer.plainText().serialize(line)));
        return text.toString();
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.PROFILE_TITLE.value(), "Your Record");
        texts.put(StatisticsMessageKeys.PROFILE_TITLE_OTHER.value(), "{player}'s Record");
        texts.put(StatisticsMessageKeys.PROFILE_LINE.value(), "{label}: {value}");
        texts.put(
                StatisticsMessageKeys.PROFILE_PRIVATE_OMITTED.value(),
                "Some details are only visible to the player themselves.");
        texts.put(StatisticsMessageKeys.LEADERBOARD_YOUR_RANK.value(), "Your place: #{rank}");
        texts.put(
                StatisticsMessageKeys.LEADERBOARD_UNRANKED.value(),
                "You are not on this board yet.");
        for (Aggregation board : Aggregation.values()) {
            texts.put(StatisticsMessageKeys.boardName(board).value(), board.key());
            texts.put(StatisticsMessageKeys.boardHint(board).value(), "hint");
        }
        return new MapMessages(texts);
    }
}

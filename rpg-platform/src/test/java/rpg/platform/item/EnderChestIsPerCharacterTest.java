package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.platform.inventory.PlayerInventoryContents;

/**
 * FR-074, FR-079 — <b>die Enderchest gehört dem Charakter, und es gibt nur eine Lagerung.</b>
 *
 * <p><b>B11 baut hier nichts.</b> B03 hat {@code CharacterInventory} mit einem eigenen Feld für die
 * Enderchest angelegt, B07 hat den Weg verdrahtet: beim Eintritt wiederhergestellt, beim Wechsel
 * geleert, beim Austritt aufgenommen. Das ist genau, was FR-074 verlangt — und die Aufgabe lautete
 * deshalb ausdrücklich <em>keine zweite Lagerung</em>.
 *
 * <p><b>Ein Test für etwas, das schon da ist, ist trotzdem kein leerer Test.</b> Was hier geprüft
 * wird, ist die Zusage, nicht der Code von gestern: dass Aufnehmen und Wiederherstellen sich
 * gegenseitig aufheben, dass ein Wechsel wirklich leert, und dass dieser Block keine zweite Fassung
 * davon mitbringt. Die letzte Prüfung ist die, die morgen etwas fängt.
 */
class EnderChestIsPerCharacterTest {

    private static final Logger QUIET = Logger.getLogger("ender-chest-test");
    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("was aufgenommen wurde, kommt wieder heraus")
    void whatIsCapturedComesBack() {
        player.getEnderChest().setItem(0, new ItemStack(Material.DIAMOND, 7));
        byte[] stored = PlayerInventoryContents.captureEnderChest(player, QUIET);

        // Was ein Charakterwechsel tut: leeren.
        player.getEnderChest().clear();
        assertThat(player.getEnderChest().getItem(0)).isNull();

        assertThat(PlayerInventoryContents.restoreEnderChest(player, stored, QUIET)).isTrue();
        assertThat(player.getEnderChest().getItem(0))
                .isEqualTo(new ItemStack(Material.DIAMOND, 7));
    }

    @Test
    @DisplayName("zwei Charaktere teilen KEINE Enderchest (ADR-011)")
    void twoCharactersShareNoEnderChest() {
        // Der erste Charakter legt etwas hinein.
        player.getEnderChest().setItem(0, new ItemStack(Material.DIAMOND, 7));
        byte[] first = PlayerInventoryContents.captureEnderChest(player, QUIET);

        // Der Wechsel, in der Reihenfolge, die der Aufrufer wirklich einhaelt: LEEREN, dann
        // wiederherstellen. restore raeumt ausdruecklich nicht selbst auf und laesst einen Slot in
        // Ruhe, fuer den der Blob nichts hat - sonst muesste es raten, was ein leerer Slot bedeutet.
        player.getEnderChest().clear();
        byte[] second = PlayerInventoryContents.captureEnderChest(player, QUIET);
        PlayerInventoryContents.restoreEnderChest(player, second, QUIET);

        assertThat(player.getEnderChest().getItem(0))
                .as("die Enderchest gehoert dem CHARAKTER, nicht dem Spieler (FR-074)")
                .isNull();

        // Und zurueck zum ersten - wieder mit dem Leeren davor.
        player.getEnderChest().clear();
        PlayerInventoryContents.restoreEnderChest(player, first, QUIET);
        assertThat(player.getEnderChest().getItem(0))
                .isEqualTo(new ItemStack(Material.DIAMOND, 7));
    }

    @Test
    @DisplayName("eine leere Enderchest ist ein gueltiger Stand, kein Fehler")
    void anemptyEnderChestIsAValidState() {
        byte[] stored = PlayerInventoryContents.captureEnderChest(player, QUIET);

        assertThat(stored).as("aufgenommen wird immer - auch nichts").isNotNull();
        // FALSCH heisst hier "nichts wiederhergestellt", nicht "fehlgeschlagen": ein leerer Blob
        // hat nichts zu setzen. Der erste Anlauf dieses Tests erwartete TRUE und hat damit eine
        // Zusage behauptet, die der Code nie gemacht hat.
        assertThat(PlayerInventoryContents.restoreEnderChest(player, stored, QUIET)).isFalse();
        assertThat(player.getEnderChest().getItem(0)).isNull();
    }

    @Test
    @DisplayName("B11 fuehrt KEINE zweite Lagerung ein (FR-079)")
    void thisBlockIntroducesNoSecondStorage() throws IOException {
        // Die Zeile, gegen die das steht, saehe naheliegend aus: eine eigene Kiste, eine eigene
        // Tabelle, ein eigenes "Lager". Und sie waere eine zweite Wahrheit darueber, was ein
        // Charakter besitzt - mit zwei Schreibern und zwei Zeitpunkten.
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            List<String> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("getEnderChest()")
                                                    || code.contains("CharacterInventory");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("die Lagerung gehoert B03 und ist von B07 verdrahtet - B11 benutzt sie nur")
                    .isEmpty();
        }
    }

    /** Kommentare weg — eine Erklärung ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}

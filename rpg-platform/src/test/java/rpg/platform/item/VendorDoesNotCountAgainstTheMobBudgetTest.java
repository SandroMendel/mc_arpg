package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.platform.mob.MobKindTag;

/**
 * FR-059 — <b>Sechs Händler verkleinern die Horde nicht.</b>
 *
 * <p>B10 vergibt ein Budget an Kreaturen je Region. Zählte ein Händler dagegen, hätte jede Region
 * eine Kreatur weniger, weil dort jemand einkaufen kann — und es wäre der Fall, den niemand meldet,
 * weil er sich als „hier spawnt weniger als früher" äußert und nicht als Fehler.
 *
 * <p><b>Warum das ohne eine einzige Ausnahme in B10 funktioniert:</b> B10 zählt, was B10 gesetzt
 * hat. Ein Eintrag entsteht in {@code HordeRegistry} und nirgends sonst; wer dort nichts einträgt,
 * ist nicht Teil des Budgets (research.md R7). Das ist die bessere Bauart als eine Liste von
 * Ausnahmen — eine Ausnahmeliste muss gepflegt werden, diese Eigenschaft nicht.
 *
 * <p>Deshalb prüft dieser Test <em>zwei</em> Dinge: dass der gesetzte Händler keinen Artvermerk
 * trägt (also von B10s Aufräum- und Zählwegen gar nicht gesehen wird), und dass dieser Block B10s
 * Buchführung nirgends anfasst.
 */
class VendorDoesNotCountAgainstTheMobBudgetTest {

    private static final Logger QUIET =
            Logger.getLogger(VendorDoesNotCountAgainstTheMobBudgetTest.class.getName());

    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der gesetzte Haendler traegt KEINEN Artvermerk - B10 sieht ihn gar nicht")
    void thePlacedVendorCarriesNoMobKind() {
        Entity npc = place("greenfields");

        assertThat(MobKindTag.kindOf(npc))
                .as(
                        "ohne Artvermerk ist er fuer B10 keine Kreatur - weder zum Zaehlen noch zum"
                                + " Aufraeumen (FR-059)")
                .isEmpty();
    }

    @Test
    @DisplayName("er traegt stattdessen seine Region - daraus folgt sein Bestand")
    void heCarriesHisZoneInstead() {
        Entity npc = place("pale-wilds");

        assertThat(VendorNpc.zoneOf(npc)).contains("pale-wilds");
        assertThat(VendorNpc.isVendor(npc)).isTrue();
    }

    @Test
    @DisplayName("und eine gewoehnliche Kreatur ist kein Haendler")
    void anOrdinaryCreatureIsNoVendor() {
        Entity creature = world.spawn(new Location(world, 0, 64, 0), org.bukkit.entity.Zombie.class);

        assertThat(VendorNpc.isVendor(creature))
                .as("sonst oeffnete jeder Zombie ein Haendlerfenster")
                .isFalse();
    }

    @Test
    @DisplayName("dieser Block traegt NIRGENDS in B10s Buchfuehrung ein")
    void thisBlockNeverWritesIntoTheHordeRegistry() throws IOException {
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("HordeRegistry")
                                                    || code.contains("MobKindTag.mark(");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as(
                            "ein Eintrag hier waere ein Haendler, der gegen das Budget zaehlt - und"
                                    + " den B10 beim naechsten Aufraeumen entfernt")
                    .isEmpty();
        }
    }

    private Entity place(String zoneKey) {
        return new VendorNpc(QUIET)
                .place(new Location(world, 0, 64, 0), zoneKey)
                .orElseThrow(() -> new AssertionError("der Haendler wurde nicht gesetzt"));
    }

    /** Kommentare weg, bevor gesucht wird — nach dem Muster von {@code SourceGuard} in B11s Kern. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}

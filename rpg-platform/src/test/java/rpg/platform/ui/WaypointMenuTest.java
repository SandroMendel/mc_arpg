package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.Area;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.Cuboid;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SafeCore;
import rpg.core.zone.WaypointCrystal;
import rpg.core.zone.Zone;

/**
 * T112, T113 - das Fenster zeigt alle Kristalle, und die gesperrten sind lesbar (FR-048, FR-048a,
 * FR-049, SC-026).
 *
 * <p>Der erste Test ist der, um den es geht: <b>alle sechs erscheinen</b>. Ein Fenster, das nur die
 * schon entdeckten Ziele zeigt, verraet einem neuen Spieler nicht, dass es die Welt gibt.
 */
class WaypointMenuTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private WaypointMenu menu;
    private List<CrystalPlacement> crystals;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        menu = new WaypointMenu(messages(), new MenuFrame(messages()));
        crystals = sixRegions();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("alle sechs erscheinen, auch die noch gesperrten (FR-048)")
    void allSixAppear() {
        Inventory inventory = menu.build(crystals, Set.of("greenfields-crystal"), null);

        List<String> shown = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            shown.add(nameAt(inventory, slot));
        }

        assertThat(shown)
                .hasSize(6)
                .allSatisfy(name -> assertThat(name).isNotEmpty());
    }

    @Test
    @DisplayName("der gesperrte Eintrag traegt Name und Levelband seiner Region (FR-048a, SC-026)")
    void alockedEntryCarriesNameAndBand() {
        Inventory inventory = menu.build(crystals, Set.of(), null);

        // Die Pale Wilds stehen zuletzt, weil nach Levelband sortiert wird.
        String last = nameAt(inventory, 5);

        assertThat(last)
                .as("Name aus zone.<key>.name, kein zweiter Ort fuer Zonennamen (FR-048b)")
                .contains("The Pale Wilds")
                .contains("51")
                .contains("60")
                .contains("not discovered");
    }

    @Test
    @DisplayName("gesperrt und freigeschaltet sind auf einen Blick zu unterscheiden")
    void lockedAndUnlockedLookDifferent() {
        Inventory inventory = menu.build(crystals, Set.of("greenfields-crystal"), null);

        assertThat(inventory.getItem(0)).isNotNull();
        assertThat(inventory.getItem(0).getType()).isEqualTo(Material.AMETHYST_SHARD);
        assertThat(inventory.getItem(1).getType())
                .as("eine graue Scheibe liest sich als gesperrt, ohne dass man den Text liest")
                .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    @DisplayName("der eigene Standort erscheint als gewaehlt, nicht als Ziel (T113)")
    void whereYouAreIsNotADestination() {
        Inventory inventory =
                menu.build(
                        crystals,
                        Set.of("greenfields-crystal", "dustlands-crystal"),
                        "dustlands-crystal");

        assertThat(inventory.getItem(1).getType()).isEqualTo(Material.AMETHYST_CLUSTER);
        assertThat(nameAt(inventory, 1)).contains("you are here");
        assertThat(inventory.getItem(0).getType())
                .as("der andere freigeschaltete bleibt ein Ziel")
                .isEqualTo(Material.AMETHYST_SHARD);
    }

    @Test
    @DisplayName("die Reihenfolge folgt dem Levelband, nicht der Konfigurationsreihenfolge")
    void theOrderFollowsTheLevelBands() {
        List<CrystalPlacement> shuffled = new ArrayList<>(crystals);
        java.util.Collections.reverse(shuffled);

        assertThat(WaypointMenu.order(shuffled))
                .extracting(placement -> placement.crystal().key())
                .containsExactly(
                        "greenfields-crystal",
                        "dustlands-crystal",
                        "safari-plains-crystal",
                        "terracotta-canyons-crystal",
                        "darkforest-crystal",
                        "pale-wilds-crystal");
    }

    @Test
    @DisplayName("ein Klick auf einen leeren Platz meint keinen Kristall")
    void aclickOnNothingMeansNothing() {
        assertThat(menu.crystalAt(crystals, 8)).isEmpty();
        assertThat(menu.crystalAt(crystals, -1)).isEmpty();
        assertThat(menu.crystalAt(crystals, 0))
                .map(placement -> placement.crystal().key())
                .contains("greenfields-crystal");
    }

    @Test
    @DisplayName("das Fenster waechst in vollen Reihen mit der Zahl der Kristalle")
    void thewindowGrowsInWholeRows() {
        assertThat(WaypointMenu.size(6)).isEqualTo(9);
        assertThat(WaypointMenu.size(9)).isEqualTo(9);
        assertThat(WaypointMenu.size(10)).isEqualTo(18);
        assertThat(WaypointMenu.size(0)).as("nie ein Fenster ohne Reihe").isEqualTo(9);
        assertThat(WaypointMenu.size(200))
                .as("eine Kiste hat sechs Reihen, und mehr gibt Bukkit nicht her")
                .isEqualTo(54);
    }

    // --- helpers ---------------------------------------------------------

    private static String nameAt(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        assertThat(item).as("Platz " + slot).isNotNull();
        Component name = item.getItemMeta().displayName();
        assertThat(name).as("Platz " + slot).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static List<CrystalPlacement> sixRegions() {
        List<CrystalPlacement> placements = new ArrayList<>();
        placements.add(region("greenfields", 0, 1, 10));
        placements.add(region("dustlands", 1500, 11, 20));
        placements.add(region("safari-plains", 3000, 21, 30));
        placements.add(region("terracotta-canyons", 4500, 31, 40));
        placements.add(region("darkforest", 6000, 41, 50));
        placements.add(region("pale-wilds", 7500, 51, 60));
        return placements;
    }

    private static CrystalPlacement region(String key, int centreX, int bandMin, int bandMax) {
        WaypointCrystal crystal =
                new WaypointCrystal(
                        key + "-crystal",
                        Area.of(Cuboid.of(centreX - 2, 64, -2, centreX + 2, 67, 2)),
                        25L);
        Zone zone =
                new Zone(
                        key,
                        WORLD,
                        Area.of(Cuboid.of(centreX - 500, -500, centreX + 500, 500)),
                        new LevelBand(bandMin, bandMax),
                        Optional.of(
                                new SafeCore(
                                        Area.of(Cuboid.of(centreX - 60, -60, centreX + 60, 60)),
                                        new WorldPosition(WORLD, centreX + 0.5d, 65.0d, 0.5d))),
                        List.of(),
                        Optional.of(crystal),
                        false,
                        bandMin == 1);
        return new CrystalPlacement(crystal, zone);
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put("zone.waypoint.menu-title", "Waypoints");
        texts.put("zone.waypoint.entry-unlocked", "{zone} - level {band-min}-{band-max} ({price} coins)");
        texts.put("zone.waypoint.entry-locked", "{zone} - level {band-min}-{band-max} (not discovered)");
        texts.put("zone.waypoint.entry-here", "{zone} - you are here");
        texts.put("zone.greenfields.name", "The Greenfields");
        texts.put("zone.dustlands.name", "The Dustlands");
        texts.put("zone.safari-plains.name", "The Safari Plains");
        texts.put("zone.terracotta-canyons.name", "The Terracotta Canyons");
        texts.put("zone.darkforest.name", "The Darkforest");
        texts.put("zone.pale-wilds.name", "The Pale Wilds");
        return new MapMessages(texts);
    }
}

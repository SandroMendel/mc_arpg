package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.Area;
import rpg.core.zone.Cuboid;
import rpg.core.zone.DefaultZones;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SafeCore;
import rpg.core.zone.WaypointCrystal;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneCharacterState;
import rpg.core.zone.ZoneConfig;
import rpg.core.zone.ZoneStateRepository;
import rpg.core.zone.ZoneStateStore;
import rpg.core.zone.Zones;

/**
 * T107, T108 - der erste Rechtsklick schaltet frei, jeder weitere oeffnet, und eine gedrueckt
 * gehaltene Maustaste oeffnet trotzdem nur einmal (FR-047, FR-048, Prinzip VI).
 *
 * <p>Ohne die Ratensperre waere ein festgehaltener Rechtsklick ein Fenster je Tick - der Spieler
 * saehe ein Inventar, das sich schneller neu aufbaut, als er darin klicken kann. Das ist kein
 * Schoenheitsfehler, sondern ein Zustand, aus dem man nicht mehr herauskommt.
 */
class InteractRateLimitTest {

    private static final UUID CHARACTER = UUID.randomUUID();

    private ServerMock server;
    private WorldMock world;
    private Player player;
    private Block crystalBlock;
    private Block elsewhere;

    private final List<String> opened = new ArrayList<>();
    private final long[] now = {1_000_000L};
    private ZoneStateStore store;
    private CrystalInteractListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        player.teleport(new org.bukkit.Location(world, 0.5d, 65.0d, 0.5d));
        crystalBlock = world.getBlockAt(0, 65, 0);
        elsewhere = world.getBlockAt(400, 65, 400);

        store = new ZoneStateStore(new SilentRepository());
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));

        Zones zones = new DefaultZones(config(world.getUID()));
        listener =
                new CrystalInteractListener(
                        () -> zones,
                        store,
                        playerId -> Optional.of(CHARACTER),
                        (who, placement) -> opened.add(placement.crystal().key()),
                        messages(),
                        () -> now[0]);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der erste Rechtsklick schaltet frei und oeffnet KEIN Fenster (FR-047)")
    void thefirstClickOnlyUnlocks() {
        rightClick(crystalBlock);

        assertThat(store.isUnlocked(CHARACTER, "greenfields-crystal")).isTrue();
        assertThat(opened)
                .as("die Entdeckung wuerde sonst hinter einem Inventar verschwinden")
                .isEmpty();
    }

    @Test
    @DisplayName("jeder weitere Rechtsklick oeffnet das Fenster (FR-048)")
    void everyFurtherClickOpens() {
        rightClick(crystalBlock);
        now[0] += 1_000L;

        rightClick(crystalBlock);

        assertThat(opened).containsExactly("greenfields-crystal");
    }

    @Test
    @DisplayName("mehrere Rechtsklicks in Folge oeffnen das Fenster nicht mehrfach (T107)")
    void aheldButtonOpensOnce() {
        rightClick(crystalBlock);
        now[0] += 1_000L;

        // Eine gedrueckt gehaltene Maustaste: Bukkit feuert das Ereignis mehrmals je Sekunde,
        // hier zwei Sekunden lang. Mit einem festen Zeitfenster waere danach viermal ein Fenster
        // aufgegangen - deshalb schiebt jeder Klick die Frist weiter, auch ein abgelehnter.
        for (int repeat = 0; repeat < 40; repeat++) {
            now[0] += 50L;
            rightClick(crystalBlock);
        }

        assertThat(opened).hasSize(1);
    }

    @Test
    @DisplayName("nach der Sperrzeit oeffnet es wieder - sie blockiert nicht dauerhaft")
    void thelimitExpires() {
        rightClick(crystalBlock);
        now[0] += 1_000L;
        rightClick(crystalBlock);

        now[0] += CrystalInteractListener.OPEN_COOLDOWN_MILLIS + 1L;
        rightClick(crystalBlock);

        assertThat(opened).hasSize(2);
    }

    @Test
    @DisplayName("ein Rechtsklick ohne Kristall tut nichts und bricht nichts ab")
    void aclickElsewhereIsUntouched() {
        PlayerInteractEvent event = event(elsewhere, EquipmentSlot.HAND);
        listener.onInteract(event);

        assertThat(opened).isEmpty();
        assertThat(store.unlockedBy(CHARACTER)).isEmpty();
        assertThat(event.useInteractedBlock())
                .as("jede Tuer und jede Kiste laeuft hier durch - der Block darf nichts anfassen")
                .isNotEqualTo(org.bukkit.event.Event.Result.DENY);
    }

    @Test
    @DisplayName("die Nebenhand feuert ein zweites Ereignis und wird ignoriert")
    void theOffHandIsIgnored() {
        listener.onInteract(event(crystalBlock, EquipmentSlot.OFF_HAND));

        assertThat(store.unlockedBy(CHARACTER))
                .as("sonst schaltet ein Druck frei und oeffnet in derselben Bewegung")
                .isEmpty();
    }

    @Test
    @DisplayName("das Vergessen beim Verlassen loescht die Sperre des Spielers")
    void leavingClearsTheLimit() {
        rightClick(crystalBlock);
        now[0] += 1_000L;
        rightClick(crystalBlock);

        listener.forget(player.getUniqueId());
        rightClick(crystalBlock);

        assertThat(opened)
                .as("die Karte darf nicht die ganze Serverlaufzeit lang wachsen")
                .hasSize(2);
    }

    // --- helpers ---------------------------------------------------------

    private void rightClick(Block block) {
        listener.onInteract(event(block, EquipmentSlot.HAND));
    }

    private PlayerInteractEvent event(Block block, EquipmentSlot hand) {
        return new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, hand);
    }

    private static ZoneConfig config(UUID worldId) {
        WaypointCrystal crystal =
                new WaypointCrystal(
                        "greenfields-crystal", Area.of(Cuboid.of(-2, 64, -2, 2, 67, 2)), 25L);
        Zone zone =
                new Zone(
                        "greenfields",
                        worldId,
                        Area.of(Cuboid.of(-500, -500, 500, 500)),
                        new LevelBand(1, 10),
                        Optional.of(
                                new SafeCore(
                                        Area.of(Cuboid.of(-60, -60, 60, 60)),
                                        new WorldPosition(worldId, 0.5d, 65.0d, 0.5d))),
                        List.of(),
                        Optional.of(crystal),
                        false,
                        true);
        return new ZoneConfig(
                false,
                new WorldPosition(worldId, 0.5d, 64.0d, 0.5d),
                List.of(zone),
                Duration.ofSeconds(30),
                true);
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put("zone.waypoint.unlocked", "Waypoint discovered: {zone}.");
        texts.put("zone.greenfields.name", "The Greenfields");
        return new MapMessages(texts);
    }

    /** Nimmt alles an und merkt sich nichts - Persistenz hat eigene Tests. */
    private static final class SilentRepository implements ZoneStateRepository {

        @Override
        public CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markSeen(UUID characterId) {}

        @Override
        public void unlock(UUID characterId, String crystalKey) {}

        @Override
        public void setPendingRespawn(UUID characterId, String zoneKey) {}
    }
}

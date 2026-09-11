package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/**
 * T124 - die Werte dieses Blocks sind unveraenderlich.
 *
 * <p>Nicht Formsache. Der Index wird beim Start einmal gebaut und danach von jedem Bewegungsereignis
 * gelesen, also von vielen Threads ohne jede Sperre (Prinzip I). Das ist nur dann richtig, wenn
 * nichts daran sich nachtraeglich aendern laesst - und eine {@code List}, die man von aussen
 * weiterreicht, ist genau die Luecke, die man nicht sieht: der Datensatz sieht unveraenderlich aus,
 * und die Liste darin ist es nicht.
 */
class ZoneImmutabilityTest {

    private static final UUID WORLD = ZoneFixture.WORLD;

    @Test
    @DisplayName("alle Werttypen des Blocks sind Records - und damit final")
    void everyValueTypeIsARecord() {
        List<Class<?>> values =
                List.of(
                        Cuboid.class,
                        Area.class,
                        Zone.class,
                        SafeCore.class,
                        SpawnArea.class,
                        WaypointCrystal.class,
                        LevelBand.class,
                        CrystalPlacement.class,
                        ZoneCharacterState.class,
                        ZoneChangedEvent.class,
                        SafeAreaCrossedEvent.class);

        assertThat(values)
                .allSatisfy(
                        type -> {
                            assertThat(type.isRecord()).as(type.getSimpleName()).isTrue();
                            assertThat(Modifier.isFinal(type.getModifiers()))
                                    .as(type.getSimpleName())
                                    .isTrue();
                        });
    }

    @Test
    @DisplayName("eine Flaeche kopiert ihre Quader - die uebergebene Liste geht sie nichts mehr an")
    void anareaCopiesItsParts() {
        List<Cuboid> mutable = new ArrayList<>();
        mutable.add(Cuboid.of(0, 0, 10, 10));
        Area area = new Area(mutable);

        mutable.add(Cuboid.of(100, 100, 200, 200));

        assertThat(area.parts()).as("die Flaeche ist nicht mitgewachsen").hasSize(1);
        assertThatThrownBy(() -> area.parts().add(Cuboid.of(0, 0, 1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("eine Zone kopiert ihre Spawn-Bereiche")
    void azoneCopiesItsSpawnAreas() {
        List<SpawnArea> mutable = new ArrayList<>();
        mutable.add(new SpawnArea("east", Area.of(Cuboid.of(20, 20, 30, 30))));
        Zone zone = zoneWith(mutable);

        mutable.add(new SpawnArea("west", Area.of(Cuboid.of(-30, -30, -20, -20))));

        assertThat(zone.spawnAreas()).hasSize(1);
        assertThatThrownBy(() -> zone.spawnAreas().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("die Freischaltungen eines Charakters lassen sich nicht von aussen erweitern")
    void thecharacterStateCopiesItsUnlocks() {
        java.util.Set<String> mutable = new java.util.HashSet<>();
        mutable.add("greenfields-crystal");
        ZoneCharacterState state =
                new ZoneCharacterState(UUID.randomUUID(), mutable, Optional.empty());

        mutable.add("pale-wilds-crystal");

        assertThat(state.unlockedCrystals()).containsExactly("greenfields-crystal");
        assertThatThrownBy(() -> state.unlockedCrystals().add("darkforest-crystal"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("was die Abfrage herausgibt, ist ebenfalls nicht zu veraendern")
    void thequeryHandsOutNothingMutable() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        assertThatThrownBy(() -> zones.all().clear())
                .as("sonst leert ein Aufrufer den Index fuer alle anderen mit")
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> zones.crystals().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> zones.spawnAreasOf("greenfields").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("ein Quader haelt nur Zahlen und kann sich nicht aendern")
    void acuboidIsSixNumbers() {
        Cuboid box = Cuboid.of(0, 0, 10, 10);

        assertThat(box).isEqualTo(Cuboid.of(0, 0, 10, 10));
        assertThat(box.getClass().getDeclaredFields())
                .allSatisfy(
                        field ->
                                assertThat(Modifier.isFinal(field.getModifiers()))
                                        .as(field.getName())
                                        .isTrue());
    }

    private static Zone zoneWith(List<SpawnArea> spawnAreas) {
        return new Zone(
                "greenfields",
                WORLD,
                Area.of(Cuboid.of(-100, -100, 100, 100)),
                new LevelBand(1, 10),
                Optional.of(
                        new SafeCore(
                                Area.of(Cuboid.of(-10, -10, 10, 10)),
                                new WorldPosition(WORLD, 0.5d, 65.0d, 0.5d))),
                spawnAreas,
                Optional.empty(),
                false,
                true);
    }
}

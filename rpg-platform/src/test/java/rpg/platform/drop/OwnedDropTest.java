package rpg.platform.drop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Die Eigentumsmechanik für liegende Gegenstände — dieselbe, die B08b für Coin-Haufen benutzt, seit
 * ADR-039 aus {@code rpg.platform.currency} hierher gehoben (research.md R5).
 *
 * <p><b>Warum die Plattform als Naht getestet wird und nicht direkt.</b> MockBukkit implementiert
 * weder {@code Item.setOwner} noch {@code Entity.setVisibleByDefault} und meldet einen nicht
 * implementierten Aufruf als <em>übersprungenen</em> Test statt als Fehler. Ein Test, der es direkt
 * versuchte, wäre still übersprungen — und der Build sagte trotzdem SUCCESSFUL. Deshalb schreibt
 * ein Mitschreiber auf, <b>was verlangt wird und über wen</b>; dass Paper es einhält, beweist der
 * echte Server (quickstart.md Abschnitt 3, Schritte 13–17).
 */
class OwnedDropTest {

    private ServerMock server;
    private WorldMock world;
    private RecordingPlatform platform;
    private OwnedDropRegistry registry;
    private OwnedDrops drops;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        platform = new RecordingPlatform();
        registry = new OwnedDropRegistry(platform);
        drops = new OwnedDrops(platform, registry, 1200);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Sichtbarkeit")
    class Visibility {

        @Test
        @DisplayName("erst vor allen verstecken, DANN dem Berechtigten zeigen")
        void hiddenFirstThenShown() {
            Player owner = server.addPlayer();
            UUID characterId = UUID.randomUUID();

            drops.drop(new ItemStack(Material.POTION), spot(), characterId, owner);

            assertThat(platform.order)
                    .as(
                            "andersherum waere der Gegenstand fuer einen Tick fuer alle sichtbar -"
                                    + " und auf einem vollen Server ist ein Tick genug")
                    .startsWith("hide", "show");
            assertThat(platform.shownTo).containsExactly(owner.getUniqueId());
        }

        @Test
        @DisplayName("ein zweiter Spieler bekommt nichts gezeigt")
        void aSecondPlayerSeesNothing() {
            Player owner = server.addPlayer();
            Player other = server.addPlayer();
            UUID characterId = UUID.randomUUID();

            drops.drop(new ItemStack(Material.POTION), spot(), characterId, owner);

            assertThat(platform.shownTo).doesNotContain(other.getUniqueId());
        }

        @Test
        @DisplayName("ein offline Eigentuemer ist kein Fehler - der Gegenstand bleibt unsichtbar")
        void anOfflineOwnerIsOrdinary() {
            UUID characterId = UUID.randomUUID();

            Optional<Item> dropped =
                    drops.drop(new ItemStack(Material.POTION), spot(), characterId, null);

            assertThat(dropped).isPresent();
            assertThat(platform.order).contains("hide");
            assertThat(platform.shownTo)
                    .as("er verfaellt ungesehen - das ist ein gewoehnlicher Ausgang")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Anspruch")
    class Ownership {

        @Test
        @DisplayName("der Vermerk haengt am CHARAKTER, nicht am Spieler")
        void theClaimIsOnTheCharacter() {
            Player owner = server.addPlayer();
            UUID characterId = UUID.randomUUID();

            Item drop =
                    drops.drop(new ItemStack(Material.POTION), spot(), characterId, owner)
                            .orElseThrow();

            assertThat(drops.ownerOf(drop.getUniqueId())).contains(characterId);
            assertThat(drops.ownerOf(drop.getUniqueId()).orElseThrow())
                    .as("und eben NICHT die Spielerkennung - ein Spieler hat bis zu drei (ADR-011)")
                    .isNotEqualTo(owner.getUniqueId());
        }

        @Test
        @DisplayName("ein fremder Gegenstand ist unbekannt - nicht null, nicht geraten")
        void aForeignItemIsUnknown() {
            assertThat(drops.ownerOf(UUID.randomUUID())).isEmpty();
            assertThat(drops.ownerOf(null)).isEmpty();
        }

        @Test
        @DisplayName("die Vanilla-Haertung wird verlangt - Eigentum, kein Mob-Aufsammeln, Voralterung")
        void theVanillaHardeningIsRequested() {
            Player owner = server.addPlayer();

            drops.drop(new ItemStack(Material.POTION), spot(), UUID.randomUUID(), owner);

            assertThat(platform.hardened)
                    .as("Haertung obendrauf - sie ersetzt das Schloss nicht, sie erspart Versuche")
                    .isEqualTo(1);
            assertThat(platform.spawnTicksLived).isEqualTo(1200);
        }
    }

    @Nested
    @DisplayName("Relogin")
    class Relogin {

        @Test
        @DisplayName("nach dem Wiederkommen sieht der Eigentuemer seine Sachen erneut")
        void visibilityIsRestored() {
            Player owner = server.addPlayer();
            UUID characterId = UUID.randomUUID();
            drops.drop(new ItemStack(Material.POTION), spot(), characterId, owner);
            platform.reset();

            // Das ist, was ein Relogin tut: showEntity haengt an der Verbindung und ist weg.
            drops.restoreVisibility(owner, characterId);

            assertThat(platform.shownTo)
                    .as("unsichtbar aber aufsammelbar waere das Schlechteste von beidem")
                    .containsExactly(owner.getUniqueId());
        }

        @Test
        @DisplayName("und der ZWEITE Charakter desselben Spielers sieht sie nicht")
        void theOtherCharacterOfTheSamePlayerSeesNothing() {
            Player owner = server.addPlayer();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            drops.drop(new ItemStack(Material.POTION), spot(), first, owner);
            platform.reset();

            drops.restoreVisibility(owner, second);

            assertThat(platform.shownTo)
                    .as("ein Gegenstand gehoert dem Charakter, der ihn verdient hat (ADR-011)")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Verschmelzen")
    class Merging {

        @Test
        @DisplayName("zwei Gegenstaende verschiedener Eigentuemer bleiben getrennt vermerkt")
        void twoOwnersStayApart() {
            Player first = server.addPlayer();
            Player second = server.addPlayer();
            UUID characterA = UUID.randomUUID();
            UUID characterB = UUID.randomUUID();

            Item a = drops.drop(new ItemStack(Material.POTION), spot(), characterA, first).orElseThrow();
            Item b = drops.drop(new ItemStack(Material.POTION), spot(), characterB, second).orElseThrow();

            // Gleiche Vorlage, gleicher Ort - genau die Lage, in der Vanilla zusammenfuehren wuerde.
            // Der Vermerk haelt sie auseinander, und der Besitz wechselt nicht durch blosse Naehe.
            assertThat(a.getUniqueId()).isNotEqualTo(b.getUniqueId());
            assertThat(drops.ownerOf(a.getUniqueId())).contains(characterA);
            assertThat(drops.ownerOf(b.getUniqueId())).contains(characterB);
        }
    }

    private Location spot() {
        return new Location(world, 0.5, 65.0, 0.5);
    }

    /** Schreibt auf, was verlangt wird — MockBukkit kann die Aufrufe selbst nicht. */
    private static final class RecordingPlatform implements OwnedDropPlatform {

        private final List<String> order = new ArrayList<>();
        private final List<UUID> shownTo = new ArrayList<>();
        private int hardened;
        private int spawnTicksLived = -1;

        @Override
        public void hideFromEveryone(Item drop) {
            order.add("hide");
        }

        @Override
        public void showTo(Item drop, Player player) {
            order.add("show");
            shownTo.add(player.getUniqueId());
        }

        @Override
        public void harden(Item drop, UUID ownerId, int ticksLived) {
            order.add("harden");
            hardened++;
            this.spawnTicksLived = ticksLived;
        }

        void reset() {
            order.clear();
            shownTo.clear();
        }
    }
}

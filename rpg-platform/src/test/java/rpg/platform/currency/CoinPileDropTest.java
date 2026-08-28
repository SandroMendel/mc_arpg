package rpg.platform.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.currency.CoinDropPlan;
import rpg.core.currency.CurrencyConfig;
import rpg.core.progression.WorldPoint;

/**
 * T059, T063b - was aus einem Plan in der Welt wird.
 *
 * <p>Geprueft wird das Verhalten, das Vanilla <b>nicht</b> mitbringt: das Zusammenlegen vor dem
 * Erzeugen (FR-028) und die Sichtbarkeit je Berechtigtem (FR-027a).
 *
 * <p><b>Warum ein Rekorder statt der echten Plattform.</b> MockBukkit implementiert weder
 * {@code ItemMock.setOwner} noch {@code EntityMock.setVisibleByDefault} - und meldet einen solchen
 * Aufruf als <em>uebersprungen</em>, nicht als Fehler. Ohne diese Naht waeren saemtliche Tests dieser
 * Klasse stillschweigend uebersprungen und der Build haette trotzdem SUCCESSFUL gemeldet: ein gruener
 * Lauf, der nichts bewiesen hat.
 *
 * <p>Was hier bewiesen wird, ist deshalb <b>was wir verlangen und wem gegenueber</b>. Dass Paper es
 * befolgt, zeigt der Durchlauf auf einem echten Server (quickstart.md 3.1).
 */
class CoinPileDropTest {

    private static final Logger QUIET = Logger.getLogger("coin-pile-drop-test");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC);

    private ServerMock server;
    private World world;
    private CoinPile piles;
    private RecordingPlatform platform;
    private WorldPoint origin;

    private final UUID aliceCharacter = UUID.randomUUID();
    private final UUID bobCharacter = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        platform = new RecordingPlatform();
        piles =
                new CoinPile(
                        MockBukkit.createMockPlugin("PileOwner"),
                        server,
                        config(),
                        CLOCK,
                        QUIET,
                        platform);
        origin = new WorldPoint(world.getUID(), 0.5, 64.0, 0.5);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Plan wird ein Entity, das Betrag und Berechtigten traegt")
    void aPlanBecomesATaggedEntity() {
        PlayerMock alice = server.addPlayer();

        Optional<Item> pile = piles.drop(plan(alice, aliceCharacter, 500L), () -> true);

        assertThat(pile).isPresent();
        assertThat(CoinPileTag.amountOf(pile.get().getItemStack())).hasValue(500L);
        assertThat(CoinPileTag.characterOf(pile.get().getItemStack())).hasValue(aliceCharacter);
        assertThat(CoinPile.isPile(pile.get())).isTrue();
    }

    @Test
    @DisplayName("zwei Kills dicht beieinander ergeben EINEN Haufen mit der Summe (FR-028)")
    void twoDropsNearbyBecomeOne() {
        PlayerMock alice = server.addPlayer();

        Optional<Item> first = piles.drop(plan(alice, aliceCharacter, 500L), () -> true);
        Optional<Item> second = piles.drop(plan(alice, aliceCharacter, 300L), () -> true);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get()).as("derselbe Haufen, nicht ein zweiter").isSameAs(first.get());
        assertThat(CoinPileTag.amountOf(second.get().getItemStack()))
                .as("die Betraege werden im Container addiert, nie ueber Stueckzahlen")
                .hasValue(800L);
    }

    @Test
    @DisplayName("zwei Kills verschiedener Charaktere ergeben ZWEI Haufen")
    void differentCharactersGetSeparatePiles() {
        PlayerMock alice = server.addPlayer();
        PlayerMock bob = server.addPlayer();

        Optional<Item> first = piles.drop(plan(alice, aliceCharacter, 500L), () -> true);
        Optional<Item> second = piles.drop(plan(bob, bobCharacter, 300L), () -> true);

        assertThat(second.get()).isNotSameAs(first.get());
        assertThat(CoinPileTag.amountOf(first.get().getItemStack())).hasValue(500L);
        assertThat(CoinPileTag.amountOf(second.get().getItemStack())).hasValue(300L);
    }

    @Test
    @DisplayName("der Haufen wird versteckt und genau dem Berechtigten gezeigt (FR-027a)")
    void hiddenFromEveryoneAndShownToTheEntitled() {
        PlayerMock alice = server.addPlayer();
        PlayerMock bob = server.addPlayer();

        Optional<Item> pile = piles.drop(plan(alice, aliceCharacter, 500L), () -> true);

        assertThat(platform.hidden)
                .as("erst unsichtbar fuer alle")
                .containsExactly(pile.get());
        assertThat(platform.shownTo)
                .as("dann genau dem einen, dem er zusteht")
                .containsExactly(Map.entry(pile.get(), alice.getUniqueId()));
        assertThat(platform.shownTo)
                .as("Bob war nicht beteiligt und sieht deshalb nichts")
                .noneMatch(entry -> entry.getValue().equals(bob.getUniqueId()));
    }

    @Test
    @DisplayName("ein abgemeldeter Berechtigter bekommt nichts gezeigt - der Haufen bleibt versteckt")
    void anOfflineOwnerIsShownNothing() {
        UUID absent = UUID.randomUUID();

        Optional<Item> pile =
                piles.drop(new CoinDropPlan(aliceCharacter, absent, 500L, origin), () -> true);

        assertThat(platform.hidden).containsExactly(pile.get());
        assertThat(platform.shownTo)
                .as("er verfaellt dann - der Spieler war nicht da, um ihn zu nehmen (FR-029)")
                .isEmpty();
    }

    @Test
    @DisplayName("die Vanilla-Haertung wird mit Besitzer und Vorabalterung verlangt")
    void hardeningIsAskedForWithOwnerAndPreAging() {
        PlayerMock alice = server.addPlayer();

        Optional<Item> pile = piles.drop(plan(alice, aliceCharacter, 500L), () -> true);

        assertThat(platform.hardened)
                .containsExactly(
                        new RecordingPlatform.Hardened(
                                pile.get(), alice.getUniqueId(), config().spawnTicksLived()));
    }

    @Test
    @DisplayName("ohne Platz entsteht nichts - der Aufrufer schreibt dann direkt gut")
    void withoutRoomNothingIsDropped() {
        PlayerMock alice = server.addPlayer();

        Optional<Item> pile = piles.drop(plan(alice, aliceCharacter, 500L), () -> false);

        assertThat(pile)
                .as("und der Aufrufer verliert die Coins deshalb NICHT - er bucht sie (FR-030)")
                .isEmpty();
    }

    @Test
    @DisplayName("die Deckelung wird beim Zusammenlegen gar nicht befragt")
    void mergingDoesNotConsultTheCap() {
        PlayerMock alice = server.addPlayer();
        piles.drop(plan(alice, aliceCharacter, 500L), () -> true);

        Optional<Item> merged = piles.drop(plan(alice, aliceCharacter, 200L), () -> false);

        assertThat(merged)
                .as("es entsteht kein Entity, also braucht es auch keinen Platz")
                .isPresent();
        assertThat(CoinPileTag.amountOf(merged.get().getItemStack())).hasValue(700L);
    }

    private CoinDropPlan plan(PlayerMock player, UUID characterId, long amount) {
        return new CoinDropPlan(characterId, player.getUniqueId(), amount, origin);
    }

    private static CurrencyConfig config() {
        return new CurrencyConfig(
                0L,
                4L,
                Map.of("ZOMBIE", 5L),
                Duration.ofSeconds(120),
                3.0d,
                400,
                Duration.ofDays(30),
                45);
    }

    /** Haelt fest, was von der Plattform verlangt wurde. */
    static final class RecordingPlatform implements CoinPile.PilePlatform {

        record Hardened(Item pile, UUID ownerId, int spawnTicksLived) {}

        final List<Item> hidden = new ArrayList<>();
        final List<Map.Entry<Item, UUID>> shownTo = new ArrayList<>();
        final List<Hardened> hardened = new ArrayList<>();

        @Override
        public void hideFromEveryone(Item pile) {
            hidden.add(pile);
        }

        @Override
        public void showTo(Item pile, Player player) {
            shownTo.add(Map.entry(pile, player.getUniqueId()));
        }

        @Override
        public void harden(Item pile, UUID ownerId, int spawnTicksLived) {
            hardened.add(new Hardened(pile, ownerId, spawnTicksLived));
        }
    }
}

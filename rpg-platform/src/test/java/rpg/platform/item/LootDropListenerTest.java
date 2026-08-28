package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.LootEntry;
import rpg.core.item.LootPlanner;
import rpg.core.item.LootTable;
import rpg.core.item.LootTables;
import rpg.core.item.PartyLootRotation;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.progression.PartyRegistry;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.platform.drop.OwnedDropPlatform;
import rpg.platform.drop.OwnedDropRegistry;
import rpg.platform.drop.OwnedDrops;

/**
 * Ein Tod erzeugt die geplanten Gegenstände — jeder mit dem richtigen Eigentümer.
 *
 * <p><b>Und einiges ausdrücklich nicht:</b> keine Beute ohne Spielertötung, keine Beute für den Tod
 * eines Spielers, kein Absturz, wenn die Kreatur schon weg ist. Der letzte Fall ist der, den
 * {@code CoinDropListener} sich seinerzeit ausdrücklich aufgeschrieben hat — ohne Art und ohne Ort
 * gibt es nichts fallen zu lassen, und beides zu raten wäre schlechter.
 *
 * <p><b>Was hier NICHT geprüft wird:</b> ob Paper die Sichtbarkeit wirklich einhält. Das beweist der
 * echte Server (quickstart.md Abschnitt 3, Schritte 13–17). Hier wird geprüft, <em>was verlangt
 * wird</em>.
 */
class LootDropListenerTest {

    private static final Logger QUIET = Logger.getLogger(LootDropListenerTest.class.getName());
    private static final String KIND = "greenfields.rotling";
    private static final String ZONE = "greenfields";

    private ServerMock server;
    private WorldMock world;
    private EventBus events;
    private RecordingPlatform platform;
    private OwnedDrops drops;
    private LootDropListener listener;

    private final UUID player = UUID.randomUUID();
    private final UUID character = UUID.randomUUID();
    private final List<Object> published = new ArrayList<>();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        events = new DefaultEventBus(QUIET);
        events.subscribe(rpg.core.item.LootDroppedEvent.class, published::add);

        platform = new RecordingPlatform();
        OwnedDropRegistry dropRegistry = new OwnedDropRegistry(platform);
        drops = new OwnedDrops(platform, dropRegistry, 0);

        listener =
                new LootDropListener(
                        server,
                        planner(),
                        new ItemStackFactory(items(), messages()),
                        drops,
                        characterId -> Optional.empty(), // offline - ein gewoehnlicher Ausgang
                        kind -> false,
                        events,
                        QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Tod erzeugt den geplanten Gegenstand, mit dem richtigen Eigentuemer")
    void aDeathCreatesThePlannedItem() {
        var creature = markedCreature();

        listener.onDeath(death(creature.getUniqueId(), false));

        assertThat(platform.dropped)
                .as("der Gegenstand wurde gesetzt - erst versteckt, dann gehaertet")
                .isEqualTo(1);
        assertThat(drops.ownerOf(platform.lastEntityId)).contains(character);
    }

    @Test
    @DisplayName("und veroeffentlicht das Ereignis fuer B12")
    void andPublishesTheEventForB12() {
        listener.onDeath(death(markedCreature().getUniqueId(), false));

        assertThat(published).hasSize(1);
        rpg.core.item.LootDroppedEvent event = (rpg.core.item.LootDroppedEvent) published.get(0);
        assertThat(event.templateKey()).isEqualTo("potion.test");
        assertThat(event.ownerCharacterId()).isEqualTo(character);
        assertThat(event.kindKey()).isEqualTo(KIND);
    }

    @Test
    @DisplayName("der Tod eines SPIELERS laesst nichts fallen")
    void aPlayerDeathDropsNothing() {
        listener.onDeath(death(markedCreature().getUniqueId(), true));

        assertThat(platform.dropped).isZero();
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("eine Kreatur, die schon weg ist, laesst nichts fallen - und stuerzt nicht ab")
    void anAlreadyRemovedCreatureDropsNothing() {
        // Ohne Art und ohne Ort gibt es nichts fallen zu lassen, und beides zu raten waere
        // schlechter. Dieselbe Entscheidung wie in CoinDropListener.
        assertThatCode(() -> listener.onDeath(death(UUID.randomUUID(), false)))
                .doesNotThrowAnyException();
        assertThat(platform.dropped).isZero();
    }

    @Test
    @DisplayName("ein Fehler hier reisst B05s Todesbehandlung nicht mit (Constitution VI)")
    void afailureDoesNotTakeTheDeathHandlingWithIt() {
        LootDropListener broken =
                new LootDropListener(
                        server,
                        planner(),
                        new ItemStackFactory(items(), messages()),
                        drops,
                        characterId -> {
                            throw new IllegalStateException("boom");
                        },
                        kind -> false,
                        events,
                        QUIET);

        assertThatCode(() -> broken.onDeath(death(markedCreature().getUniqueId(), false)))
                .as("lokal gefangen und protokolliert - ein Block darf keinen anderen mitreissen")
                .doesNotThrowAnyException();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private org.bukkit.entity.Entity markedCreature() {
        var creature = world.spawnEntity(new Location(world, 0.5, 65, 0.5), EntityType.ZOMBIE);
        rpg.platform.mob.MobKindTag.mark(creature, KIND, ZONE);
        return creature;
    }

    private CombatDeathEvent death(UUID victimId, boolean playerVictim) {
        return new CombatDeathEvent(
                victimId,
                playerVictim ? character : null,
                player,
                DeathCause.COMBAT,
                new DamageShare(Map.of(player, 1.0), player, 10.0),
                playerVictim);
    }

    private LootPlanner planner() {
        PartyRegistry parties =
                new PartyRegistry(
                        new NoSessions(), new DefaultEventBus(QUIET), Clock.systemUTC(), 5,
                        Duration.ofSeconds(60));
        ItemConfig config =
                new ItemConfig(
                        Map.of("potion.test", template()),
                        WearCurve.defaults(),
                        new RepairPricing(List.of(0L, 40L, 120L, 400L, 1200L, 3000L)),
                        new LootTables(
                                Map.of(KIND, new LootTable(List.of(LootEntry.single("potion.test", 1.0)))),
                                Map.of(),
                                Map.of()),
                        Map.of());
        return new LootPlanner(
                () -> config,
                parties,
                () -> (origin, candidates, count, range, out) -> 0,
                () -> 32.0,
                playerId -> playerId.equals(player) ? Optional.of(character) : Optional.empty(),
                new PartyLootRotation(),
                always());
    }

    private static Items items() {
        ItemConfig config =
                new ItemConfig(
                        Map.of("potion.test", template()),
                        WearCurve.defaults(),
                        new RepairPricing(List.of(0L, 40L)),
                        LootTables.empty(),
                        Map.of());
        return new Items() {
            @Override
            public Optional<ItemTemplate> template(String key) {
                return config.template(key);
            }

            @Override
            public java.util.Collection<String> templateKeys() {
                return config.templates().keySet();
            }

            @Override
            public java.util.OptionalLong sellPriceOf(String key) {
                return config.template(key)
                        .map(ItemTemplate::sellPriceOrNone)
                        .orElseGet(java.util.OptionalLong::empty);
            }

            @Override
            public WearCurve wear() {
                return config.wear();
            }
        };
    }

    private static ItemTemplate template() {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                "POTION",
                Rarity.COMMON,
                null,
                null,
                3L,
                null,
                ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                null);
    }

    private static MapMessages messages() {
        return new MapMessages(
                Map.of("item.potion.test.name", "Test Potion", "item.rarity.common.name", "Common"));
    }

    private static RandomGenerator always() {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public double nextDouble() {
                return 0.0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    /** Schreibt auf, was verlangt wird — MockBukkit kann die Aufrufe selbst nicht. */
    private static final class RecordingPlatform implements OwnedDropPlatform {

        private int dropped;
        private UUID lastEntityId;

        @Override
        public void hideFromEveryone(org.bukkit.entity.Item drop) {
            dropped++;
            lastEntityId = drop.getUniqueId();
        }

        @Override
        public void showTo(org.bukkit.entity.Item drop, org.bukkit.entity.Player player) {}

        @Override
        public void harden(org.bukkit.entity.Item drop, UUID ownerId, int spawnTicksLived) {}
    }

    private static final class NoSessions implements SessionRegistry {
        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public PlayerSession require(UUID playerId) {
            throw new IllegalStateException("no session");
        }

        @Override
        public boolean isReady(UUID playerId) {
            return true;
        }

        @Override
        public int activeSessionCount() {
            return 0;
        }
    }
}

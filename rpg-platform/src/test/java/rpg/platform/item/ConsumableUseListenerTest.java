package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.item.ConsumableBuffs;
import rpg.core.item.ConsumableCooldown;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ConsumableUse;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.LootTables;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.session.CharacterClass;

/**
 * <b>Der Trank wirkt — und der Test hat DREI verschiedene Kennungen, weil das der Punkt ist.</b>
 *
 * <p>Auf dem echten Server tat der Trank gar nichts: kein Effekt, keine Meldung, kein verbrauchtes
 * Exemplar. Die Ursache war eine Zeile in der Verdrahtung —
 * {@code holderOf.apply(player.getUniqueId())} statt {@code holderOf.apply(characterId)}.
 * {@code StatEngine.holderOf} hält einen Rückwärtsindex über <em>Charaktere</em>; mit einer
 * Spieler-ID gefragt findet er nichts, und der Frühausstieg darunter brach still ab.
 *
 * <p><b>Warum kein einziger der 2439 Tests das gesehen hat.</b> Sie setzen Spieler, Charakter und
 * Halter auf dieselbe UUID. Damit ist jede Verwechslung der drei unsichtbar — dasselbe blinde Auge,
 * das dieses Projekt schon einmal 1614 Tests lang hatte. Dieser Test vergibt deshalb <b>drei
 * verschiedene</b> Kennungen und akzeptiert nur die richtige.
 *
 * <p>Und es ist derselbe Fehler, den B08 gemacht hat. {@code StatEngine.holderOf} trägt seine
 * Beschreibung im Javadoc: <em>„no ability did anything and nobody healed."</em>
 */
class ConsumableUseListenerTest {

    private static final Logger QUIET = Logger.getLogger("consumable-use-test");
    private static final String POTION = "potion.test";

    /** Drei verschiedene, und keine davon ist eine andere. */
    private final UUID characterId = UUID.randomUUID();
    private final UUID holderId = UUID.randomUUID();

    private ServerMock server;
    private PlayerMock player;
    private RecordingResources resources;
    private ConsumableUseListener listener;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        player = server.addPlayer();
        resources = new RecordingResources();

        java.util.function.Function<UUID, Optional<UUID>> holderOf =
                id -> characterId.equals(id) ? Optional.of(holderId) : Optional.empty();
        java.util.function.Function<UUID, Optional<UUID>> characterOf =
                id -> player.getUniqueId().equals(id) ? Optional.of(characterId) : Optional.empty();

        listener =
                new ConsumableUseListener(
                        new ConfiguredItems(),
                        new ConsumableUse(
                                new ConsumableCooldown(Clock.systemUTC()),
                                ConsumableUseListener.wouldDoSomething(resources, holderOf)),
                        new ConsumableBuffs(new NoBuffs(), Clock.systemUTC()),
                        resources,
                        characterOf,
                        holderOf,
                        id -> 60,
                        id -> Optional.of(CharacterClass.WARRIOR),
                        messages(),
                        QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Rechtsklick heilt den HALTER - nicht den Spieler, nicht den Charakter")
    void arightClickHealsTheHolder() {
        ItemStack potion = potion(1);
        player.getInventory().setItemInMainHand(potion);

        listener.onUse(rightClick(potion));

        assertThat(resources.healed)
                .as("genau das ging auf dem Server schief: es passierte gar nichts")
                .containsExactly(Map.entry(holderId, 40.0));
    }

    @Test
    @DisplayName("und verbraucht GENAU EIN Exemplar (FR-033)")
    void andconsumesExactlyOne() {
        ItemStack potion = potion(3);
        player.getInventory().setItemInMainHand(potion);

        listener.onUse(rightClick(potion));

        assertThat(potion.getAmount()).isEqualTo(2);
    }

    @Test
    @DisplayName("bei vollem Leben passiert nichts - und der Stapel bleibt (FR-036)")
    void atfullHealthNothingHappens() {
        resources.currentHealth = resources.maxHealth;
        ItemStack potion = potion(3);
        player.getInventory().setItemInMainHand(potion);

        listener.onUse(rightClick(potion));

        assertThat(resources.healed).isEmpty();
        assertThat(potion.getAmount())
                .as("verbraucht wird nur, was gewirkt hat")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("ein Spieler ohne Charakter trinkt nichts - und verliert nichts")
    void aplayerWithoutACharacterDrinksNothing() {
        ConsumableUseListener orphan =
                new ConsumableUseListener(
                        new ConfiguredItems(),
                        new ConsumableUse(
                                new ConsumableCooldown(Clock.systemUTC()), (id, effect) -> true),
                        new ConsumableBuffs(new NoBuffs(), Clock.systemUTC()),
                        resources,
                        id -> Optional.empty(),
                        id -> Optional.of(holderId),
                        id -> 60,
                        id -> Optional.of(CharacterClass.WARRIOR),
                        messages(),
                        QUIET);
        ItemStack potion = potion(2);
        player.getInventory().setItemInMainHand(potion);

        orphan.onUse(rightClick(potion));

        assertThat(resources.healed).isEmpty();
        assertThat(potion.getAmount()).isEqualTo(2);
    }

    @Test
    @DisplayName("ein gewoehnlicher Gegenstand wird nicht angefasst - der haeufigste Rechtsklick")
    void anordinaryItemIsLeftAlone() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        player.getInventory().setItemInMainHand(sword);

        PlayerInteractEvent event = rightClick(sword);
        listener.onUse(event);

        // NICHT ueber isCancelled(): bei RIGHT_CLICK_AIR meldet Bukkit das von Haus aus als
        // abgebrochen, weil kein Block beteiligt ist - unabhaengig davon, was ein Zuhoerer tut.
        // Gefragt wird deshalb nach dem, was setCancelled(true) hier wirklich setzt.
        assertThat(event.useItemInHand())
                .as("fast jeder Rechtsklick im Spiel geht hier durch - er darf nichts kosten")
                .isNotEqualTo(org.bukkit.event.Event.Result.DENY);
        assertThat(resources.healed).isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private PlayerInteractEvent rightClick(ItemStack held) {
        return new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_AIR, held, null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
    }

    private static ItemStack potion(int amount) {
        ItemStack stack = new ItemStack(Material.POTION, amount);
        ItemTag.mark(stack, POTION, ItemSchemaMigration.CURRENT);
        return stack;
    }

    private static MapMessages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.refused.no-effect", "nothing");
        return new MapMessages(texts);
    }

    private static ItemConfig config() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(
                POTION,
                new ItemTemplate(
                        POTION,
                        ItemCategory.CONSUMABLE,
                        "POTION",
                        Rarity.COMMON,
                        null,
                        null,
                        3L,
                        null,
                        ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                        null));
        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of());
    }

    private static final class ConfiguredItems implements Items {

        @Override
        public Optional<ItemTemplate> template(String templateKey) {
            return Optional.ofNullable(config().templates().get(templateKey));
        }

        @Override
        public java.util.Collection<String> templateKeys() {
            return config().templates().keySet();
        }

        @Override
        public OptionalLong sellPriceOf(String templateKey) {
            return template(templateKey).map(ItemTemplate::sellPriceOrNone).orElse(OptionalLong.empty());
        }

        @Override
        public WearCurve wear() {
            return config().wear();
        }
    }

    /** Schreibt mit, WEN geheilt wurde — die Kennung ist hier die eigentliche Zusicherung. */
    private static final class RecordingResources implements ConsumableUseListener.Resources {

        private final List<Map.Entry<UUID, Double>> healed = new ArrayList<>();
        private final Map<UUID, Double> mana = new HashMap<>();
        private double currentHealth = 20.0;
        private final double maxHealth = 100.0;

        @Override
        public double currentHealth(UUID holderId) {
            return currentHealth;
        }

        @Override
        public double maxHealth(UUID holderId) {
            return maxHealth;
        }

        @Override
        public double currentMana(UUID holderId) {
            return 0.0;
        }

        @Override
        public double maxMana(UUID holderId) {
            return 100.0;
        }

        @Override
        public void changeHealth(UUID holderId, double delta) {
            healed.add(Map.entry(holderId, delta));
        }

        @Override
        public void changeMana(UUID holderId, double delta) {
            mana.merge(holderId, delta, Double::sum);
        }
    }

    private static final class NoBuffs implements ConsumableBuffs.BuffSink {

        @Override
        public void apply(UUID holderId, rpg.core.stats.ModifierSet set) {}

        @Override
        public void remove(UUID holderId, rpg.core.stats.SourceId source) {}
    }
}

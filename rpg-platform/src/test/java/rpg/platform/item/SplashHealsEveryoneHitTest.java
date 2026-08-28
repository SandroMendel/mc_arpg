package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.item.ConsumableBuffs;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.LootTables;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.WearCurve;

/**
 * <b>Ein geworfener Heiltrank heilt jeden, den er trifft.</b>
 *
 * <p>Ein Trank, den man nur selbst trinkt, hilft in einer Gruppe genau einem. Geworfen hilft er
 * allen im Radius — und macht aus dem Heiltrank eine Entscheidung, wohin man ihn wirft, statt einer
 * Taste, die man drückt.
 *
 * <p><b>Zwei Dinge werden hier ausdrücklich <em>nicht</em> geheilt</b>, und beide wären ein Fehler,
 * den man erst im Kampf bemerkt: eine getroffene Kreatur (die Horde mitzuheilen wäre kein
 * Heiltrank), und jemand ohne Charakter (wer in der Klassenauswahl steht, hat keine Werte).
 */
class SplashHealsEveryoneHitTest {

    private static final Logger QUIET = Logger.getLogger("splash-test");
    private static final String POTION = "potion.test";

    private ServerMock server;
    private WorldMock world;
    private RecordingResources resources;
    private PotionSplashListener listener;

    private final List<UUID> withCharacter = new ArrayList<>();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        resources = new RecordingResources();
        listener =
                new PotionSplashListener(
                        new ConfiguredItems(),
                        new ConsumableBuffs(new NoBuffs(), Clock.systemUTC()),
                        resources,
                        holderId ->
                                withCharacter.contains(holderId)
                                        ? Optional.of(UUID.randomUUID())
                                        : Optional.empty(),
                        QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("DREI getroffene Spieler werden alle drei geheilt")
    void allthreeHitPlayersAreHealed() {
        PlayerMock first = playerWithCharacter();
        PlayerMock second = playerWithCharacter();
        PlayerMock third = playerWithCharacter();

        listener.onSplash(splash(first, second, third));

        assertThat(resources.healed.keySet())
                .as("das ist der ganze Grund, warum Heiltraenke geworfen werden")
                .containsExactlyInAnyOrder(
                        first.getUniqueId(), second.getUniqueId(), third.getUniqueId());
    }

    @Test
    @DisplayName("und jeder bekommt den VOLLEN Betrag - nicht nach Entfernung abgestuft")
    void andeveryoneGetsTheFullAmount() {
        PlayerMock near = playerWithCharacter();
        PlayerMock far = playerWithCharacter();

        listener.onSplash(splash(near, far));

        // In der Lore steht eine Zahl. Ein Trank, der "heilt 140" sagt und 63 heilt, ist ein Trank,
        // dem man nicht glaubt.
        assertThat(resources.healed.values()).containsExactly(140.0, 140.0);
    }

    @Test
    @DisplayName("ein Spieler OHNE Charakter wird nicht geheilt")
    void aplayerWithoutACharacterIsNotHealed() {
        PlayerMock inSelection = server.addPlayer();

        listener.onSplash(splash(inSelection));

        assertThat(resources.healed).isEmpty();
    }

    @Test
    @DisplayName("Vanillas eigene Wirkung wird abgeschaltet")
    void vanillaEffectsAreSwitchedOff() {
        PlayerMock hit = playerWithCharacter();
        PotionSplashEvent event = splash(hit);

        listener.onSplash(event);

        assertThat(event.getIntensity(hit))
                .as("ein B11-Trank ist ein Behaelter mit einer Vorlagen-ID, kein Vanilla-Trank")
                .isZero();
    }

    @Test
    @DisplayName("ein gewoehnlicher Vanilla-Wurftrank wird nicht angefasst")
    void anordinaryVanillaPotionIsLeftAlone() {
        PlayerMock hit = playerWithCharacter();
        PotionSplashEvent event = splashOf(new ItemStack(Material.SPLASH_POTION), hit);

        listener.onSplash(event);

        assertThat(resources.healed).isEmpty();
        assertThat(event.getIntensity(hit))
                .as("das Spiel bleibt Minecraft")
                .isNotZero();
    }

    @Test
    @DisplayName("eine verschwundene Vorlage laesst den Trank wirkungslos zerschellen (FR-007)")
    void avanishedTemplateSplashesWithoutEffect() {
        PlayerMock hit = playerWithCharacter();
        ItemStack unknown = new ItemStack(Material.SPLASH_POTION);
        ItemTag.mark(unknown, "potion.does-not-exist", ItemSchemaMigration.CURRENT);

        listener.onSplash(splashOf(unknown, hit));

        assertThat(resources.healed).isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private PlayerMock playerWithCharacter() {
        PlayerMock player = server.addPlayer();
        withCharacter.add(player.getUniqueId());
        return player;
    }

    private PotionSplashEvent splash(LivingEntity... hit) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        ItemTag.mark(potion, POTION, ItemSchemaMigration.CURRENT);
        return splashOf(potion, hit);
    }

    private PotionSplashEvent splashOf(ItemStack potion, LivingEntity... hit) {
        ThrownPotion thrown =
                world.spawn(new Location(world, 0, 64, 0), ThrownPotion.class);
        thrown.setItem(potion);
        Map<LivingEntity, Double> affected = new LinkedHashMap<>();
        for (LivingEntity entity : hit) {
            affected.put(entity, 1.0);
        }
        return new PotionSplashEvent(thrown, affected);
    }

    private static ItemConfig config() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(
                POTION,
                new ItemTemplate(
                        POTION,
                        ItemCategory.CONSUMABLE,
                        "SPLASH_POTION",
                        Rarity.COMMON,
                        null,
                        null,
                        3L,
                        null,
                        ConsumableEffect.healing(140.0, Duration.ofSeconds(8)),
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

    /** Schreibt mit, WER wie viel bekommen hat. */
    private static final class RecordingResources implements ConsumableUseListener.Resources {

        private final Map<UUID, Double> healed = new LinkedHashMap<>();

        @Override
        public double currentHealth(UUID holderId) {
            return 10.0;
        }

        @Override
        public double maxHealth(UUID holderId) {
            return 100.0;
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
            healed.merge(holderId, delta, Double::sum);
        }

        @Override
        public void changeMana(UUID holderId, double delta) {}
    }

    private static final class NoBuffs implements ConsumableBuffs.BuffSink {

        @Override
        public void apply(UUID holderId, rpg.core.stats.ModifierSet set) {}

        @Override
        public void remove(UUID holderId, rpg.core.stats.SourceId source) {}
    }
}

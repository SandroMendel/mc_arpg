package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.Rarity;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.platform.drop.OwnedDropPlatform;
import rpg.platform.drop.OwnedDropRegistry;
import rpg.platform.drop.OwnedDrops;
import rpg.platform.item.ItemStackFactory;
import rpg.platform.item.ItemTag;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/** T060 — the admin grant follows the same item and full-inventory rules as loot. */
class ItemGiveTest {

    private static final String TEMPLATE = "potion.test";
    private static final UUID CHARACTER = UUID.randomUUID();

    private ServerMock server;
    private WorldMock world;
    private PlayerMock target;
    private MutableItems items;
    private ItemStackFactory factory;
    private RecordingPlatform platform;
    private OwnedDrops drops;
    private RecordingAuditLog auditLog;
    private ItemGiveCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        target = server.addPlayer("Ticoo");
        target.teleport(new Location(world, 0.5, 65, 0.5));

        items = new MutableItems();
        items.templates.put(TEMPLATE, template());
        factory = new ItemStackFactory(items, messages());

        platform = new RecordingPlatform();
        OwnedDropRegistry registry = new OwnedDropRegistry(platform);
        drops = new OwnedDrops(platform, registry, 0);

        auditLog = new RecordingAuditLog();
        command =
                new ItemGiveCommand(
                        server,
                        items,
                        factory,
                        drops,
                        playerId -> playerId.equals(target.getUniqueId())
                                ? Optional.of(CHARACTER)
                                : Optional.empty(),
                        new AdminAudit(
                                auditLog,
                                Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC)),
                        messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein gegebener Gegenstand kommt aus derselben Fabrik wie Beute")
    void aGrantedItemIsTheSameAsLoot() {
        execute(TEMPLATE, 3L);

        ItemStack expected = factory.create(TEMPLATE, 1).orElseThrow();
        List<ItemStack> actual =
                Arrays.stream(target.getInventory().getContents())
                        .filter(Objects::nonNull)
                        .toList();

        assertThat(actual).hasSize(3).allSatisfy(stack -> assertThat(stack).isEqualTo(expected));
        assertThat(ItemTag.templateOf(actual.get(0))).contains(TEMPLATE);
        assertThat(auditLog.entries).singleElement().satisfies(this::assertGrantAudit);
    }

    @Test
    @DisplayName("volles Inventar laesst nichts fallen oder verschwinden")
    void aFullInventoryKeepsTheItemAsAnOwnedDrop() {
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) {
            target.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }

        execute(TEMPLATE, 5L);

        assertThat(target.getInventory().getContents())
                .allMatch(stack -> stack != null && stack.getType() == Material.STONE);
        assertThat(platform.lastDrop).isNotNull();
        assertThat(platform.lastDrop.getItemStack())
                .isEqualTo(factory.create(TEMPLATE, 5).orElseThrow());
        assertThat(drops.ownerOf(platform.lastDrop.getUniqueId())).contains(CHARACTER);
        assertThat(auditLog.entries).singleElement().satisfies(this::assertGrantAudit);
        assertThat(target.nextMessage()).contains("Inventory full");
    }

    @Test
    @DisplayName("eine waehrend des Reloads verschwundene Vorlage bricht ohne Aenderung ab")
    void anUnknownTemplateIsRejectedWithoutChangingAnything() {
        items.templates.clear();

        execute(TEMPLATE, 1L);

        assertThat(target.getInventory().getContents()).allMatch(stack -> stack == null);
        assertThat(platform.lastDrop).isNull();
        assertThat(auditLog.entries).isEmpty();
        assertThat(target.nextMessage()).contains(TEMPLATE);
    }

    @Test
    @DisplayName("die Item-Gruppe haengt mit Recht unter der rpg-Wurzel")
    void theDefinitionHasTheAdminShape() {
        RpgCommand item = command.definition();
        RpgCommand give = item.children().get(0);

        assertThat(item.name()).isEqualTo("item");
        assertThat(item.permissionOrNone()).isEmpty();
        assertThat(give.name()).isEqualTo("give");
        assertThat(give.permissionOrNone()).contains(ItemGiveCommand.PERMISSION);
        assertThat(give.arguments()).extracting(rpg.plugin.command.framework.Argument::name)
                .containsExactly("player", "template", "amount");
        assertThat(give.arguments().get(2).required()).isFalse();
        assertThat(give.requiresPlayer()).isFalse();
    }

    private void execute(String templateKey, long amount) {
        RpgCommand give = command.definition().children().get(0);
        List<rpg.plugin.command.framework.Argument<?>> arguments = give.arguments();
        give.action()
                .accept(
                        context(
                                target,
                                Map.of(
                                        arguments.get(0).name(), target,
                                        arguments.get(1).name(), templateKey,
                                        arguments.get(2).name(), amount)));
    }

    private static CommandContext context(CommandSender sender, Map<String, Object> values) {
        try {
            Constructor<CommandContext> constructor =
                    CommandContext.class.getDeclaredConstructor(CommandSender.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(sender, values);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("test context could not be built", failure);
        }
    }

    private void assertGrantAudit(AuditEntry entry) {
        assertThat(entry.action()).isEqualTo("item_granted");
        assertThat(entry.targetPlayerId()).contains(target.getUniqueId());
        assertThat(entry.actor()).isEqualTo(target.getUniqueId().toString());
        assertThat(entry.details()).containsEntry("template", TEMPLATE);
    }

    private static ItemTemplate template() {
        return new ItemTemplate(
                TEMPLATE,
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

    private static Messages messages() {
        return new MapMessages(
                Map.of(
                        "item.potion.test.name", "Test Potion",
                        "item.rarity.common.name", "Common",
                        "item.effect.heal", "Heals {amount}.",
                        "item.effect.cooldown", "Cooldown {seconds}s.",
                        "command.item.description", "Items",
                        "command.item.give.description", "Give an item",
                        "command.item.give.done", "Granted {amount}x {template} to {player}.",
                        "command.item.give.dropped",
                                "Inventory full: dropped {amount}x {template} for {player}.",
                        "command.item.give.target-unavailable",
                                "{player} is not ready.",
                        "command.error.unknown-key", "Unknown key {key}."));
    }

    private static final class MutableItems implements Items {

        private final Map<String, ItemTemplate> templates = new LinkedHashMap<>();

        @Override
        public Optional<ItemTemplate> template(String templateKey) {
            return Optional.ofNullable(templates.get(templateKey));
        }

        @Override
        public Collection<String> templateKeys() {
            return templates.keySet();
        }

        @Override
        public java.util.OptionalLong sellPriceOf(String templateKey) {
            return template(templateKey)
                    .map(ItemTemplate::sellPriceOrNone)
                    .orElseGet(java.util.OptionalLong::empty);
        }

        @Override
        public WearCurve wear() {
            return WearCurve.defaults();
        }
    }

    private static final class RecordingPlatform implements OwnedDropPlatform {

        private Item lastDrop;

        @Override
        public void hideFromEveryone(Item drop) {
            lastDrop = drop;
        }

        @Override
        public void showTo(Item drop, org.bukkit.entity.Player player) {}

        @Override
        public void harden(Item drop, UUID ownerId, int spawnTicksLived) {}
    }

    private static final class RecordingAuditLog implements AuditLogRepository {

        private final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public CompletableFuture<List<AuditEntry>> between(Instant from, Instant to) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }
    }
}

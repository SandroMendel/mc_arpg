package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.message.MapMessages;
import rpg.core.session.CharacterClass;
import rpg.platform.classes.BoundItemFactory;

/**
 * FR-078, FR-063 — <b>der Mülleimer vernichtet erst nach einer Bestätigung, und nie
 * Klassenausrüstung.</b>
 *
 * <p><b>Zwei Aufrufe, und der erste tut nichts.</b> Vernichten ist der einzige unumkehrbare Vorgang,
 * den ein Spieler hier auslösen kann. Ein einzelner Tippfehler darf ihn nicht auslösen — und die
 * Bestätigung muss sich auf <em>denselben</em> Gegenstand beziehen, sonst bestätigt sie etwas
 * anderes, als der Spieler gemeint hat.
 *
 * <p><b>Klassenausrüstung wird abgewiesen</b> (ADR-018). Sie ist der Fortschritt selbst; sie zu
 * vernichten wäre eine gelöschte Stufe. Der Mülleimer ist einer von drei Wegen, die das einzeln
 * nachweisen müssen — die anderen sind der Händler ({@code BoundEquipmentIsNotSellableTest}) und die
 * Enderchest, die B07s {@code EquipmentLockListener} bereits schließt.
 */
class TrashRefusesBoundEquipmentTest {

    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private ServerMock server;
    private PlayerMock player;
    private TrashCommand trash;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        Clock clock =
                new Clock() {
                    @Override
                    public ZoneOffset getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return Instant.ofEpochMilli(now.get());
                    }

                    @Override
                    public long millis() {
                        return now.get();
                    }
                };
        // Der Vermerk kommt von B07; hier steht das Praedikat, das der Haendler auch benutzt.
        trash = new TrashCommand(messages(), clock, tag -> tag != null && !tag.isBlank());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der ERSTE Aufruf vernichtet nichts - er fragt nach")
    void thefirstCallDestroysNothing() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 5));

        trash.handle(player);

        assertThat(player.getInventory().getItemInMainHand().getType())
                .as("ein einzelner Tippfehler darf nichts vernichten")
                .isEqualTo(Material.COBBLESTONE);
        assertThat(trash.hasPending(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("der zweite Aufruf vernichtet")
    void thesecondCallDestroys() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 5));

        trash.handle(player);
        trash.handle(player);

        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.AIR);
        assertThat(trash.hasPending(player.getUniqueId()))
                .as("und die Bestaetigung ist verbraucht")
                .isFalse();
    }

    @Test
    @DisplayName("ein ANDERER Gegenstand in der Hand fragt neu - die Bestaetigung galt dem ersten")
    void adifferentItemAsksAgain() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 5));
        trash.handle(player);

        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 3));
        trash.handle(player);

        assertThat(player.getInventory().getItemInMainHand().getType())
                .as("sonst bestaetigte ein 'ja' etwas, das der Spieler nie gemeint hat")
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    @DisplayName("eine abgelaufene Bestaetigung fragt neu, statt zu vernichten")
    void anexpiredConfirmationAsksAgain() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 5));
        trash.handle(player);

        now.addAndGet(TrashCommand.WINDOW.plus(Duration.ofSeconds(1)).toMillis());
        trash.handle(player);

        assertThat(player.getInventory().getItemInMainHand().getType())
                .as("eine Bestaetigung, die eine Minute spaeter gilt, ist eine Falle")
                .isEqualTo(Material.COBBLESTONE);
    }

    @Test
    @DisplayName("KLASSENAUSRUESTUNG wird abgewiesen - auch beim zweiten Aufruf")
    void boundEquipmentIsRefusedEvenTwice() {
        player.getInventory().setItemInMainHand(boundChestplate());

        trash.handle(player);
        trash.handle(player);

        assertThat(player.getInventory().getItemInMainHand().getType())
                .as("die Ausruestung IST der Fortschritt - kein Weg vernichtet sie (ADR-018)")
                .isEqualTo(Material.IRON_CHESTPLATE);
        assertThat(trash.hasPending(player.getUniqueId()))
                .as("und sie hinterlaesst nicht einmal eine offene Frage")
                .isFalse();
    }

    @Test
    @DisplayName("eine leere Hand vernichtet nichts und merkt sich nichts")
    void anemptyHandDoesNothing() {
        trash.handle(player);

        assertThat(trash.hasPending(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("das Sitzungsende vergisst eine offene Bestaetigung")
    void thesessionEndForgetsAPendingConfirmation() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 5));
        trash.handle(player);

        trash.forget(player.getUniqueId());

        assertThat(trash.hasPending(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks entscheidet die Bindung selbst")
    void nothingHereDecidesTheBindingItself() throws IOException {
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("class_bound")
                                                    || code.contains("BoundEquipment.tagFor(");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("alle drei Entsorgungswege fragen B07 - keiner entscheidet selbst (FR-079)")
                    .isEmpty();
        }
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static ItemStack boundChestplate() {
        ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = stack.getItemMeta();
        BoundItemFactory.markBound(
                meta,
                BoundEquipment.tagFor(UUID.randomUUID(), CharacterClass.WARRIOR, LadderSlot.ARMOR));
        stack.setItemMeta(meta);
        return stack;
    }

    private static MapMessages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.trash.confirm", "confirm");
        texts.put("item.trash.done", "done");
        texts.put("item.trash.nothing-held", "nothing");
        texts.put("item.trash.bound", "bound");
        return new MapMessages(texts);
    }

    /** Kommentare weg — eine Erklärung ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}

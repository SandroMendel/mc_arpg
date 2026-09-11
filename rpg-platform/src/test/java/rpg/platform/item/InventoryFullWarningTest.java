package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.classes.ClassNotice;
import rpg.core.message.MessageKey;
import rpg.platform.classes.InventoryFullNoticeListener;

/**
 * SC-015, FR-075 bis FR-077 — <b>ein volles Inventar warnt, mit Ruhezeit, und verwirft nichts.</b>
 *
 * <p><b>Die zweite Hälfte ist die wichtigere.</b> Kein automatisches Aufräumen, keine
 * Hintergrundbank, kein stilles Verwerfen (ADR-018): der Gegenstand liegt weiter da, und der Spieler
 * entscheidet. Genau deshalb hängt der Zuhörer auf {@code MONITOR} und bricht nichts ab — das
 * Aufsammeln ist nicht das Problem, der fehlende Platz ist es.
 *
 * <p><b>Erweitert, nicht verdoppelt.</b> Der Zuhörer stammt aus B07 und tat schon das Richtige;
 * B11 hat ihm die konfigurierbare Ruhezeit gegeben (FR-076). Ein zweiter Zuhörer für dieselbe
 * Bedingung hätte zwei Warnungen für einen Aufsammelversuch erzeugt — und die zweite hätte die erste
 * verdeckt.
 */
class InventoryFullWarningTest {

    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private final AtomicLong now = new AtomicLong(5_000_000L);
    private final RecordingNotice notice = new RecordingNotice();

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private Duration cooldown = Duration.ofSeconds(15);
    private InventoryFullNoticeListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        listener = new InventoryFullNoticeListener(notice, movingClock(), () -> cooldown);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("SC-015: zwanzig Versuche, EINE Warnung - und nichts wird verworfen")
    void twentyAttemptsWarnOnceAndDiscardNothing() {
        fillTheInventory();

        List<PlayerAttemptPickupItemEvent> events = new ArrayList<>();
        for (int attempt = 0; attempt < 20; attempt++) {
            PlayerAttemptPickupItemEvent event = pickup();
            listener.onAttemptPickup(event);
            events.add(event);
        }

        assertThat(notice.shown)
                .as("ohne Ruhezeit waere das eine Wand aus zwanzig Titeln (FR-076)")
                .hasSize(1);
        assertThat(events)
                .as("nichts abgebrochen, nichts verworfen - der Gegenstand liegt weiter da (FR-075)")
                .allMatch(event -> !event.isCancelled());
    }

    @Test
    @DisplayName("nach Ablauf der Ruhezeit wird wieder gewarnt")
    void afterTheCooldownItWarnsAgain() {
        fillTheInventory();

        listener.onAttemptPickup(pickup());
        now.addAndGet(cooldown.plusSeconds(1).toMillis());
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown)
                .as("es bleibt eine Warnung - sie darf nicht fuer immer verstummen")
                .hasSize(2);
    }

    @Test
    @DisplayName("FR-076: die Ruhezeit ist KONFIGURIERBAR - eine kuerzere warnt frueher")
    void thecooldownIsConfigurable() {
        fillTheInventory();
        cooldown = Duration.ofSeconds(1);

        listener.onAttemptPickup(pickup());
        now.addAndGet(Duration.ofSeconds(2).toMillis());
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown)
                .as("mit fuenfzehn Sekunden waere hier erst eine Warnung gefallen")
                .hasSize(2);
    }

    @Test
    @DisplayName("und sie wird bei JEDEM Versuch neu gefragt - ein Reload wirkt sofort")
    void thecooldownIsAskedEveryTime() {
        fillTheInventory();
        cooldown = Duration.ofHours(1);
        listener.onAttemptPickup(pickup());

        // Was ein Nachladen tut: die Zahl in items.yml aendert sich.
        cooldown = Duration.ofMillis(1);
        now.addAndGet(10L);
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown)
                .as("ein beim Bauen gezogener Wert bliebe bis zum Neustart der alte")
                .hasSize(2);
    }

    @Test
    @DisplayName("mit Platz im Inventar wird gar nicht gewarnt - das ist der ganze heisse Pfad")
    void withRoomThereIsNoWarningAtAll() {
        for (int attempt = 0; attempt < 20; attempt++) {
            listener.onAttemptPickup(pickup());
        }

        assertThat(notice.shown).isEmpty();
    }

    @Test
    @DisplayName("das Sitzungsende vergisst den Spieler - die Karte waechst nicht ueber die Laufzeit")
    void thesessionEndForgetsThePlayer() {
        fillTheInventory();
        listener.onAttemptPickup(pickup());

        listener.forget(player.getUniqueId());
        listener.onAttemptPickup(pickup());

        assertThat(notice.shown)
                .as("nach einem Wiedereinstieg faengt die Ruhezeit von vorn an")
                .hasSize(2);
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks raeumt bei vollem Inventar etwas weg")
    void nothingHereClearsAnythingWhenTheInventoryIsFull() throws IOException {
        // ADR-018 ist hier eine Abwesenheit: keine Hintergrundbank, kein automatisches Verkaufen,
        // kein stilles Verwerfen. Alle drei saehen als Zeile hilfreich aus.
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            // Hier stand eine Ausnahme fuer TrashCommand.java - der Muelleimer
                            // vernichtet, aber nur nach zweifacher Bestaetigung. Seit B14 (T003)
                            // wohnt er in rpg.plugin.command, also findet dieser Scan ihn ohnehin
                            // nicht mehr, und die Ausnahme waere ein Filter auf eine Datei, die es
                            // hier nicht gibt. Sein eigener Wachtest ist mit umgezogen.
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("getInventory().clear()")
                                                    || code.contains("setItemInMainHand(null)");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("der Spieler macht Platz, nicht der Server (ADR-018, FR-075)")
                    .isEmpty();
        }
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private void fillTheInventory() {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
    }

    private PlayerAttemptPickupItemEvent pickup() {
        Item dropped =
                world.dropItem(
                        new org.bukkit.Location(world, 0, 64, 0), new ItemStack(Material.DIAMOND));
        return new PlayerAttemptPickupItemEvent(player, dropped, 0);
    }

    private Clock movingClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
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
    }

    /** Kommentare weg — eine Erklärung ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /** Schreibt mit, was gezeigt wurde — und wie oft. */
    private static final class RecordingNotice implements ClassNotice {

        private final List<MessageKey> shown = new ArrayList<>();

        @Override
        public void show(UUID playerId, MessageKey key) {
            shown.add(key);
        }
    }
}

package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageType;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.ui.DamageNumber;
import rpg.core.ui.DamageNumberSetting;
import rpg.core.ui.SurfaceSetting;
import rpg.core.ui.UiConfig;

/**
 * Die Schadenszahlen (T105–T108) — <b>der vorsichtigste Teil dieses Blocks</b>.
 *
 * <p>Sie sind die einzige Stelle, an der B13 etwas in die Welt schreibt, und sie schreiben es
 * bewusst flüchtig. Zwei Tests hier sind keine Bequemlichkeit, sondern der Grund, warum der Block
 * überhaupt Entities anfassen darf: <b>nicht persistent</b> und <b>Entfernung eingeplant</b>.
 */
class DamageNumbersTest {

    private static final Instant T0 = Instant.parse("2026-08-30T21:00:00Z");

    private ServerMock server;
    private WorldMock world;
    private PlayerMock dealer;
    private PlayerMock bystander;
    private RecordingScheduler scheduler;
    private DamageNumbers numbers;

    private final UUID targetId = UUID.randomUUID();
    private volatile boolean enabled = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        dealer = server.addPlayer();
        bystander = server.addPlayer();
        scheduler = new RecordingScheduler();
        numbers =
                new DamageNumbers(
                        MockBukkit.createMockPlugin("rpg-test"),
                        server,
                        scheduler,
                        messages(),
                        this::config,
                        id ->
                                id.equals(targetId)
                                        ? Optional.of(
                                                new WorldPosition(world.getUID(), 10.0, 64.0, 20.0))
                                        : Optional.empty(),
                        Clock.fixed(T0, ZoneOffset.UTC),
                        java.util.logging.Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- T105: der Test, an dem SC-009 haengt --------------------------------

    @Test
    @DisplayName("T105: eine Schadenszahl ist NICHT persistent")
    void adamageNumberIsNotPersistent() {
        // DER wichtigste Test dieser Klasse. Was der Server nicht speichert, kann ein Absturz nicht
        // zuruecklassen - das ist der ganze Inhalt von SC-009.
        //
        // Die genaue Umkehrung von B12s Hologramm: das MUSS persistent sein und raeumt deshalb vor
        // dem Setzen auf. Eine Schadenszahl, die dasselbe taete, waere bei 150 Spielern in Minuten
        // tausendfacher Muell, der einen Neustart ueberlebt - und niemand faende ihn wieder, weil
        // er keiner Rangliste gehoert.
        numbers.show(number(120.0, 1, false));
        scheduler.runLocationTasks();

        List<TextDisplay> placed = displays();
        assertThat(placed).hasSize(1);
        assertThat(placed.get(0).isPersistent())
                .as("persistent waere ein Neustart voller Zahlen")
                .isFalse();
    }

    @Test
    @DisplayName("T105: sie traegt die Markierung - die zweite Verteidigung")
    void itcarriesTheMark() {
        // Sollte nie gebraucht werden. B10 hat gelernt, warum es sie trotzdem braucht: dort umging
        // der Vanilla-Despawn das eigene Aufraeumen, und der Fehler war zwei Wochen unsichtbar,
        // weil ein zweiter ihn verdeckte.
        numbers.show(number(120.0, 1, false));
        scheduler.runLocationTasks();

        assertThat(DamageNumbers.isDamageNumber(displays().get(0))).isTrue();
    }

    // --- T106: nur der Verursacher -------------------------------------------

    @Test
    @DisplayName("T106: die Sichtbarkeitsregel steht im Code - MockBukkit kann sie nicht zurueckgeben")
    void thevisibilityRuleIsInThePlace() throws java.io.IOException {
        // FR-042. Ohne das saehe jeder im Umkreis fremde Zahlen, und bei einem Bosskampf waere der
        // Bildschirm voll.
        //
        // DIESER TEST HIESS ZUERST "die Entity ist standardmaessig UNSICHTBAR" und las
        // display.isVisibleByDefault() zurueck. MOCKBUKKIT MELDET DAS ALS "SKIPPED", NICHT ALS
        // FEHLER (UnimplementedOperationException) - der Test stand gruen in der Zusammenfassung
        // und bewies nichts. Genau davor warnt die Projektnotiz zu MockBukkit-Skips, und genau so
        // waere die Zusage unbemerkt verlorengegangen.
        //
        // Was hier stattdessen geprueft wird: dass die zwei Aufrufe UEBERHAUPT DA SIND. Das ist
        // weniger, als es aussieht - aber es ist ehrlich, und es faellt auf, wenn jemand sie
        // entfernt. Der VERHALTENSBEWEIS gehoert auf den Server: quickstart §5, Schritt 19, mit
        // zwei Spielern.
        String source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "src/main/java/rpg/platform/ui/DamageNumbers.java"));

        assertThat(source)
                .as("fuer alle unsichtbar")
                .contains("setVisibleByDefault(false)");
        assertThat(source)
                .as("und genau einer bekommt sie gezeigt")
                .contains("viewer.showEntity(plugin, display)");
    }

    @Test
    @DisplayName("T106: ohne Verursacher entsteht gar keine Zahl")
    void withoutADealerNothingIsPlaced() {
        // Es gaebe niemanden, dem man sie zeigen koennte.
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        numbers.subscribeTo(bus);

        bus.publish(new DamageDealtEvent(null, targetId, DamageType.PHYSICAL, 50.0, 1, false));
        scheduler.runLocationTasks();

        assertThat(displays()).isEmpty();
    }

    // --- T107: sie laeuft ab --------------------------------------------------

    @Test
    @DisplayName("T107: die Entfernung wird BEIM SETZEN eingeplant, nicht spaeter")
    void theremovalIsScheduledWhilePlacing() {
        // FR-044, und die Stelle, an der die T112-Falle NICHT betreten wird: hier, im Tick, ist die
        // Entitaet aufgeloest. Aus dem asynchronen Takt heraus waere derselbe Aufruf ein still
        // abgebrochener Handle - der Fehler bliebe gruen und die Zahl staende fuer immer.
        numbers.show(number(120.0, 1, false));
        scheduler.runLocationTasks();

        assertThat(scheduler.entityDelayed)
                .as("eine eingeplante Entfernung je gesetzter Anzeige")
                .hasSize(1);
    }

    @Test
    @DisplayName("T107: nach der konfigurierten Lebensdauer ist sie weg")
    void afterItsLifetimeItIsGone() {
        numbers.show(number(120.0, 1, false));
        scheduler.runLocationTasks();
        assertThat(displays()).hasSize(1);

        scheduler.runEntityDelayed();

        assertThat(displays()).isEmpty();
    }

    @Test
    @DisplayName("T107: die eingeplante Verzoegerung ist die konfigurierte Lebensdauer")
    void thedelayIsTheConfiguredLifetime() {
        numbers.show(number(120.0, 1, false));
        scheduler.runLocationTasks();

        assertThat(scheduler.entityDelays).containsExactly(Duration.ofMillis(1200));
    }

    // --- T108: abgeschaltet heisst kostenlos ---------------------------------

    @Test
    @DisplayName("T108: mit damage-numbers.enabled=false entsteht KEINE Entity")
    void disabledPlacesNothing() {
        enabled = false;
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        numbers.subscribeTo(bus);

        bus.publish(
                new DamageDealtEvent(
                        dealer.getUniqueId(), targetId, DamageType.PHYSICAL, 50.0, 1, false));
        scheduler.runLocationTasks();

        assertThat(displays()).isEmpty();
    }

    @Test
    @DisplayName("T108: und es wird auch nichts eingeplant")
    void disabledSchedulesNothing() {
        // Abgeschaltet heisst KOSTENLOS, nicht unsichtbar (FR-045): die Pruefung steht vor allem
        // anderen, nicht erst vor dem Setzen. Sonst entstuenden Position und Zahl fuer eine
        // Anzeige, die niemand sieht.
        enabled = false;
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        numbers.subscribeTo(bus);

        bus.publish(
                new DamageDealtEvent(
                        dealer.getUniqueId(), targetId, DamageType.PHYSICAL, 50.0, 1, false));

        assertThat(scheduler.locationTasks).isEmpty();
        assertThat(scheduler.entityDelayed).isEmpty();
    }

    // --- Der gebuendelte Betrag ----------------------------------------------

    @Test
    @DisplayName("die Zahl kommt GEBUENDELT aus B05, nicht je Einzelschlag")
    void theamountIsTheAggregatedOne() {
        // FR-041. B05 buendelt seit Monaten fuer genau diesen Zweck; eine Zahl je Schlag waere bei
        // einer Angriffsfolge ein Zahlenregen.
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        numbers.subscribeTo(bus);

        bus.publish(
                new DamageDealtEvent(
                        dealer.getUniqueId(), targetId, DamageType.PHYSICAL, 240.0, 4, false));
        scheduler.runLocationTasks();

        assertThat(displays()).hasSize(1);
        assertThat(plain(displays().get(0))).contains("240").contains("4");
    }

    @Test
    @DisplayName("ein toedlicher Treffer bekommt einen eigenen Schluessel")
    void alethalHitGetsItsOwnKey() {
        // Der letzte Treffer ist die einzige Zahl, auf die ein Spieler wirklich wartet - der eigene
        // Schluessel laesst den Betreiber ihn hervorheben.
        numbers.show(number(99.0, 1, true));
        scheduler.runLocationTasks();

        assertThat(plain(displays().get(0))).contains("LETHAL");
    }

    @Test
    @DisplayName("ein Ziel, das es nicht mehr gibt, ergibt keine Zahl")
    void agoneTargetYieldsNothing() {
        // Ein normaler Ausgang: der letzte Treffer trifft oft eine Kreatur, die im selben Tick
        // entfernt wird.
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        numbers.subscribeTo(bus);

        bus.publish(
                new DamageDealtEvent(
                        dealer.getUniqueId(),
                        UUID.randomUUID(),
                        DamageType.PHYSICAL,
                        50.0,
                        1,
                        false));

        assertThat(scheduler.locationTasks).isEmpty();
    }

    @Test
    @DisplayName("ein Verursacher, der offline ist, kostet keinen Tick")
    void anofflineDealerCostsNoTick() {
        assertThatCode(
                        () -> {
                            numbers.show(
                                    new DamageNumber(
                                            UUID.randomUUID(),
                                            new WorldPosition(world.getUID(), 1, 64, 1),
                                            10.0,
                                            1,
                                            false,
                                            T0.plusMillis(1200)));
                            scheduler.runLocationTasks();
                        })
                .doesNotThrowAnyException();
        assertThat(displays()).isEmpty();
    }

    // --- Aufbau ---------------------------------------------------------------

    private DamageNumber number(double amount, int hits, boolean lethal) {
        return new DamageNumber(
                dealer.getUniqueId(),
                new WorldPosition(world.getUID(), 10.0, 65.4, 20.0),
                amount,
                hits,
                lethal,
                T0.plusMillis(1200));
    }

    private List<TextDisplay> displays() {
        return world.getEntitiesByClass(TextDisplay.class).stream().toList();
    }

    private static String plain(TextDisplay display) {
        return display.text() == null
                ? ""
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(display.text());
    }

    private UiConfig config() {
        return new UiConfig(
                "en",
                Duration.ofSeconds(1),
                SurfaceSetting.on(),
                SurfaceSetting.on(),
                SurfaceSetting.on(),
                Duration.ofSeconds(4),
                new DamageNumberSetting(enabled, Duration.ofMillis(1200), 1.4));
    }

    private static Messages messages() {
        return MapMessages.fromNested(
                Map.of(
                        "ui",
                        Map.of(
                                "damage",
                                Map.of(
                                        "number", "&f{amount} x{hits}",
                                        "number-lethal", "&cLETHAL {amount} x{hits}"))));
    }

    /**
     * Hält die eingeplanten Aufgaben fest, statt sie sofort auszuführen.
     *
     * <p><b>Das ist hier das Prüfobjekt und keine Bequemlichkeit</b>: FR-044 fragt, <em>wann</em>
     * die Entfernung eingeplant wird — beim Setzen und nicht später. Ein Scheduler, der alles
     * sofort ausführte, könnte das nicht unterscheiden.
     */
    private static final class RecordingScheduler implements Scheduler {

        final List<Runnable> locationTasks = new ArrayList<>();
        final List<Runnable> entityDelayed = new ArrayList<>();
        final List<Duration> entityDelays = new ArrayList<>();

        void runLocationTasks() {
            List<Runnable> due = List.copyOf(locationTasks);
            locationTasks.clear();
            due.forEach(Runnable::run);
        }

        void runEntityDelayed() {
            List<Runnable> due = List.copyOf(entityDelayed);
            entityDelayed.clear();
            due.forEach(Runnable::run);
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            locationTasks.add(task);
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            entityDelayed.add(task);
            entityDelays.add(delay);
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            task.run();
            return handle();
        }

        private static TaskHandle handle() {
            return new TaskHandle() {

                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}

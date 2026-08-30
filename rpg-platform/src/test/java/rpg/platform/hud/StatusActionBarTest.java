package rpg.platform.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.progression.LevelUpEvent;
import rpg.core.progression.ProgressChangedEvent;
import rpg.core.progression.ProgressView;
import rpg.core.stats.ChangeCause;
import rpg.core.stats.ResourceChangedEvent;
import rpg.core.stats.ResourceKind;

/**
 * Die eigene Gesundheit, Mana und Verteidigung auf der Actionbar.
 *
 * <p>Zwei Auslöser: jede Änderung einer Ressource und jede Neuberechnung - und dazu eine stetige
 * Auffrischung. Die ist nicht vermeidbar: Minecraft blendet eine Actionbar nach etwa zwei Sekunden
 * aus, eine dauerhafte Anzeige heißt also erneut senden.
 */
class StatusActionBarTest {

    private static final Logger QUIET = Logger.getLogger("status-action-bar-test");

    private ServerMock server;
    private HudFixture.Statuses statuses;
    private HudFixture.RecordingScheduler scheduler;
    private StatusActionBar bar;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        statuses = new HudFixture.Statuses();
        scheduler = new HudFixture.RecordingScheduler();
        bar = new StatusActionBar(server, statuses, scheduler, HudFixture.messages(), QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Zeile nennt Leben, Maximum, Prozent, MANA und Verteidigung")
    void theLineCarriesEveryNumber() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player)).isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148");
    }

    @Test
    @DisplayName("ohne Mana faellt der Manateil weg - 0/0 saehe aus wie ein Fehler")
    void withoutManaThatPartIsOmitted() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .as("eine Zahl, die nichts sagt, nimmt dreien den Platz weg, die etwas sagen")
                .isEqualTo("620/2000 HP (31%) DEF 148");
    }

    @Test
    @DisplayName("wer einen Zaehler hat, liest ihn mit - beim Berserker die Wut")
    void aHolderWithAMeterReadsItToo() {
        PlayerMock player = server.addPlayer();
        statuses.giveWithMeter(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0, 47.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player)).isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148 RAGE 47");
    }

    @Test
    @DisplayName("wer keinen hat, bekommt auch keine Null - Magier und Rogue lesen drei Zahlen")
    void withoutAMeterThatPartIsOmitted() {
        // Kein Platzhalter, der leer bleibt, und keine Null, die nichts bedeutet: dieselbe
        // Entscheidung wie beim Mana eines Mobs. WELCHE Klasse einen Zaehler hat, steht dabei
        // nirgends im Code - sie hat einen, wenn eine ihrer Faehigkeiten einen METER-Effekt traegt.
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148")
                .doesNotContain("RAGE");
    }

    @Test
    @DisplayName("gerundet, nicht mit Nachkommastellen - die Bruchteile sind Rauschen")
    void valuesAreRounded() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 1234.7, 2000.0, 74.6, 300.0, 147.6);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .contains("1235/2000")
                .contains("75/300")
                .contains("DEF 148");
    }

    @Test
    @DisplayName("jede Gesundheitsänderung zeichnet neu, ohne dass jemand die Leiste ruft")
    void aHealthChangeRedrawsIt() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 500.0, 1000.0, 20.0);
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(healthChange(player.getUniqueId()));

        assertThat(actionBarOf(player)).contains("500/1000").contains("(50%)");
    }

    @Test
    @DisplayName("eine Manaänderung zeichnet die Zeile neu - Mana steht jetzt darauf")
    void aManaChangeRedrawsToo() {
        // UMGEKEHRT statt gelöscht. Vorher stand hier, dass eine Manaänderung NICHT neu zeichnet:
        // die Zeile nannte Mana nicht, also wäre das ein Paket ohne sichtbaren Unterschied gewesen.
        // Seit Mana auf der Zeile steht, ist das Gegenteil richtig - ohne diese Neuzeichnung hinkten
        // die Kosten eines Zaubers bis zu einer Sekunde nach, lang genug um wie ein verschluckter
        // Klick auszusehen.
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 500.0, 1000.0, 40.0, 200.0, 20.0);
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(
                new ResourceChangedEvent(
                        player.getUniqueId(),
                        UUID.randomUUID(),
                        ResourceKind.MANA,
                        10.0,
                        5.0,
                        50.0,
                        ChangeCause.DELTA));

        assertThat(scheduler.entityTasks).as("eingeplant und gezeichnet").isEqualTo(1);
    }

    @Test
    @DisplayName("ein Mob löst dieselben Ereignisse aus, bekommt aber keine Actionbar")
    void aMobGetsNoActionBar() {
        UUID mobId = UUID.randomUUID();
        statuses.give(mobId, 80.0, 80.0, 10.0);

        bar.show(mobId);

        assertThat(scheduler.entityTasks).as("die Aufgabe lief, fand aber keinen Spieler").isEqualTo(1);
    }

    @Test
    @DisplayName("ohne Werte passiert nichts - es wird nicht einmal eingeplant")
    void withoutAStatusNothingIsScheduled() {
        bar.show(UUID.randomUUID());

        assertThat(scheduler.entityTasks).isZero();
    }

    // theRefreshDrawsForEveryone() und theRefreshReschedulesItself() standen hier bis B13.
    //
    // Sie pruefen den Takt, und der ist UMGEZOGEN: rpg.platform.ui.HudTick ist die Erweiterung
    // genau dieses Takts und kein zweiter daneben (R1). Ihre Nachfolger stehen in HudTickTest als
    // "T036: ueber mehrere Durchlaeufe bleibt es bei EINER eingeplanten Aufgabe" und "T037: der
    // Takt bewaffnet sich am Ende jedes Durchlaufs neu" - und sie pruefen dort MEHR als vorher,
    // naemlich auch, dass es bei einer Einplanung bleibt.
    //
    // Sie hier stehenzulassen haette bedeutet, den alten Takt am Leben zu halten, damit sein Test
    // gruen bleibt. Das ist die Sorte Test, die eine Umsetzung festnagelt statt eine Zusage.

    @Test
    @DisplayName("die Zeile zeichnet fuer jeden Spielenden, nicht nur im Kampf")
    void thelineDrawsForEveryone() {
        // Was von den zwei Takttests hierher gehoert: dass show(...) fuer jeden zeichnet und nicht
        // nur fuer den, der gerade kaempft. Das ist eine Aussage ueber die Zeile und nicht ueber
        // ihren Anlass - und die bleibt bei B05/B06.
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        statuses.give(first.getUniqueId(), 100.0, 200.0, 5.0);
        statuses.give(second.getUniqueId(), 60.0, 200.0, 5.0);

        bar.show(first.getUniqueId());
        bar.show(second.getUniqueId());

        assertThat(actionBarOf(first)).contains("100/200");
        assertThat(actionBarOf(second)).contains("60/200");
    }

    @Test
    @DisplayName("T047a: der Fortschritt steht NICHT mehr auf der Actionbar")
    void progressIsNoLongerOnTheLine() {
        // Die Umkehrung des Tests, der bis B13 hier stand. FR-002 gab der Actionbar den
        // Fortschritt, FR-005 der Sidebar Level und Erfahrung - dieselben Zahlen. Die Sidebar
        // behaelt sie (FR-002a), und das ist der eine Wert, den dieser Block einer Flaeche
        // WEGNIMMT statt hinzuzufuegen.
        //
        // Dieser Test ist die Gegenprobe zu den drei verbleibenden Werten: er allein unterscheidet
        // "entfernt" von "vergessen".
        PlayerMock player = server.addPlayer();
        statuses.giveWithProgress(
                player.getUniqueId(),
                620.0,
                2000.0,
                75.0,
                300.0,
                148.0,
                new ProgressView(12, 340L, 1200L, false));

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148")
                .doesNotContain("LV")
                .doesNotContain("XP")
                .doesNotContain("{progress}");
    }

    @Test
    @DisplayName("T047: Leben, Mana und Verteidigung stehen nach dem Umzug unveraendert da")
    void theThreeRemainingValuesSurvivedTheMove() {
        // Die andere Haelfte der Zusage aus FR-023: beim Umzug hinter HudRenderer darf kein Wert
        // VERSEHENTLICH verschwinden. Zusammen mit dem Test darueber ist die Zeile vollstaendig
        // beschrieben - drei Werte da, einer bewusst weg.
        PlayerMock player = server.addPlayer();
        statuses.giveWithProgress(
                player.getUniqueId(),
                620.0,
                2000.0,
                75.0,
                300.0,
                148.0,
                new ProgressView(60, 4120L, 0L, true));

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .contains("620/2000")
                .contains("31%")
                .contains("75/300")
                .contains("148");
    }

    // atTheMaximumTheThresholdIsNotPrinted() stand hier bis B13.
    //
    // Der Fall gilt weiter, aber eine Flaeche weiter: "am Hoechstlevel steht kein 4120/0" ist jetzt
    // eine Aussage ueber die Sidebar. Sein Nachfolger heisst in SidebarLinesTest "am Hoechstlevel
    // traegt die Erfahrungszeile ihren eigenen Schluessel". ProgressView.atMaxLevel() beantwortet
    // die Frage dort weiterhin als eigenes Feld und nicht als abgeleitete Regel.

    @Test
    @DisplayName("der Zaehler bleibt auf der Zeile - er ist ein Kampfwert und kein Fortschritt")
    void themeterStaysOnTheLine() {
        // Der Berserker-Zaehler wandert NICHT mit: er gehoert zu den laufenden Kampfwerten, die
        // FR-002 der Actionbar zuweist, und nicht zum Fortschritt. Der Test steht hier, damit der
        // Umzug des Fortschritts ihn nicht versehentlich mitnimmt.
        PlayerMock player = server.addPlayer();
        statuses.giveWithMeterAndProgress(
                player.getUniqueId(),
                620.0,
                2000.0,
                75.0,
                300.0,
                148.0,
                47.0,
                new ProgressView(12, 340L, 1200L, false));

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148 RAGE 47")
                .doesNotContain("LV");
    }

    @Test
    @DisplayName("ohne Charakter bleibt kein {progress} stehen - ein Betreiber ohne Klasse")
    void withoutProgressNoPlaceholderIsLeftBehind() {
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 620.0, 2000.0, 75.0, 300.0, 148.0);

        bar.show(player.getUniqueId());

        assertThat(actionBarOf(player))
                .as("auf einer Zeile, die jede Sekunde neu kommt, waere das eine Ruine")
                .isEqualTo("620/2000 HP (31%) 75/300 MP DEF 148")
                .doesNotContain("{progress}")
                .doesNotContain("LV");
    }

    @Test
    @DisplayName("ein Erfahrungsgewinn zeichnet die Zeile neu, ueber den Spieler im Ereignis")
    void anExperienceGainRedrawsIt() {
        PlayerMock player = server.addPlayer();
        statuses.giveWithProgress(
                player.getUniqueId(),
                500.0,
                1000.0,
                40.0,
                200.0,
                20.0,
                new ProgressView(12, 340L, 1200L, false));
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(
                new ProgressChangedEvent(
                        UUID.randomUUID(), player.getUniqueId(), 25L, 12, 340L, 1200L));

        // SEIT B13 zeichnet die Actionbar auf dieses Ereignis NICHT mehr (T050b). Das Abonnement
        // ist nach rpg.platform.ui.HudRefresh umgezogen, weil Level und Erfahrung auf der Sidebar
        // stehen (FR-002a) - ein Aufstieg muss DORT ankommen, nicht hier.
        //
        // VERSCHOBEN und nicht kopiert: zwei Abonnements auf dasselbe Ereignis hiessen zweimal
        // zeichnen, und welcher Aufruf zuletzt kommt, haengt an der Registrierungsreihenfolge.
        // Sein Nachfolger heisst in HudRefreshEventsTest "ein Erfahrungsgewinn zeichnet die
        // Sidebar sofort neu".
        assertThat(player.nextActionBar())
                .as("kein Fortschrittsereignis mehr auf dieser Flaeche")
                .isNull();
    }

    @Test
    @DisplayName("ein Stufenaufstieg zeichnet die Actionbar NICHT mehr - er gehoert der Sidebar")
    void alevelUpNoLongerRedrawsTheActionBar() {
        PlayerMock player = server.addPlayer();
        statuses.giveWithProgress(
                player.getUniqueId(),
                500.0,
                1000.0,
                40.0,
                200.0,
                20.0,
                new ProgressView(13, 0L, 1400L, false));
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(new LevelUpEvent(UUID.randomUUID(), player.getUniqueId(), 12, 13, true));

        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    @DisplayName("die beiden Kampfereignisse zeichnen weiterhin - sie sind nicht mitgewandert")
    void thetwoCombatEventsStillRedraw() {
        // Die Gegenprobe zu den zwei Tests darueber: ResourceChangedEvent und
        // StatsRecalculatedEvent gehoeren zu Leben, Mana und Verteidigung und bleiben hier. Ohne
        // diesen Test koennte der Umzug alle vier Abonnements mitgenommen haben, und drei davon
        // waeren still verloren.
        PlayerMock player = server.addPlayer();
        statuses.give(player.getUniqueId(), 500.0, 1000.0, 40.0, 200.0, 20.0);
        EventBus eventBus = new DefaultEventBus(QUIET);
        bar.subscribeTo(eventBus);

        eventBus.publish(healthChange(player.getUniqueId()));

        assertThat(actionBarOf(player)).contains("500/1000");
    }

    // --- fixtures ---

    private static ResourceChangedEvent healthChange(UUID playerId) {
        return new ResourceChangedEvent(
                playerId,
                UUID.randomUUID(),
                ResourceKind.HEALTH,
                600.0,
                500.0,
                1000.0,
                ChangeCause.DELTA);
    }

    private static String actionBarOf(PlayerMock player) {
        var component = player.nextActionBar();
        return component == null
                ? null
                : PlainTextComponentSerializer.plainText()
                        .serialize(component)
                        .replaceAll("\\s+", " ")
                        .trim();
    }
}

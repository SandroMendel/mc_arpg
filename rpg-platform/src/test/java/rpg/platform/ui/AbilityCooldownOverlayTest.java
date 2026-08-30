package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.ability.Ability;
import rpg.core.classes.AbilityKind;
import rpg.core.message.MessageKey;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Das Vanilla-Cooldown-Overlay (T096, T097).
 *
 * <p>Keine eigene Fläche, keine Zahl, keine Textzeile — die graue Sweep-Animation, die jeder von der
 * Enderperle kennt. Der Test prüft, dass sie <b>auf dem richtigen Material</b> landet und dass sie
 * eine Wiederanmeldung übersteht.
 *
 * <p><b>Ohne einen AbilityRegistry-Doppelgänger</b> geht das nicht sinnvoll: die Restzeit kommt aus
 * B08, und ihn hier echt aufzubauen hieße, eine Datenbank und drei Module mitzubringen für eine
 * Frage, die {@code Duration} lautet. Was der Test deshalb <em>nicht</em> beweist, steht am Ende.
 */
class AbilityCooldownOverlayTest {

    private static final Instant T0 = Instant.parse("2026-08-30T20:00:00Z");

    private ServerMock server;
    private PlayerMock player;
    private final UUID characterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("T096: eine ausgeloeste Faehigkeit setzt den Cooldown auf IHREM Material")
    void atriggeredAbilitySetsTheCooldownOnItsMaterial() {
        AbilityCooldownOverlay overlay = overlay();

        overlay.apply(
                player.getUniqueId(),
                active("cleave", "IRON_SWORD"),
                Duration.ofSeconds(5));

        assertThat(player.getCooldown(Material.IRON_SWORD)).isPositive();
    }

    @Test
    @DisplayName("T096: und auf keinem zweiten")
    void andOnNoOther() {
        // Die Zusage, die FR-030 gibt: keine eigene Flaeche und kein Nebeneffekt auf anderen
        // Slots. Ein Overlay, das zu viel faerbt, sperrt eine bereite Faehigkeit optisch.
        AbilityCooldownOverlay overlay = overlay();

        overlay.apply(player.getUniqueId(), active("cleave", "IRON_SWORD"), Duration.ofSeconds(5));

        assertThat(player.getCooldown(Material.SHIELD)).isZero();
        assertThat(player.getCooldown(Material.BLAZE_ROD)).isZero();
    }

    @Test
    @DisplayName("T096: eine passive Faehigkeit faerbt ALLE ihre Materialien")
    void apassiveColoursEveryOneOfItsMaterials() {
        // Der Magier deckt mit Rise & Fall zwei Slots aus einer Faehigkeit. Eines davon grau zu
        // lassen saehe aus, als haette nur die Haelfte ausgeloest.
        AbilityCooldownOverlay overlay = overlay();

        overlay.apply(
                player.getUniqueId(),
                passive("rise-and-fall", List.of("FEATHER", "PHANTOM_MEMBRANE")),
                Duration.ofSeconds(3));

        assertThat(player.getCooldown(Material.FEATHER)).isPositive();
        assertThat(player.getCooldown(Material.PHANTOM_MEMBRANE)).isPositive();
    }

    @Test
    @DisplayName("T096: die Restzeit wird auf Ticks AUFGERUNDET, nicht abgeschnitten")
    void theremainderIsRoundedUp() {
        // Nach unten gerundet waere das Item eine Zwanzigstelsekunde zu frueh frei - sichtbar als
        // ein Klick, den B08 noch ablehnt, waehrend das Item schon bereit aussieht.
        AbilityCooldownOverlay overlay = overlay();

        overlay.apply(player.getUniqueId(), active("cleave", "IRON_SWORD"), Duration.ofMillis(30));

        assertThat(player.getCooldown(Material.IRON_SWORD)).isEqualTo(1);
    }

    @Test
    @DisplayName("T096: eine Faehigkeit ohne Material tut nichts")
    void anabilityWithoutAMaterialDoesNothing() {
        AbilityCooldownOverlay overlay = overlay();

        assertThatCode(
                        () ->
                                overlay.apply(
                                        player.getUniqueId(),
                                        passive("evasion", List.of()),
                                        Duration.ofSeconds(5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("T096: ein unbekanntes Material wird uebergangen statt zu werfen")
    void anunknownMaterialIsSkipped() {
        // Beim Start bereits abgefangen - hier still uebergehen statt den Aufrufer mitzureissen.
        AbilityCooldownOverlay overlay = overlay();

        assertThatCode(
                        () ->
                                overlay.apply(
                                        player.getUniqueId(),
                                        active("cleave", "NOT_A_MATERIAL"),
                                        Duration.ofSeconds(5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("T097: ohne Charakter wird nichts wiederhergestellt")
    void withoutACharacterNothingIsRestored() {
        AbilityCooldownOverlay overlay =
                new AbilityCooldownOverlay(
                        server,
                        new DirectScheduler(),
                        registry(),
                        id -> Optional.empty(),
                        Clock.fixed(T0, ZoneOffset.UTC));

        overlay.restore(player.getUniqueId());

        assertThat(player.getCooldown(Material.IRON_SWORD)).isZero();
    }

    @Test
    @DisplayName("der Sekundenabgleich faengt einen Cooldown, der NICHT beim Ausloesen begann")
    void thesecondlyReconciliationCatchesALateCooldown() {
        // DER FALL, DEN DER ERSTE ENTWURF NICHT SAH (Serverabnahme, Schritt 8).
        //
        // Eine ANHALTENDE Faehigkeit (sustained: true) startet ihren Cooldown, wenn sie ENDET -
        // nicht beim Ausloesen. Beim Krieger sind das shield, whirl und call-of-the-berserker, beim
        // Schurken invisibility. Der Ausloesepfad allein liess deshalb AUSSCHLIESSLICH Leap grau
        // werden: die einzige aktive Faehigkeit des Kriegers ohne sustained.
        //
        // Ein Fehler, der bei einer von vier funktioniert - und deshalb wie ein Sonderfall aussieht.
        MovingRegistry registry = new MovingRegistry();
        AbilityCooldownOverlay overlay =
                new AbilityCooldownOverlay(
                        server,
                        new DirectScheduler(),
                        registry.registry(),
                        id -> Optional.of(characterId),
                        Clock.fixed(T0, ZoneOffset.UTC));

        // Erster Abgleich: nichts kuehlt ab, also faerbt nichts.
        overlay.refresh(player.getUniqueId());
        assertThat(player.getCooldown(Material.IRON_SWORD)).isZero();

        // Die Faehigkeit endet - JETZT beginnt ihr Cooldown, ohne dass jemand ausgeloest hat.
        registry.startCooldown(Duration.ofSeconds(5));
        overlay.refresh(player.getUniqueId());

        assertThat(player.getCooldown(Material.IRON_SWORD))
                .as("der Takt hat ihn gefunden, obwohl der Ausloesepfad nichts gemeldet hat")
                .isPositive();
    }

    @Test
    @DisplayName("derselbe Cooldown wird nicht jede Sekunde neu gesendet")
    void thesameCooldownIsNotResentEverySecond() {
        // setCooldown ist ein Paket. Bei 200 Spielern mal sieben Faehigkeiten je Sekunde waeren das
        // 1400 fuer eine Anzeige, die sich nicht aendert.
        MovingRegistry registry = new MovingRegistry();
        AbilityCooldownOverlay overlay =
                new AbilityCooldownOverlay(
                        server,
                        new DirectScheduler(),
                        registry.registry(),
                        id -> Optional.of(characterId),
                        Clock.fixed(T0, ZoneOffset.UTC));
        registry.startCooldown(Duration.ofSeconds(5));

        overlay.refresh(player.getUniqueId());
        int afterFirst = player.getCooldown(Material.IRON_SWORD);
        player.setCooldown(Material.IRON_SWORD, 0);

        overlay.refresh(player.getUniqueId());

        assertThat(afterFirst).isPositive();
        assertThat(player.getCooldown(Material.IRON_SWORD))
                .as("beim zweiten Mal wurde nichts gesendet - der Endzeitpunkt ist derselbe")
                .isZero();
    }

    @Test
    @DisplayName("ein NEUER Cooldown derselben Faehigkeit wird wieder gesendet")
    void anewCooldownOnTheSameAbilityIsSentAgain() {
        // Die Gegenprobe: der Merker darf nicht dazu fuehren, dass eine zweite Ausloesung stumm
        // bleibt.
        MovingRegistry registry = new MovingRegistry();
        AbilityCooldownOverlay overlay =
                new AbilityCooldownOverlay(
                        server,
                        new DirectScheduler(),
                        registry.registry(),
                        id -> Optional.of(characterId),
                        Clock.fixed(T0, ZoneOffset.UTC));

        registry.startCooldown(Duration.ofSeconds(5));
        overlay.refresh(player.getUniqueId());
        player.setCooldown(Material.IRON_SWORD, 0);

        // Abgelaufen, dann neu - ein anderer Endzeitpunkt.
        registry.startCooldown(Duration.ZERO);
        overlay.refresh(player.getUniqueId());
        registry.startCooldown(Duration.ofSeconds(9));
        overlay.refresh(player.getUniqueId());

        assertThat(player.getCooldown(Material.IRON_SWORD)).isPositive();
    }

    // T097 - der Teil, den dieser Test NICHT beweist:
    //
    // "Nach Ab- und Anmeldung steht die VERBLEIBENDE Restzeit da" haengt an zwei Dingen, die beide
    // ausserhalb dieser Klasse liegen: dass B08 den Cooldown ueber Zeitstempel fuehrt (also
    // ueberlebt er die Abmeldung von selbst) und dass die Verdrahtung restore(...) beim
    // Sitzungsbeginn ruft. Das erste ist B08s Zusage und dort geprueft; das zweite ist eine Zeile
    // in RpgPlugin.onSessionReady und gehoert zur Serverabnahme (quickstart §5, Schritt 9).
    //
    // DIESE LUECKE IST BENANNT, damit sie nicht als erledigt gilt. Ein Test, der hier eine
    // Wiederanmeldung nachstellte, pruefte seinen eigenen Doppelgaenger - genau die Sorte gruener
    // Test, die an der Stelle vorbeischaut, an der es schiefgeht.

    // --- Aufbau ---------------------------------------------------------------

    /**
     * Ein Verzeichnis, dessen Cooldown sich stellen lässt.
     *
     * <p>Genau eine aktive Fähigkeit auf {@code IRON_SWORD}. Mehr braucht die Frage nicht — und
     * weniger hätte sie nicht beantwortet: der Fehler aus Schritt 8 hing daran, <em>wann</em> ein
     * Cooldown beginnt, nicht daran, welche Fähigkeit ihn hat.
     */
    private final class MovingRegistry {

        private final rpg.core.ability.AbilityRegistry registry;

        MovingRegistry() {
            rpg.core.classes.AbilityBinding binding =
                    new rpg.core.classes.AbilityBinding("cleave", AbilityKind.ACTIVE, false, 1);
            this.registry =
                    new rpg.core.ability.AbilityRegistry(
                            new rpg.core.ability.AbilityConfig(
                                    Map.of("cleave", active("cleave", "IRON_SWORD")),
                                    Duration.ofSeconds(1),
                                    1.0,
                                    1.0),
                            id -> rpg.core.session.CharacterClass.WARRIOR,
                            characterClass -> List.of(binding),
                            id -> List.of(binding),
                            Clock.fixed(T0, ZoneOffset.UTC));
        }

        /**
         * Setzt den Cooldown so, wie B08 ihn setzen würde.
         *
         * <p><b>Über {@code put(stateOf(...).withCooldown(...))} und nicht über ein Double</b>: das
         * ist derselbe Weg, den {@code AbilityRuntime.startCooldown} geht. Ein Doppelgänger hätte
         * hier jede Antwort geliefert, die der Test hören will — und genau deshalb hätte er den
         * Fehler aus Schritt 8 nicht gefunden.
         */
        void startCooldown(Duration duration) {
            registry.put(
                    duration.isZero()
                            ? rpg.core.ability.AbilityState.initial(characterId, "cleave")
                            : registry.stateOf(characterId, "cleave")
                                    .withCooldown(T0.plus(duration)));
        }

        rpg.core.ability.AbilityRegistry registry() {
            return registry;
        }
    }

    private AbilityCooldownOverlay overlay() {
        return new AbilityCooldownOverlay(
                server,
                new DirectScheduler(),
                registry(),
                id -> Optional.of(characterId),
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static rpg.core.ability.AbilityRegistry registry() {
        // Nicht benutzt in den Tests, die apply(...) direkt rufen - restore(...) braucht ihn, und
        // dort endet der Weg bei classOf(...) ohne Klasse.
        return new rpg.core.ability.AbilityRegistry(
                new rpg.core.ability.AbilityConfig(Map.of(), Duration.ofSeconds(1), 1.0, 1.0),
                // Kein Charakter hat eine Klasse: restore(...) endet damit sauber, ohne dass ein
                // Verzeichnis voller Faehigkeiten aufgebaut werden muesste.
                id -> null,
                characterClass -> List.of(),
                id -> List.of(),
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static Ability active(String id, String material) {
        return ability(id, AbilityKind.ACTIVE, List.of(material), java.util.Set.of());
    }

    private static Ability passive(String id, List<String> materials) {
        return ability(
                id,
                AbilityKind.PASSIVE,
                materials,
                java.util.Set.of(rpg.core.ability.AbilityTrigger.ALWAYS));
    }

    private static Ability ability(
            String id,
            AbilityKind kind,
            List<String> materials,
            java.util.Set<rpg.core.ability.AbilityTrigger> triggers) {
        return new Ability(
                id,
                kind,
                MessageKey.of("ability." + id + ".name"),
                MessageKey.of("ability." + id + ".description"),
                0.0,
                Duration.ofSeconds(5),
                Duration.ZERO,
                false,
                false,
                Duration.ZERO,
                1,
                // chargeWindow NULL: "ein Fenster bedeutet nichts bei einer einzigen Ladung".
                null,
                false,
                false,
                false,
                false,
                triggers,
                0.0,
                rpg.core.ability.TargetSpec.self(),
                // Ein Effekt, weil Ability mindestens einen verlangt: "eine Faehigkeit ohne Wirkung
                // ist immer ein Versehen". Fuer das Overlay zaehlt er nicht - es liest nur die
                // Materialien und die Restzeit.
                List.of(healEffect()),
                1,
                Map.of(),
                materials);
    }

    private static rpg.core.ability.EffectSpec healEffect() {
        return new rpg.core.ability.EffectSpec(
                rpg.core.ability.EffectType.HEAL,
                1.0,
                0.0,
                Duration.ZERO,
                // interval NULL und nicht ZERO: ein gesetztes Intervall verlangt eine Dauer
                // ("ohne die wuerde es nie enden"), und ZERO ist gesetzt.
                null,
                1,
                null,
                null,
                null,
                java.util.Set.of(),
                null,
                null,
                Duration.ZERO,
                null,
                false,
                null);
    }

    /** Führt alles sofort aus — der Sprung in den Tick ist hier nicht das Prüfobjekt. */
    private static final class DirectScheduler implements Scheduler {

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            task.run();
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

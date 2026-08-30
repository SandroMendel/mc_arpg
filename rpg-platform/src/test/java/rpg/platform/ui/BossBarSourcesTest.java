package rpg.platform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageType;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.DamageNumberSetting;
import rpg.core.ui.SurfaceSetting;
import rpg.core.ui.UiConfig;
import rpg.core.zone.ZoneChangedEvent;
import rpg.platform.hud.CombatStatusSource;

/**
 * Woher die Bossbar ihre drei Anlässe bekommt (T051–T053) — und wer gewinnt, wenn zwei zusammen
 * anstehen (T068).
 *
 * <p><b>Alle drei rechnen gegen eine gestellte Uhr.</b> Zonenhinweis und Bosskampf laufen ab, statt
 * von einer eingeplanten Aufgabe entfernt zu werden (Constitution II.2: keine Aufgabe je Spieler) —
 * mit {@code Instant.now()} wäre keiner der Ablauffälle ansteuerbar.
 */
class BossBarSourcesTest {

    private static final Instant T0 = Instant.parse("2026-08-30T18:00:00Z");

    private final MovableClock clock = new MovableClock();
    private final UUID playerId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();

    // --- T051: der Zonenname --------------------------------------------------

    @Test
    @DisplayName("T051: ein ZoneChangedEvent mit to erzeugt den Anlass ZONE_NAME")
    void azoneChangeCreatesTheOccasion() {
        ZoneNoticeSource source = zoneSource();
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);

        bus.publish(new ZoneChangedEvent(characterId, Optional.empty(), Optional.of("Ashen Reach")));

        assertThat(source.contentFor(playerId))
                .get()
                .satisfies(
                        content -> {
                            assertThat(content.occasion()).isEqualTo(BossBarOccasion.ZONE_NAME);
                            assertThat(content.values()).containsEntry("zone", "Ashen Reach");
                        });
    }

    @Test
    @DisplayName("T051: er verfaellt nach der konfigurierten Dauer - ohne eingeplante Aufgabe")
    void itExpiresAfterTheConfiguredDuration() {
        ZoneNoticeSource source = zoneSource();
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);
        bus.publish(new ZoneChangedEvent(characterId, Optional.empty(), Optional.of("Ashen Reach")));

        clock.advance(Duration.ofSeconds(3));
        assertThat(source.contentFor(playerId)).as("nach 3 s von 4 steht er noch").isPresent();

        clock.advance(Duration.ofSeconds(2));
        assertThat(source.contentFor(playerId)).as("nach 5 s ist er weg").isEmpty();
        assertThat(source.tracked()).as("und er hinterlaesst keinen Eintrag").isZero();
    }

    @Test
    @DisplayName("T051: der Weg in die Wildnis erzeugt keinen Hinweis")
    void leavingIntoTheWildernessSaysNothing() {
        // "du bist jetzt nirgendwo" ist keine Nachricht.
        ZoneNoticeSource source = zoneSource();
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);

        bus.publish(new ZoneChangedEvent(characterId, Optional.of("Ashen Reach"), Optional.empty()));

        assertThat(source.contentFor(playerId)).isEmpty();
    }

    @Test
    @DisplayName("T051: ohne Spieler hinter dem Charakter passiert nichts")
    void withoutAPlayerBehindTheCharacterNothingHappens() {
        // Das Ereignis traegt die CHARAKTERkennung, die Anzeige haengt am SPIELER. Mit der falschen
        // Kennung antwortet die Naht nicht mit einem Fehler, sondern mit einem leeren Optional -
        // und das saehe aus wie "keine Zone".
        ZoneNoticeSource source =
                new ZoneNoticeSource(this::config, id -> Optional.empty(), clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);

        bus.publish(new ZoneChangedEvent(characterId, Optional.empty(), Optional.of("Ashen Reach")));

        assertThat(source.tracked()).isZero();
    }

    // --- T052: der Bosskampf --------------------------------------------------

    @Test
    @DisplayName("T052: Schaden an einem Boss erzeugt BOSS_FIGHT mit seinem Fuellstand")
    void damageToABossCreatesTheOccasion() {
        UUID bossId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markBoss(bossId, "warden");
        Statuses statuses = new Statuses();
        statuses.give(bossId, 300.0, 1000.0);
        BossFightSource source = new BossFightSource(kinds, statuses, clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);

        bus.publish(new DamageDealtEvent(playerId, bossId, DamageType.PHYSICAL, 50.0, 1, false));

        assertThat(source.contentFor(playerId))
                .get()
                .satisfies(
                        content -> {
                            assertThat(content.occasion()).isEqualTo(BossBarOccasion.BOSS_FIGHT);
                            assertThat(content.fraction()).isEqualTo(0.3);
                            assertThat(content.values()).containsEntry("name", "warden");
                        });
    }

    @Test
    @DisplayName("T052: Schaden an einer gewoehnlichen Kreatur erzeugt nichts")
    void damageToAnOrdinaryMobCreatesNothing() {
        UUID mobId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markOrdinary(mobId, "zombie");
        BossFightSource source = new BossFightSource(kinds, new Statuses(), clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);

        bus.publish(new DamageDealtEvent(playerId, mobId, DamageType.PHYSICAL, 50.0, 1, false));

        assertThat(source.contentFor(playerId)).isEmpty();
    }

    @Test
    @DisplayName("T052: ein toter Boss verschwindet von selbst")
    void adeadBossDisappearsOnItsOwn() {
        // Der Fuellstand wird bei jedem Takt neu gelesen und nirgends gemerkt - deshalb reicht es,
        // dass statusOf fuer ihn leer antwortet. Ein gemerkter Wert stuende noch minutenlang da.
        UUID bossId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markBoss(bossId, "warden");
        Statuses statuses = new Statuses();
        statuses.give(bossId, 300.0, 1000.0);
        BossFightSource source = new BossFightSource(kinds, statuses, clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);
        bus.publish(new DamageDealtEvent(playerId, bossId, DamageType.PHYSICAL, 50.0, 1, false));

        statuses.remove(bossId);

        assertThat(source.contentFor(playerId)).isEmpty();
        assertThat(source.tracked()).isZero();
    }

    @Test
    @DisplayName("T052: der Balken laeuft nach dem letzten Treffer ab")
    void thebarExpiresAfterTheLastHit() {
        UUID bossId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markBoss(bossId, "warden");
        Statuses statuses = new Statuses();
        statuses.give(bossId, 300.0, 1000.0);
        BossFightSource source = new BossFightSource(kinds, statuses, clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        source.subscribeTo(bus);
        bus.publish(new DamageDealtEvent(playerId, bossId, DamageType.PHYSICAL, 50.0, 1, false));

        clock.advance(Duration.ofSeconds(6));

        assertThat(source.contentFor(playerId)).isEmpty();
    }

    // --- T068: die Rangfolge im Zusammenspiel --------------------------------

    @Test
    @DisplayName("T068: Zonenwechsel und Bosskampf treffen zusammen - der Bosskampf gewinnt")
    void thebossFightWinsOverTheZoneName() {
        UUID bossId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markBoss(bossId, "warden");
        Statuses statuses = new Statuses();
        statuses.give(bossId, 800.0, 1000.0);

        ZoneNoticeSource zone = zoneSource();
        BossFightSource boss = new BossFightSource(kinds, statuses, clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        zone.subscribeTo(bus);
        boss.subscribeTo(bus);

        bus.publish(new ZoneChangedEvent(characterId, Optional.empty(), Optional.of("Ashen Reach")));
        bus.publish(new DamageDealtEvent(playerId, bossId, DamageType.PHYSICAL, 50.0, 1, false));

        BossBarOccasions occasions = new BossBarOccasions(zone, boss);

        assertThat(occasions.contentFor(playerId))
                .get()
                .satisfies(c -> assertThat(c.occasion()).isEqualTo(BossBarOccasion.BOSS_FIGHT));
    }

    @Test
    @DisplayName("T068: der verdraengte Zonenname wird NICHT nachgeholt")
    void thedisplacedZoneNameIsNotReplayed() {
        UUID bossId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markBoss(bossId, "warden");
        Statuses statuses = new Statuses();
        statuses.give(bossId, 800.0, 1000.0);

        ZoneNoticeSource zone = zoneSource();
        BossFightSource boss = new BossFightSource(kinds, statuses, clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        zone.subscribeTo(bus);
        boss.subscribeTo(bus);
        BossBarOccasions occasions = new BossBarOccasions(zone, boss);

        bus.publish(new ZoneChangedEvent(characterId, Optional.empty(), Optional.of("Ashen Reach")));
        bus.publish(new DamageDealtEvent(playerId, bossId, DamageType.PHYSICAL, 50.0, 1, false));

        // Der Zonenname steht 4 s, der Kampf 5 s nach dem letzten Treffer. Bei 4,5 s ist der eine
        // abgelaufen und der andere nicht - genau das Fenster, in dem die Frage sich stellt.
        clock.advance(Duration.ofMillis(4500));
        assertThat(occasions.contentFor(playerId))
                .as("der Kampf steht noch, der Zonenname nicht mehr")
                .get()
                .satisfies(c -> assertThat(c.occasion()).isEqualTo(BossBarOccasion.BOSS_FIGHT));

        clock.advance(Duration.ofSeconds(2));

        // NICHTS kommt nach. Waere der Zonenname gemerkt worden, staende er jetzt hier - und zwar
        // als Meldung ueber etwas, das sieben Sekunden her ist.
        assertThat(occasions.contentFor(playerId)).isEmpty();
    }

    @Test
    @DisplayName("T068: der Bosskampf kehrt zurueck, ohne dass ihn jemand neu meldet")
    void thebossFightComesBackUnreported() {
        UUID bossId = UUID.randomUUID();
        Kinds kinds = new Kinds();
        kinds.markBoss(bossId, "warden");
        Statuses statuses = new Statuses();
        statuses.give(bossId, 800.0, 1000.0);

        BossFightSource boss = new BossFightSource(kinds, statuses, clock);
        EventBus bus = new DefaultEventBus(java.util.logging.Logger.getLogger("test"));
        boss.subscribeTo(bus);

        // Eine Kanalisierung, die zwei Sekunden lang laeuft und dann vorbei ist.
        HudRefresh.BossBarSource channelling =
                id ->
                        clock.instant().isBefore(T0.plusSeconds(2))
                                ? Optional.of(
                                        new HudRefresh.BossBarContent(
                                                BossBarOccasion.CHANNELLING,
                                                rpg.core.ui.UiMessageKeys.BOSSBAR_CHANNELLING,
                                                Map.of("ability", "rise"),
                                                0.5))
                                : Optional.empty();

        BossBarOccasions occasions = new BossBarOccasions(channelling, boss);
        bus.publish(new DamageDealtEvent(playerId, bossId, DamageType.PHYSICAL, 50.0, 1, false));

        assertThat(occasions.contentFor(playerId))
                .as("die Kanalisierung verdraengt den Kampf")
                .get()
                .satisfies(c -> assertThat(c.occasion()).isEqualTo(BossBarOccasion.CHANNELLING));

        clock.advance(Duration.ofSeconds(3));

        assertThat(occasions.contentFor(playerId))
                .as("der Kampf ist wieder da - sein Zustand bestand fort, er war nur verdeckt")
                .get()
                .satisfies(c -> assertThat(c.occasion()).isEqualTo(BossBarOccasion.BOSS_FIGHT));
    }

    @Test
    @DisplayName("T068: liegt nichts an, bekommt der Spieler keine Bossbar")
    void withNothingPendingThereIsNoBar() {
        BossBarOccasions occasions = new BossBarOccasions(zoneSource());

        assertThat(occasions.contentFor(playerId)).isEmpty();
    }

    // --- Aufbau ---------------------------------------------------------------

    private ZoneNoticeSource zoneSource() {
        return new ZoneNoticeSource(this::config, id -> Optional.of(playerId), clock);
    }

    private UiConfig config() {
        return new UiConfig(
                "en",
                Duration.ofSeconds(1),
                SurfaceSetting.on(),
                SurfaceSetting.on(),
                SurfaceSetting.on(),
                Duration.ofSeconds(4),
                new DamageNumberSetting(true, Duration.ofMillis(1200), 1.4));
    }

    /** Eine Uhr, die sich stellen lässt, ohne neu erzeugt zu werden. */
    private static final class MovableClock extends Clock {

        private Instant now = T0;

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Arten je Entität, so klein wie die Frage. */
    private static final class Kinds implements MobKinds {

        private final Map<UUID, MobKind> byEntity = new HashMap<>();

        void markBoss(UUID entityId, String key) {
            byEntity.put(entityId, kind(key, true));
        }

        void markOrdinary(UUID entityId, String key) {
            byEntity.put(entityId, kind(key, false));
        }

        @Override
        public Optional<MobKind> find(String kindKey) {
            return byEntity.values().stream().filter(k -> k.key().equals(kindKey)).findFirst();
        }

        @Override
        public Optional<MobKind> ofEntity(UUID entityId) {
            return Optional.ofNullable(byEntity.get(entityId));
        }

        @Override
        public List<MobKind> all() {
            return List.copyOf(byEntity.values());
        }

        private static MobKind kind(String key, boolean boss) {
            return new MobKind(
                    key,
                    "ZOMBIE",
                    1,
                    Map.of(),
                    16.0,
                    rpg.core.message.MessageKey.of("mob.kind." + key + ".name"),
                    10L,
                    5L,
                    boss);
        }
    }

    /** Lebenswerte je Halter — für Kreaturen genauso wie für Spieler. */
    private static final class Statuses implements CombatStatusSource {

        private final Map<UUID, Status> byHolder = new HashMap<>();

        void give(UUID holderId, double health, double maxHealth) {
            byHolder.put(holderId, new Status(health, maxHealth, 0.0, 0.0, 0.0));
        }

        void remove(UUID holderId) {
            byHolder.remove(holderId);
        }

        @Override
        public Optional<Status> statusOf(UUID holderId) {
            return Optional.ofNullable(byHolder.get(holderId));
        }
    }
}

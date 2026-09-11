package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DamageType;
import rpg.core.combat.DeathCause;
import rpg.core.message.MessageKey;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.persistence.StatisticsRepository;
import rpg.core.statistics.AccountLookup;
import rpg.core.statistics.RecordedStatistics;
import rpg.core.statistics.Statistics;

/**
 * FR-004, SC-011 — <b>ein kaputter Statistikdienst darf das Spiel nicht anhalten.</b>
 *
 * <p>Diese Zuhörer hängen im Kampfpfad. Eine durchgereichte Ausnahme macht dort etwas kaputt, das
 * mit Statistik nichts zu tun hat: einen Kill, der nicht zu Ende geführt wird, eine Beute, die
 * nicht fällt, einen Levelaufstieg, der hängen bleibt. Der Spieler sieht dann nicht „die Statistik
 * ist gestört", sondern „der Boss ist verschwunden und ich habe nichts bekommen".
 *
 * <p><b>Ein nicht gezählter Kill ist hinnehmbar, ein verlorener Kill nicht.</b>
 *
 * <p>Geprüft wird mit einem Dienst, der bei <em>jedem</em> Aufruf wirft — nicht mit einem, der
 * gelegentlich versagt. Ein gelegentlicher Fehler wäre der freundlichere Test und der schwächere:
 * er ließe offen, ob der Pfad ihn nur zufällig überlebt hat.
 */
class CaptureFailureDoesNotBreakTheGameTest {

    @Test
    @DisplayName("SC-011 - ein Kill laeuft weiter, auch wenn jede Zaehlung wirft")
    void akillSurvivesAThrowingStatisticsService() {
        UUID player = UUID.randomUUID();
        UUID creature = UUID.randomUUID();

        KillStatListener listener =
                new KillStatListener(
                        throwingStatistics(),
                        kinds(Map.of(creature, kind("rotling"))),
                        new AccountLookup(id -> Optional.empty()),
                        (victim, contributors) -> Set.of(),
                        () -> 0.05);

        assertThatCode(
                        () ->
                                listener.onDeath(
                                        new CombatDeathEvent(
                                                creature,
                                                null,
                                                player,
                                                DeathCause.COMBAT,
                                                new DamageShare(Map.of(player, 1.0), player, 100.0),
                                                false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SC-011 - ein Tod laeuft weiter")
    void adeathSurvivesToo() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();

        KillStatListener listener =
                new KillStatListener(
                        throwingStatistics(),
                        kinds(Map.of()),
                        new AccountLookup(id -> Optional.of(account)),
                        (victim, contributors) -> Set.of(),
                        () -> 0.05);

        assertThatCode(
                        () ->
                                listener.onDeath(
                                        new CombatDeathEvent(
                                                account,
                                                character,
                                                null,
                                                DeathCause.ENVIRONMENT,
                                                DamageShare.empty(),
                                                true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SC-011 - ein Schadensereignis laeuft weiter")
    void adamageEventSurvivesToo() {
        DamageStatListener listener =
                new DamageStatListener(throwingStatistics(), id -> Optional.empty());

        assertThatCode(
                        () ->
                                listener.onDamage(
                                        new DamageDealtEvent(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                DamageType.PHYSICAL,
                                                900.0,
                                                1,
                                                true)))
                .doesNotThrowAnyException();
    }

    /**
     * Ein Repository, das bei jedem Schreibversuch wirft.
     *
     * <p>Bewusst über {@link RecordedStatistics} und nicht über eine werfende {@link Statistics}:
     * so wird geprüft, dass die <b>ausgelieferte</b> Fassade den Fehler abfängt — und nicht nur,
     * dass ein Testdouble ihn nicht auslöst.
     */
    private static Statistics throwingStatistics() {
        return new RecordedStatistics(
                new StatisticsRepository() {

                    @Override
                    public void increment(UUID playerId, String metric, long delta) {
                        throw new IllegalStateException("database is on fire");
                    }

                    @Override
                    public void reportMax(UUID playerId, String metric, long value) {
                        throw new IllegalStateException("database is on fire");
                    }

                    @Override
                    public java.util.concurrent.CompletableFuture<Long> sum(
                            UUID playerId,
                            String metric,
                            java.time.LocalDate from,
                            java.time.LocalDate to) {
                        throw new IllegalStateException("database is on fire");
                    }

                    @Override
                    public java.util.concurrent.CompletableFuture<Long> total(
                            UUID playerId, String metric) {
                        throw new IllegalStateException("database is on fire");
                    }
                },
                Logger.getLogger("test"));
    }

    private static MobKind kind(String key) {
        return new MobKind(
                key, "ZOMBIE", 10, Map.of(), 16.0, MessageKey.of("mob." + key + ".name"), 10, 5, false);
    }

    private static MobKinds kinds(Map<UUID, MobKind> byEntity) {
        return new MobKinds() {

            @Override
            public Optional<MobKind> find(String kindKey) {
                return Optional.empty();
            }

            @Override
            public Optional<MobKind> ofEntity(UUID entityId) {
                return Optional.ofNullable(byEntity.get(entityId));
            }

            @Override
            public List<MobKind> all() {
                return List.copyOf(byEntity.values());
            }
        };
    }
}

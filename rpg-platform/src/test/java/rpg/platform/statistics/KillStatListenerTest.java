package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;
import rpg.core.message.MessageKey;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.statistics.AccountLookup;
import rpg.core.statistics.Metric;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Statistics;

/**
 * FR-006, FR-008, FR-010, FR-011 — jeder Fall landet unter dem richtigen Schlüssel.
 *
 * <p>Fünf Fälle, und vier davon sind Sonderfälle: ein Kill mit bekannter Art, ein Tod einer
 * Kreatur ohne Spielerbeteiligung, ein Tod durch eine Kreatur, ein Tod durch Sturz und ein Tod
 * durch einen anderen Spieler. Die Sonderfälle sind der eigentliche Inhalt — der Normalfall geht
 * fast von allein richtig, und was schiefgeht, geht an den Rändern schief.
 *
 * <p>Ohne MockBukkit: dieser Zuhörer hängt an B05s Ereignis, nicht an einem Bukkit-Ereignis, und
 * er fasst keine Entität an. Was er braucht, ist eine aufgelöste Art — und die kommt aus einer
 * Map.
 */
class KillStatListenerTest {

    private static final double THRESHOLD = 0.05;

    @Test
    @DisplayName("FR-006 - ein Kill mit bekannter Art zaehlt unter dieser Art")
    void akillWithAKnownKindCountsUnderThatKind() {
        UUID player = UUID.randomUUID();
        UUID creature = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(creature, kind("rotling", false)))
                .onDeath(creatureDeath(creature, Map.of(player, 1.0)));

        assertThat(recorder.counts)
                .containsExactly(entry(player, MetricRegistry.MOB_KILLS, "rotling", 1));
    }

    @Test
    @DisplayName("FR-009 - ein Boss zaehlt unter DEMSELBEN Schluessel wie jede andere Art")
    void abossCountsUnderTheSameKeyAsAnyOtherKind() {
        UUID player = UUID.randomUUID();
        UUID boss = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(boss, kind("dune-warlord", true)))
                .onDeath(creatureDeath(boss, Map.of(player, 1.0)));

        // Kein zweiter Zaehler fuer Bosskills: die Trennung passiert in der Aggregation. Sonst
        // stuende dieselbe Tat zweimal in der Datenbank.
        assertThat(recorder.counts)
                .containsExactly(entry(player, MetricRegistry.MOB_KILLS, "dune-warlord", 1));
    }

    @Test
    @DisplayName("FR-008 - eine Kreatur ohne Spielerbeteiligung zaehlt fuer niemanden")
    void acreatureWithoutAPlayerContributionCountsForNobody() {
        UUID creature = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(creature, kind("rotling", false)))
                .onDeath(
                        new CombatDeathEvent(
                                creature, null, null, DeathCause.ENVIRONMENT, DamageShare.empty(), false));

        assertThat(recorder.counts).isEmpty();
    }

    @Test
    @DisplayName("FR-010 - ein Tod durch eine Kreatur steht unter deren Art")
    void adeathByACreatureStandsUnderItsKind() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        UUID creature = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(creature, kind("dune-warlord", true)), Map.of(character, account))
                .onDeath(
                        new CombatDeathEvent(
                                account, character, creature, DeathCause.COMBAT, DamageShare.empty(), true));

        assertThat(recorder.counts)
                .containsExactly(entry(account, MetricRegistry.DEATHS, "dune-warlord", 1));
    }

    @Test
    @DisplayName("FR-011 - ein Sturz steht unter dem festen Umgebungsschluessel")
    void afallStandsUnderTheFixedEnvironmentKey() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(), Map.of(character, account))
                .onDeath(
                        new CombatDeathEvent(
                                account,
                                character,
                                null,
                                DeathCause.ENVIRONMENT,
                                DamageShare.empty(),
                                true));

        assertThat(recorder.counts)
                .containsExactly(
                        entry(account, MetricRegistry.DEATHS, MetricRegistry.DEATH_ENVIRONMENT, 1));
    }

    @Test
    @DisplayName("FR-011 - die Leere hat ihren eigenen Schluessel")
    void thevoidHasItsOwnKey() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(), Map.of(character, account))
                .onDeath(
                        new CombatDeathEvent(
                                account, character, null, DeathCause.VOID, DamageShare.empty(), true));

        assertThat(recorder.counts)
                .containsExactly(entry(account, MetricRegistry.DEATHS, MetricRegistry.DEATH_VOID, 1));
    }

    @Test
    @DisplayName("FR-011 - ein Tod durch einen anderen Spieler steht unter dem Spielerschluessel")
    void adeathByAnotherPlayerStandsUnderThePlayerKey() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        UUID killer = UUID.randomUUID();
        UUID killerCharacter = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(), Map.of(character, account, killerCharacter, killer))
                .onDeath(
                        new CombatDeathEvent(
                                account,
                                character,
                                killer,
                                DeathCause.COMBAT,
                                new DamageShare(Map.of(killer, 1.0), killer, 100.0),
                                true));

        assertThat(recorder.counts)
                .containsExactly(
                        entry(account, MetricRegistry.DEATHS, MetricRegistry.DEATH_PLAYER, 1));
    }

    @Test
    @DisplayName("FR-007a - die Party in Reichweite bekommt den Kill mit")
    void thepartyInRangeGetsTheKillToo() {
        UUID fighter = UUID.randomUUID();
        UUID healer = UUID.randomUUID();
        UUID creature = UUID.randomUUID();
        Recorder recorder = new Recorder();

        KillStatListener listener =
                new KillStatListener(
                        recorder,
                        kinds(Map.of(creature, kind("rotling", false))),
                        new AccountLookup(id -> Optional.empty()),
                        (victim, contributors) -> Set.of(healer),
                        () -> THRESHOLD);

        listener.onDeath(creatureDeath(creature, Map.of(fighter, 1.0)));

        assertThat(recorder.counts)
                .containsExactlyInAnyOrder(
                        entry(fighter, MetricRegistry.MOB_KILLS, "rotling", 1),
                        entry(healer, MetricRegistry.MOB_KILLS, "rotling", 1));
    }

    // ------------------------------------------------------------------ Gerüst

    private static KillStatListener listener(Recorder recorder, Map<UUID, MobKind> byEntity) {
        return listener(recorder, byEntity, Map.of());
    }

    private static KillStatListener listener(
            Recorder recorder, Map<UUID, MobKind> byEntity, Map<UUID, UUID> accountsByCharacter) {
        return new KillStatListener(
                recorder,
                kinds(byEntity),
                new AccountLookup(id -> Optional.ofNullable(accountsByCharacter.get(id))),
                (victim, contributors) -> Set.of(),
                () -> THRESHOLD);
    }

    private static CombatDeathEvent creatureDeath(UUID creature, Map<UUID, Double> shares) {
        Map<UUID, Double> copy = new LinkedHashMap<>(shares);
        UUID top =
                copy.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
        return new CombatDeathEvent(
                creature, null, top, DeathCause.COMBAT, new DamageShare(copy, top, 100.0), false);
    }

    private static MobKind kind(String key, boolean boss) {
        return new MobKind(
                key, "ZOMBIE", 10, Map.of(), 16.0, MessageKey.of("mob." + key + ".name"), 10, 5, boss);
    }

    private static MobKinds kinds(Map<UUID, MobKind> byEntity) {
        return new MobKinds() {

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
        };
    }

    private static String entry(UUID account, Metric metric, String dimension, long delta) {
        return account + "|" + metric.key() + "." + dimension + "|" + delta;
    }

    /** Nimmt entgegen, was gezählt wurde — mehr braucht dieser Test nicht. */
    private static final class Recorder implements Statistics {

        private final List<String> counts = new ArrayList<>();

        @Override
        public void count(UUID playerId, Metric metric, long delta) {
            counts.add(playerId + "|" + metric.key() + "|" + delta);
        }

        @Override
        public void count(UUID playerId, Metric family, String dimension, long delta) {
            counts.add(playerId + "|" + family.key() + "." + dimension + "|" + delta);
        }

        @Override
        public void reportMax(UUID playerId, Metric metric, long value) {
            counts.add(playerId + "|max:" + metric.key() + "|" + value);
        }
    }
}

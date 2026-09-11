package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
 * FR-011, Edge Case — <b>ein Verursacher ohne Vermerk landet nie unter einer fremden Art.</b>
 *
 * <p>Der gefährliche Fehler an dieser Stelle ist nicht, dass etwas fehlt, sondern dass etwas
 * <em>falsch Zugeordnetes</em> erscheint. Ein Vanilla-Zombie, den B10 nie gesetzt hat, hat keinen
 * Artenschlüssel; wer an dieser Stelle „irgendeine plausible Art" einträgt — die letzte bekannte,
 * die häufigste, die der Zone —, erzeugt eine Zeile, die eine Geschichte erzählt, die nicht
 * stattgefunden hat. Ein Spieler liest dann in seinem Profil, er sei zwölfmal am Dünen-Kriegsherrn
 * gestorben, obwohl er ihm nie begegnet ist.
 *
 * <p>Deshalb gibt es genau drei feste Ersatzschlüssel und keine Heuristik.
 */
class UnresolvableKillerFallsBackTest {

    @Test
    @DisplayName("ein unbekanntes Wesen als Verursacher landet unter Umgebung, nicht unter einer Art")
    void anunknownCreatureFallsBackToEnvironment() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        UUID vanillaZombie = UUID.randomUUID();
        UUID someOtherKind = UUID.randomUUID();
        Recorder recorder = new Recorder();

        // Der Bestand kennt eine Art - aber nicht die des Verursachers. Genau die Lage, in der
        // eine Heuristik "die einzige bekannte" waehlen wuerde.
        listener(recorder, Map.of(someOtherKind, kind("dune-warlord")), Map.of(character, account))
                .onDeath(
                        new CombatDeathEvent(
                                account,
                                character,
                                vanillaZombie,
                                DeathCause.COMBAT,
                                DamageShare.empty(),
                                true));

        assertThat(recorder.counts)
                .containsExactly(
                        account + "|deaths." + MetricRegistry.DEATH_ENVIRONMENT + "|1");
        assertThat(recorder.counts.toString()).doesNotContain("dune-warlord");
    }

    @Test
    @DisplayName("ein Kampftod ohne Verursacher landet unter Umgebung")
    void acombatDeathWithoutAKillerFallsBackToEnvironment() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(), Map.of(character, account))
                .onDeath(
                        new CombatDeathEvent(
                                account, character, null, DeathCause.COMBAT, DamageShare.empty(), true));

        assertThat(recorder.counts)
                .containsExactly(account + "|deaths." + MetricRegistry.DEATH_ENVIRONMENT + "|1");
    }

    @Test
    @DisplayName("eine getoetete Kreatur ohne Art zaehlt gar nicht, statt unter 'unbekannt'")
    void akilledCreatureWithoutAKindCountsNotAtAll() {
        UUID player = UUID.randomUUID();
        UUID vanillaCow = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(), Map.of())
                .onDeath(
                        new CombatDeathEvent(
                                vanillaCow,
                                null,
                                player,
                                DeathCause.COMBAT,
                                new DamageShare(Map.of(player, 1.0), player, 20.0),
                                false));

        // Ein Sammeleintrag "unbekannt" waere eine Zeile in der Kill-Rangliste, die niemand deuten
        // kann - und sie stuende neben echten Arten, als waere sie eine.
        assertThat(recorder.counts).isEmpty();
    }

    @Test
    @DisplayName("die drei festen Schluessel sind genau drei")
    void thethreeFixedKeysAreExactlyThree() {
        assertThat(KillStatListener.fixedDeathKeys())
                .containsExactlyInAnyOrder(
                        MetricRegistry.DEATH_ENVIRONMENT,
                        MetricRegistry.DEATH_VOID,
                        MetricRegistry.DEATH_PLAYER);
    }

    private static KillStatListener listener(
            Recorder recorder, Map<UUID, MobKind> byEntity, Map<UUID, UUID> accountsByCharacter) {
        return new KillStatListener(
                recorder,
                new MobKinds() {

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
                },
                new AccountLookup(id -> Optional.ofNullable(accountsByCharacter.get(id))),
                (victim, contributors) -> Set.of(),
                () -> 0.05);
    }

    private static MobKind kind(String key) {
        return new MobKind(
                key, "ZOMBIE", 10, Map.of(), 16.0, MessageKey.of("mob." + key + ".name"), 10, 5, true);
    }

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

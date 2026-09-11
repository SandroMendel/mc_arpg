package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.StatisticsRepository;

/**
 * FR-005 — <b>ein Spieler mit drei Charakteren hat eine Kill-Zahl.</b>
 *
 * <p>Die Entscheidung ist nicht selbstverständlich, und die Gegenrichtung wäre gut begründbar
 * gewesen: Charaktere haben eigene Level, eigene Ausrüstung, eigene Klassen — warum nicht eigene
 * Statistiken? Weil eine Rangliste den <em>Spieler</em> vergleicht. Zählte sie je Figur, stünde
 * derselbe Mensch dreimal darin, und wer seine Zeit auf drei Figuren verteilt, verlöre gegen
 * einen, der dasselbe auf einer gespielt hat.
 *
 * <p><b>Die Falle daran ist die Übersetzung</b>, nicht die Regel: die Ereignisse, aus denen
 * gezählt wird, kommen charakterbezogen an, und die Kennungen sind beide {@code UUID}. Wer sie
 * vertauscht, bekommt keinen Fehler, sondern drei Zeilen, wo eine stehen sollte — und drei
 * plausible Zahlen, die niemand als falsch erkennt.
 */
class ThreeCharactersOneCountTest {

    @Test
    @DisplayName("FR-005 - drei Charaktere, ein Konto, eine Zeile")
    void threeCharactersOneAccountOneRow() {
        StatisticsFixtures.Identities warrior = StatisticsFixtures.identities();
        UUID account = warrior.holderId();
        UUID mage = StatisticsFixtures.characterId();
        UUID rogue = StatisticsFixtures.characterId();

        Map<UUID, UUID> accountByCharacter = new HashMap<>();
        accountByCharacter.put(warrior.characterId(), account);
        accountByCharacter.put(mage, account);
        accountByCharacter.put(rogue, account);
        AccountLookup accounts =
                new AccountLookup(id -> Optional.ofNullable(accountByCharacter.get(id)));

        Recorder recorder = new Recorder();
        Statistics statistics = new RecordedStatistics(recorder, Logger.getLogger("test"));

        // Jede Figur toetet einmal - so, wie die Zuhoerer es tun wuerden: erst uebersetzen, dann
        // zaehlen.
        for (UUID character : List.of(warrior.characterId(), mage, rogue)) {
            accounts
                    .accountOf(character)
                    .ifPresent(id -> statistics.count(id, MetricRegistry.MOB_KILLS, "rotling", 1));
        }

        assertThat(recorder.written)
                .as("dreimal geschrieben, aber immer auf dasselbe Konto")
                .hasSize(3)
                .allMatch(entry -> entry.playerId().equals(account));
        assertThat(recorder.distinctPlayers()).hasSize(1);
    }

    @Test
    @DisplayName("mit der KONTOkennung gefragt, findet die Naht nichts - und es wird nichts gezaehlt")
    void askedWithTheAccountIdNothingIsFoundAndNothingCounted() {
        StatisticsFixtures.Identities identities = StatisticsFixtures.identities();
        AccountLookup accounts =
                new AccountLookup(
                        id ->
                                id.equals(identities.characterId())
                                        ? Optional.of(identities.holderId())
                                        : Optional.empty());

        Recorder recorder = new Recorder();
        Statistics statistics = new RecordedStatistics(recorder, Logger.getLogger("test"));

        // Der Fehler, der B08 und B11 je einmal erwischt hat, hier als Testfall: die Kontokennung
        // dort hineingegeben, wo die Charakterkennung erwartet wird.
        accounts
                .accountOf(identities.holderId())
                .ifPresent(id -> statistics.count(id, MetricRegistry.MOB_KILLS, "rotling", 1));

        assertThat(recorder.written)
                .as("lieber nichts zaehlen als auf ein erfundenes Konto schreiben")
                .isEmpty();
    }

    /** Nimmt entgegen, was das Repository zu sehen bekäme. */
    private static final class Recorder implements StatisticsRepository {

        private record Written(UUID playerId, String metric, long value) {}

        private final List<Written> written = new ArrayList<>();

        @Override
        public void increment(UUID playerId, String metric, long delta) {
            written.add(new Written(playerId, metric, delta));
        }

        @Override
        public void reportMax(UUID playerId, String metric, long value) {
            written.add(new Written(playerId, metric, value));
        }

        @Override
        public java.util.concurrent.CompletableFuture<Long> sum(
                UUID playerId, String metric, java.time.LocalDate from, java.time.LocalDate to) {
            throw new UnsupportedOperationException("nicht benoetigt");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Long> total(UUID playerId, String metric) {
            throw new UnsupportedOperationException("nicht benoetigt");
        }

        List<UUID> distinctPlayers() {
            return written.stream().map(Written::playerId).distinct().toList();
        }
    }
}

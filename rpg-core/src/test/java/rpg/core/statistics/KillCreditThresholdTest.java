package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamageShare;

/**
 * FR-007, SC-019 — <b>die Schwelle entscheidet, nicht der letzte Treffer.</b>
 *
 * <p>Das ist die Zusage, die diesen Block von einer naiven Kill-Zählung unterscheidet. Wer den
 * letzten Schlag führt, hat nichts Besonderes geleistet — er war nur zuletzt dran. Zählte man ihn,
 * wäre Kill-Stealing eine Strategie und die Rangliste eine Liste der Opportunisten.
 *
 * <p>Umgekehrt darf auch nicht jeder Streiftreffer zählen: sonst misst die Rangliste Anwesenheit.
 * Zwischen beiden liegt die Schwelle, und diese Tests halten fest, wo genau.
 */
class KillCreditThresholdTest {

    private static final double THRESHOLD = 0.05;

    @Test
    @DisplayName("FR-007 - drei Beteiligte, einer unter der Schwelle: zwei Gutschriften")
    void threeContributorsOneBelowTheThreshold() {
        UUID heavy = StatisticsFixtures.holderId();
        UUID medium = StatisticsFixtures.holderId();
        UUID grazing = StatisticsFixtures.holderId();

        DamageShare shares = shares(Map.of(heavy, 0.70, medium, 0.28, grazing, 0.02));

        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of()))
                .containsExactlyInAnyOrder(heavy, medium)
                .doesNotContain(grazing);
    }

    @Test
    @DisplayName("FR-007 - der letzte Treffer entscheidet nichts")
    void thelastHitDecidesNothing() {
        UUID worker = StatisticsFixtures.holderId();
        UUID finisher = StatisticsFixtures.holderId();

        // Der Finisher hat 2 % beigetragen und den toedlichen Schlag gefuehrt. CombatDeathEvent
        // wuesste, wer zuletzt zuschlug - diese Klasse bekommt es absichtlich gar nicht zu sehen.
        DamageShare shares = shares(Map.of(worker, 0.98, finisher, 0.02));

        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of()))
                .containsExactly(worker);
    }

    @Test
    @DisplayName("FR-007 - genau AUF der Schwelle zaehlt als erreicht")
    void exactlyAtTheThresholdCounts() {
        UUID exact = StatisticsFixtures.holderId();

        assertThat(KillCredit.recipients(shares(Map.of(exact, THRESHOLD)), THRESHOLD, List.of()))
                .as("0.05 in der Konfiguration liest jeder als 'ab 5 %', nicht als 'ueber 5 %'")
                .containsExactly(exact);
    }

    @Test
    @DisplayName("knapp unter der Schwelle zaehlt nicht")
    void justBelowTheThresholdDoesNot() {
        UUID almost = StatisticsFixtures.holderId();

        assertThat(KillCredit.recipients(shares(Map.of(almost, 0.0499)), THRESHOLD, List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("FR-008 - ohne Beitrag eines Spielers wird kein Kill gezaehlt")
    void withoutAPlayerContributionNoKillIsCounted() {
        // Eine Kreatur, die in der Lava gestorben ist. DamageShare.empty() ist genau dieser Fall.
        assertThat(KillCredit.recipients(DamageShare.empty(), THRESHOLD, List.of())).isEmpty();
        assertThat(KillCredit.creditedToAnyone(DamageShare.empty(), THRESHOLD, List.of())).isFalse();
    }

    @Test
    @DisplayName("alle ueber der Schwelle bekommen ihn, nicht nur der groesste")
    void everyoneAboveTheThresholdGetsIt() {
        UUID first = StatisticsFixtures.holderId();
        UUID second = StatisticsFixtures.holderId();
        UUID third = StatisticsFixtures.holderId();

        DamageShare shares = shares(Map.of(first, 0.50, second, 0.30, third, 0.20));

        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of())).hasSize(3);
    }

    @Test
    @DisplayName("die Reihenfolge ist stabil - absteigend nach Anteil")
    void theOrderIsStable() {
        UUID big = StatisticsFixtures.holderId();
        UUID middle = StatisticsFixtures.holderId();
        UUID small = StatisticsFixtures.holderId();

        DamageShare shares = shares(Map.of(small, 0.10, big, 0.60, middle, 0.30));

        // DamageShare haelt seine Anteile in einer Map.copyOf; ohne ausdrueckliches Sortieren
        // haette ein fehlgeschlagener Test hier bei jedem Lauf eine andere Reihenfolge gezeigt.
        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of()))
                .containsExactly(big, middle, small);
    }

    private static DamageShare shares(Map<UUID, Double> byAttacker) {
        Map<UUID, Double> copy = new LinkedHashMap<>(byAttacker);
        UUID top =
                copy.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
        return new DamageShare(copy, top, 1000.0);
    }
}

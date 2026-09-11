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
 * FR-007e — <b>für einen Boss gilt keine Sonderregel.</b>
 *
 * <p>Der Reflex wäre eine: „einen Boss erlegt man nicht nebenbei", also eine höhere Schwelle, oder
 * nur der größte Beitragende, oder nur die Party des Erstschlags. Jede dieser Varianten wäre für
 * sich begründbar — und zusammen hätten sie die Bosskill-Rangliste vom Rest der Statistik
 * abgekoppelt, an genau der Stelle, an der die Leute am genauesten hinsehen.
 *
 * <p><b>Ein Boss ist eine Kreatur mit größeren Zahlen.</b> Die Schwelle ist ein <em>Anteil</em>,
 * kein absoluter Schaden; sie skaliert von selbst mit. Wer an einem Boss mit 200.000 Leben 5 %
 * beiträgt, hat 10.000 Schaden ausgeteilt — das ist genau die Arbeit, die „nicht nebenbei"
 * bedeutet, und niemand musste sie extra definieren.
 *
 * <p>Diese Klasse weiß deshalb gar nicht, ob die getötete Kreatur ein Boss war. Sie sieht keinen
 * Artenschlüssel und kein Kennzeichen — die Trennung der beiden Ranglisten passiert später, in der
 * Aggregation (FR-009a).
 */
class BossFollowsTheSameRuleTest {

    private static final double THRESHOLD = 0.05;

    @Test
    @DisplayName("FR-007e - zehn Beteiligte ohne Party, acht ueber der Schwelle: acht Bosskills")
    void tenContributorsEightAboveTheThreshold() {
        Map<UUID, Double> byAttacker = new LinkedHashMap<>();
        List<UUID> above =
                List.of(
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId(),
                        StatisticsFixtures.holderId());
        above.forEach(id -> byAttacker.put(id, 0.115));

        UUID firstGrazer = StatisticsFixtures.holderId();
        UUID secondGrazer = StatisticsFixtures.holderId();
        byAttacker.put(firstGrazer, 0.04);
        byAttacker.put(secondGrazer, 0.04);

        // Ohne Party: ADR-042 im Nachtrag - wer beitraegt, zaehlt, ob er in einer Gruppe steht
        // oder nicht.
        assertThat(KillCredit.recipients(shares(byAttacker), THRESHOLD, List.of()))
                .hasSize(8)
                .containsAll(above)
                .doesNotContain(firstGrazer, secondGrazer);
    }

    @Test
    @DisplayName("die Schwelle skaliert von selbst - sie ist ein Anteil, kein Schadenswert")
    void thethresholdScalesByItself() {
        UUID player = StatisticsFixtures.holderId();

        // Derselbe Anteil an einem Boss und an einem Rotling. Der absolute Schaden dahinter
        // unterscheidet sich um Groessenordnungen; die Regel bleibt dieselbe.
        DamageShare atABoss = new DamageShare(Map.of(player, 0.05), player, 200_000.0);
        DamageShare atACritter = new DamageShare(Map.of(player, 0.05), player, 40.0);

        assertThat(KillCredit.recipients(atABoss, THRESHOLD, List.of())).containsExactly(player);
        assertThat(KillCredit.recipients(atACritter, THRESHOLD, List.of())).containsExactly(player);
    }

    @Test
    @DisplayName("auch am Boss entscheidet nicht der letzte Treffer")
    void evenAtABossTheLastHitDecidesNothing() {
        UUID raid = StatisticsFixtures.holderId();
        UUID finisher = StatisticsFixtures.holderId();

        assertThat(KillCredit.recipients(shares(Map.of(raid, 0.99, finisher, 0.01)), THRESHOLD, List.of()))
                .containsExactly(raid);
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

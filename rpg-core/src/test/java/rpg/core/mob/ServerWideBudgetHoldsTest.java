package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-013a, SC-011: das serverweite Budget haelt auch dann, wenn die Summe der Zonenbudgets
 * darueber liegt.
 *
 * <p>Sechs Zonen zu je 130 sind 780 und bleiben unter einem serverweiten Budget von 800 - heute
 * greift die Grenze nie, und genau deshalb braucht es diesen Test mit Zonen, deren Summe die
 * Grenze wirklich uebersteigt: sonst faellt ein fehlendes serverweites Budget erst auf, wenn
 * jemand eine Zone hochstellt.
 */
class ServerWideBudgetHoldsTest {

    @Test
    @DisplayName("sieben Zonen zu je 200 bei serverweit 800 - die siebte bekommt nichts mehr")
    void theSeventhZoneGetsNothingOnceTheServerWideBudgetIsSpent() {
        Budget budget = new Budget(800, 200, 200, 999);
        List<String> zoneKeys =
                List.of("zone-1", "zone-2", "zone-3", "zone-4", "zone-5", "zone-6", "zone-7");
        Map<String, HordeSpec> hordes = new HashMap<>();
        for (String zoneKey : zoneKeys) {
            hordes.put(
                    zoneKey,
                    new HordeSpec(
                            zoneKey, List.of(new HordeSpec.Entry("area", "kind", 1)), null));
        }
        Map<String, Integer> zoneTotals = new HashMap<>();
        zoneKeys.forEach(key -> zoneTotals.put(key, 0));

        // Rundenweise fuellen, wie ein wiederholter Sweep ueber alle sieben Zonen - bis nichts
        // mehr angenommen wird.
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (String zoneKey : zoneKeys) {
                int serverTotal = totalExcept(zoneTotals, zoneKey);
                Optional<SpawnPlanner.Decision> decision =
                        SpawnPlanner.plan(
                                Optional.of(hordes.get(zoneKey)),
                                budget,
                                serverTotal,
                                zoneTotals.get(zoneKey),
                                10,
                                fixed(0));
                if (decision.isPresent()) {
                    zoneTotals.merge(zoneKey, 1, Integer::sum);
                    progressed = true;
                }
            }
        }

        int grandTotal = zoneTotals.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(grandTotal).as("das serverweite Budget haelt").isEqualTo(800);
        assertThat(zoneTotals.get("zone-7"))
                .as("die siebte Zone bekommt nichts mehr, sobald 800 anderswo stehen")
                .isLessThan(200);
    }

    private static int totalExcept(Map<String, Integer> zoneTotals, String excluded) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : new ArrayList<>(zoneTotals.entrySet())) {
            if (!entry.getKey().equals(excluded)) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}

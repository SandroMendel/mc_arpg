package rpg.core.currency;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import rpg.core.progression.LevelGrowth;
import rpg.core.progression.ProgressionConfig;
import rpg.core.progression.XpCurve;
import rpg.core.stats.Attribute;

/**
 * Die kleinste gueltige {@code ProgressionConfig}, die {@code ShareCalculator} zufriedenstellt.
 *
 * <p>Der Rechner liest daraus nur {@code partyMaxSize}, {@code partyRange} und den Bonus. Alles
 * andere ist Pflichtfeld des Records und hier bewusst belanglos gewaehlt - waeren die Werte
 * aussagekraeftig, laese jemand sie als Balancing-Aussage dieses Blocks.
 */
final class ProgressionConfigStub {

    private ProgressionConfigStub() {}

    static ProgressionConfig forParties() {
        Map<Integer, Long> curve = new LinkedHashMap<>();
        curve.put(2, 100L);
        return new ProgressionConfig(
                XpCurve.of(curve),
                LevelGrowth.of(new double[Attribute.count()]),
                10L,
                Map.of(),
                5,
                50.0d,
                0.10d,
                0.40d,
                Duration.ofSeconds(60),
                Duration.ofMillis(500));
    }
}

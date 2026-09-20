package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T088, FR-038, SC-007: die Messung ohne Volllast, nach dem Vorbild von {@code
 * ZoneLookupBenchmarkTest} aus B09.
 *
 * <p><b>Eine Messung, kein Lasttest</b> (Prinzip VII in der Fassung von ADR-031). Gemessen wird die
 * eigene Rechenarbeit dieses Blocks bei 130 Kreaturen - ein Spawn-Durchlauf (Budget pruefen,
 * Bereich waehlen, Art wuerfeln) und ein Aufraeum-Durchlauf (wer ist ausserhalb der Reichweite
 * jedes Spielers, mit der fertigen Chunk-Menge aus {@link NearbyChunks} und nicht ueber eine
 * Schleife je Spieler). Beides ohne Bukkit, ohne Entitaet, ohne Server. Was nur unter 150 Spielern
 * und 800 Kreaturen sichtbar wird - das Setzen der Entitaet, die Pfadfindung, das Entity-Ticking -
 * gehoert seit ADR-031 zu B15 und wird hier absichtlich NICHT gemessen.
 */
class HordeBudgetBenchmarkTest {

    private static final int CREATURES = 130;
    private static final long BUDGET_NANOS = 1_000_000L; // 1 ms fuer Spawn- und Aufraeum-Durchlauf zusammen
    private static final int WARMUP_ROUNDS = 200;
    private static final int MEASURED_ROUNDS = 50;

    @Test
    @DisplayName(
            "ein Spawn- und ein Aufraeum-Durchlauf bei 130 Kreaturen zusammen unter 1 ms (FR-038, SC-007)")
    void oneSpawnRoundAndOneCleanupRoundOfOneHundredThirtyCreaturesUnderOneMillisecond() {
        Budget budget = new Budget(800, 130, 12, 25);
        HordeSpec horde = hordeFixture();
        HordeRegistry registry = populatedRegistry();
        NearbyChunks nearby = stampedNearby();
        RandomGenerator random = RandomGenerator.getDefault();

        // Warm up: die ersten Durchlaeufe messen den JIT, nicht den Index.
        long sink = 0L;
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            sink += oneRound(budget, horde, registry, nearby, random);
        }

        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            sink += oneRound(budget, horde, registry, nearby, random);
            best = Math.min(best, System.nanoTime() - start);
        }

        assertThat(sink).as("der Compiler darf die Durchlaeufe nicht wegoptimieren").isPositive();
        System.out.printf(
                "[mob] SC-007: Spawn- und Aufraeum-Durchlauf bei %d Kreaturen in %d ns (Budget %d"
                        + " ns, Marge %.1fx)%n",
                CREATURES, best, BUDGET_NANOS, (double) BUDGET_NANOS / best);
        assertThat(best)
                .as(
                        "SC-007: ein Spawn- und ein Aufraeum-Durchlauf bei %d Kreaturen muessen in %d"
                                + " ns passen, aber der schnellste von %d Durchlaeufen brauchte %d ns",
                        CREATURES, BUDGET_NANOS, MEASURED_ROUNDS, best)
                .isLessThan(BUDGET_NANOS);
    }

    /** Budget pruefen, Bereich waehlen, Art wuerfeln, Chunk-Zaehlung nachfuehren - plus Aufraeumen. */
    private static long oneRound(
            Budget budget,
            HordeSpec horde,
            HordeRegistry registry,
            NearbyChunks nearby,
            RandomGenerator random) {
        long touched = 0L;

        int zoneTotal = registry.countIn(horde.zoneKey());
        Optional<SpawnPlanner.Decision> decision =
                SpawnPlanner.plan(Optional.of(horde), budget, 0, zoneTotal, 5, random);
        if (decision.isPresent()) {
            touched++;
        }

        for (HordeRegistry.Entry entry : registry.all()) {
            boolean remove = CleanupRule.shouldRemove(false, entry.chunkKey(), nearby, false);
            if (remove) {
                touched++;
            }
        }
        return touched;
    }

    private static HordeSpec hordeFixture() {
        return new HordeSpec(
                "greenfields",
                List.of(
                        new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 4),
                        new HordeSpec.Entry("greenfields-east", "greenfields.field-shade", 3),
                        new HordeSpec.Entry("greenfields-west", "greenfields.creeping-vine", 3),
                        new HordeSpec.Entry("greenfields-west", "greenfields.bog-slime", 2)),
                null);
    }

    /** 130 Kreaturen, verteilt ueber mehrere Chunks - keine einzelne heisse Tabellenzeile. */
    private static HordeRegistry populatedRegistry() {
        HordeRegistry registry = new HordeRegistry();
        Instant now = Instant.now();
        for (int i = 0; i < CREATURES; i++) {
            long chunkKey = NearbyChunks.pack(i % 20, (i * 3) % 20);
            registry.add(
                    new HordeRegistry.Entry(
                            UUID.randomUUID(),
                            "greenfields.rotling",
                            "greenfields",
                            chunkKey,
                            now,
                            HordeRegistry.Origin.BUDGET));
        }
        return registry;
    }

    /** Ein paar Spieler, gestempelt wie in einem echten Durchlauf - nicht alle Chunks sind nah. */
    private static NearbyChunks stampedNearby() {
        NearbyChunks nearby = new NearbyChunks();
        for (int p = 0; p < 5; p++) {
            nearby.stampAround(p * 5 * 16, p * 3 * 16, 96.0);
        }
        return nearby;
    }
}

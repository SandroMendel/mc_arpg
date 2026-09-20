package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T078 — Admin-Spawns teilen den Bestand, aber nicht das reguläre Budget. */
class AdminSpawnDoesNotConsumeBudgetTest {

    private static final Instant WHEN = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    @DisplayName("ein Admin-Spawn bleibt im Chunk, verbraucht aber kein Horde-Budget")
    void adminSpawnDoesNotConsumeBudget() {
        HordeRegistry registry = new HordeRegistry();
        long chunk = NearbyChunks.pack(2, 3);

        UUID budgetMob = UUID.randomUUID();
        UUID adminMob = UUID.randomUUID();
        registry.add(
                new HordeRegistry.Entry(
                        budgetMob,
                        "greenfields.rotling",
                        "greenfields",
                        chunk,
                        WHEN,
                        HordeRegistry.Origin.BUDGET));

        registry.add(
                new HordeRegistry.Entry(
                        adminMob,
                        "greenfields.rotling",
                        "greenfields",
                        chunk,
                        WHEN,
                        HordeRegistry.Origin.ADMIN));

        assertThat(registry.total()).as("server-wide budget").isEqualTo(1);
        assertThat(registry.countIn("greenfields")).as("zone budget").isEqualTo(1);
        assertThat(registry.countAdmin()).isEqualTo(1);
        assertThat(registry.countInChunk(chunk)).as("cleanup sees both entries").isEqualTo(2);

        registry.remove(adminMob);

        assertThat(registry.total()).isEqualTo(1);
        assertThat(registry.countIn("greenfields")).isEqualTo(1);
        assertThat(registry.countAdmin()).isZero();
        assertThat(registry.countInChunk(chunk)).isEqualTo(1);
    }
}

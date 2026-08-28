package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-033: ein Boss zaehlt im Budget seiner Zone mit - er bekommt keinen eigenen, zusaetzlichen
 * Platz. {@link HordeRegistry} und {@link Budget} kennen keinen Unterschied zwischen einer
 * gewoehnlichen Kreatur und einem Boss; der Bestand zaehlt beide gleich.
 */
class BossCountsAgainstTheBudgetTest {

    @Test
    @DisplayName("ein gesetzter Boss belegt einen Platz - eine volle Zone nimmt danach nichts mehr an")
    void aPlacedBossOccupiesASlotAFullZoneAcceptsNothingMoreAfterwards() {
        Budget budget = new Budget(10, 1, 1, 5);
        HordeRegistry registry = new HordeRegistry();

        registry.add(
                new HordeRegistry.Entry(
                        UUID.randomUUID(),
                        "greenfields.warden-of-the-field",
                        "greenfields",
                        NearbyChunks.pack(0, 0),
                        Instant.now()));

        assertThat(registry.countIn("greenfields")).as("der Boss zaehlt wie jede Kreatur").isEqualTo(1);
        assertThat(registry.total()).isEqualTo(1);
        assertThat(
                        budget.allows(
                                registry.total(),
                                registry.countIn("greenfields"),
                                registry.countInChunk(NearbyChunks.pack(0, 0)),
                                1))
                .as("die Zone ist mit dem Boss allein schon voll - kein eigener Platz fuer ihn")
                .isFalse();
    }

    @Test
    @DisplayName("mit Platz im Budget bleibt neben dem Boss noch Raum fuer gewoehnliche Kreaturen")
    void withRoomInTheBudgetOrdinaryCreaturesStillFitNextToTheBoss() {
        Budget budget = new Budget(10, 5, 5, 5);
        HordeRegistry registry = new HordeRegistry();

        registry.add(
                new HordeRegistry.Entry(
                        UUID.randomUUID(),
                        "greenfields.warden-of-the-field",
                        "greenfields",
                        NearbyChunks.pack(0, 0),
                        Instant.now()));

        assertThat(
                        budget.allows(
                                registry.total(),
                                registry.countIn("greenfields"),
                                registry.countInChunk(NearbyChunks.pack(1, 0)),
                                1))
                .as("ein Platz ist belegt, aber vier bleiben")
                .isTrue();
    }
}

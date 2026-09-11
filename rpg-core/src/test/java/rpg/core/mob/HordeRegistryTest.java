package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Bestand und seine drei Zählungen.
 *
 * <p>Sie werden mitgeführt statt bei Bedarf gezählt, weil sie im Spawn-Pfad stehen — eine Schleife
 * über den Bestand wäre dort genau das, was Prinzip II verbietet. Und weil sie mitgeführt werden,
 * müssen sie nach jeder Folge von Eintragen und Austragen stimmen; eine Zählung, die auseinanderläuft,
 * fällt erst auf, wenn das Budget nicht mehr hält.
 */
class HordeRegistryTest {

    private static final Instant WHEN = Instant.parse("2026-08-24T20:00:00Z");

    @Test
    @DisplayName("die drei Zaehlungen stimmen nach Eintragen und Austragen")
    void theThreeCountsHoldThroughAddAndRemove() {
        HordeRegistry registry = new HordeRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long chunk = NearbyChunks.pack(10, 10);

        registry.add(entry(first, "greenfields.rotling", "greenfields", chunk));
        registry.add(entry(second, "greenfields.rotling", "greenfields", chunk));

        assertThat(registry.total()).isEqualTo(2);
        assertThat(registry.countIn("greenfields")).isEqualTo(2);
        assertThat(registry.countInChunk(chunk)).isEqualTo(2);

        registry.remove(first);

        assertThat(registry.total()).isEqualTo(1);
        assertThat(registry.countIn("greenfields")).isEqualTo(1);
        assertThat(registry.countInChunk(chunk)).isEqualTo(1);

        registry.remove(second);

        assertThat(registry.total()).isZero();
        assertThat(registry.countIn("greenfields")).isZero();
        assertThat(registry.countInChunk(chunk)).isZero();
        assertThat(registry.occupiedChunks()).isZero();
    }

    @Test
    @DisplayName("eine Kreatur bleibt der URSPRUNGSzone zugerechnet, auch wenn sie hinauslaeuft")
    void aCreatureStaysWithTheZoneItCameFrom() {
        // FR-017. Ohne das waere das Budget zu umgehen, indem man Kreaturen ueber die Grenze
        // schiebt - und ein Budget, das man umgehen kann, ist keines. Der Eintrag traegt die Zone
        // von seiner Entstehung; er wird nie umgeschrieben.
        HordeRegistry registry = new HordeRegistry();
        UUID mob = UUID.randomUUID();
        registry.add(entry(mob, "greenfields.rotling", "greenfields", NearbyChunks.pack(0, 0)));

        assertThat(registry.find(mob).zoneKey()).isEqualTo("greenfields");
        assertThat(registry.countIn("dustlands"))
                .as("die Nachbarzone zaehlt sie nicht mit, egal wo sie steht")
                .isZero();
    }

    @Test
    @DisplayName("eine unbekannte Entitaet auszutragen ist kein Fehler")
    void removingAnUnknownEntityIsHarmless() {
        // Eine Kreatur kann auf einem Weg verschwinden, den dieser Block nicht ausgeloest hat: ein
        // Betreiber, eine entladene Welt, ein anderes Plugin. Der Bestand hoert dem
        // Entfernungs-Ereignis zu, damit er ehrlich bleibt - und darf dabei nicht werfen.
        HordeRegistry registry = new HordeRegistry();

        assertThat(registry.remove(UUID.randomUUID())).isNull();
        assertThat(registry.total()).isZero();
    }

    @Test
    @DisplayName("die Reihenfolge ist die des Setzens - die aelteste zuerst, ohne Sortierung")
    void theOrderIsTheOrderOfPlacement() {
        HordeRegistry registry = new HordeRegistry();
        UUID oldest = UUID.randomUUID();
        registry.add(entry(oldest, "a", "greenfields", NearbyChunks.pack(0, 0)));
        registry.add(entry(UUID.randomUUID(), "b", "greenfields", NearbyChunks.pack(1, 0)));

        assertThat(registry.all().iterator().next().entityId()).isEqualTo(oldest);
    }

    @Test
    @DisplayName("zwei Zonen zaehlen getrennt, der Server zaehlt zusammen")
    void zonesCountSeparatelyAndTheServerCountsTogether() {
        HordeRegistry registry = new HordeRegistry();
        registry.add(entry(UUID.randomUUID(), "a", "greenfields", NearbyChunks.pack(0, 0)));
        registry.add(entry(UUID.randomUUID(), "b", "dustlands", NearbyChunks.pack(100, 0)));

        assertThat(registry.countIn("greenfields")).isEqualTo(1);
        assertThat(registry.countIn("dustlands")).isEqualTo(1);
        assertThat(registry.total()).as("gegen das serverweite Budget").isEqualTo(2);
    }

    @Test
    @DisplayName("Leeren nimmt alles mit, auch die Chunk-Zaehlung")
    void clearingTakesTheChunkCountWithIt() {
        HordeRegistry registry = new HordeRegistry();
        registry.add(entry(UUID.randomUUID(), "a", "greenfields", NearbyChunks.pack(5, 5)));

        registry.clear();

        assertThat(registry.total()).isZero();
        assertThat(registry.countIn("greenfields")).isZero();
        assertThat(registry.countInChunk(NearbyChunks.pack(5, 5)))
                .as("sonst blockierte ein Geisterchunk beim naechsten Mal das Budget")
                .isZero();
    }

    private static HordeRegistry.Entry entry(UUID id, String kind, String zone, long chunk) {
        return new HordeRegistry.Entry(id, kind, zone, chunk, WHEN, HordeRegistry.Origin.BUDGET);
    }
}

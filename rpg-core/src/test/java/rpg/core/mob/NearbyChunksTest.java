package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der räumliche Index, den FR-018 verlangt.
 *
 * <p>Die Frage beim Aufräumen ist: <em>steht ein Spieler in Reichweite dieser Kreatur?</em>
 * Naheliegend wäre, sie je Kreatur über alle Spieler zu stellen — bei 130 Kreaturen und 200
 * Spielern 26.000 Abstandsrechnungen je Durchlauf, und genau die lineare Iteration über alle
 * Kandidaten, die Prinzip II verbietet. Diese Klasse dreht sie um: die wenigen stempeln, die vielen
 * schlagen nach.
 */
class NearbyChunksTest {

    @Test
    @DisplayName("der eigene Chunk und die Nachbarn liegen drin, der ferne nicht")
    void ownChunkAndNeighboursAreIn() {
        NearbyChunks near = new NearbyChunks();
        near.stampAround(0, 0, 96.0);

        assertThat(near.contains(NearbyChunks.pack(0, 0))).isTrue();
        assertThat(near.contains(NearbyChunks.pack(6, 6)))
                .as("96 Bloecke sind 6 Chunks")
                .isTrue();
        assertThat(near.contains(NearbyChunks.pack(7, 0)))
                .as("und der siebte nicht mehr")
                .isFalse();
    }

    @Test
    @DisplayName("zwei nah beieinanderstehende Spieler stempeln denselben Chunk nur einmal")
    void twoNearbyPlayersStampTheSameChunkOnlyOnce() {
        // Der Grund, warum ein Pulk Spieler nicht so viel kostet wie seine Zahl: die Menge ist eine
        // Menge. Ohne das waere ein Treffpunkt der teuerste Ort auf dem Server.
        NearbyChunks one = new NearbyChunks();
        one.stampAround(0, 0, 32.0);
        int alone = one.size();

        NearbyChunks two = new NearbyChunks();
        two.stampAround(0, 0, 32.0);
        two.stampAround(4, 4, 32.0);

        assertThat(two.size())
                .as("derselbe Chunk, also kein einziger Eintrag mehr")
                .isEqualTo(alone);
    }

    @Test
    @DisplayName("zwei weit auseinanderstehende Spieler stempeln getrennte Bereiche")
    void twoDistantPlayersStampSeparateAreas() {
        NearbyChunks near = new NearbyChunks();
        near.stampAround(0, 0, 32.0);
        near.stampAround(5000, 5000, 32.0);

        assertThat(near.contains(NearbyChunks.packBlock(0, 0))).isTrue();
        assertThat(near.contains(NearbyChunks.packBlock(5000, 5000))).isTrue();
        assertThat(near.contains(NearbyChunks.packBlock(2500, 2500))).isFalse();
    }

    @Test
    @DisplayName("Leeren macht den Puffer wirklich leer - er wird zwischen Durchlaeufen wiederverwendet")
    void clearingReallyEmptiesTheBuffer() {
        // Eine neue Menge je Durchlauf waere eine Zuweisung im Spawn-Pfad. Der Puffer bleibt also,
        // und dann muss das Leeren stimmen - sonst stuende ein Spieler von vor zehn Minuten noch
        // drin und haelt eine Horde am Leben, die niemand mehr sieht.
        NearbyChunks near = new NearbyChunks();
        near.stampAround(0, 0, 96.0);
        assertThat(near.size()).isPositive();

        near.clear();

        assertThat(near.size()).isZero();
        assertThat(near.contains(NearbyChunks.pack(0, 0))).isFalse();
    }

    @Test
    @DisplayName("negative Koordinaten funktionieren - die halbe Welt liegt dort")
    void negativeCoordinatesWork() {
        NearbyChunks near = new NearbyChunks();
        near.stampAround(-300, -80, 32.0);

        assertThat(near.contains(NearbyChunks.packBlock(-300, -80))).isTrue();
        assertThat(near.contains(NearbyChunks.packBlock(300, 80))).isFalse();
    }

    @Test
    @DisplayName("der Puffer waechst, wenn viele Spieler stempeln")
    void theBufferGrowsWithManyPlayers() {
        NearbyChunks near = new NearbyChunks();
        for (int i = 0; i < 200; i++) {
            near.stampAround(i * 1000, 0, 96.0);
        }
        assertThat(near.size()).isGreaterThan(1000);
        assertThat(near.contains(NearbyChunks.packBlock(199_000, 0))).isTrue();
    }
}

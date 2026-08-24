package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Chunk-Zaehlung - und die eine Zusage, die sie von B09s Tabelle unterscheidet: sie schrumpft.
 *
 * <p>B09s {@code ChunkTable} wird beim Laden gebaut und danach nur gelesen; sie waechst nie und
 * schrumpft nie. Diese hier aendert sich bei jedem Setzen und bei jedem Tod, und ihre Groesse muss
 * mit dem Bestand fallen - sonst waere sie eine Struktur, die ueber die Serverlaufzeit nur zunimmt,
 * und genau das sollte das Chunk-Budget nicht kosten.
 */
class ChunkCountTest {

    @Test
    @DisplayName("hoch, runter, und der Eintrag verschwindet bei null")
    void upDownAndGoneAtZero() {
        ChunkCount count = new ChunkCount();
        long chunk = NearbyChunks.pack(12, -7);

        count.increment(chunk);
        count.increment(chunk);
        assertThat(count.get(chunk)).isEqualTo(2);
        assertThat(count.occupiedChunks()).isEqualTo(1);

        count.decrement(chunk);
        assertThat(count.get(chunk)).isEqualTo(1);
        assertThat(count.occupiedChunks()).as("noch belegt").isEqualTo(1);

        count.decrement(chunk);
        assertThat(count.get(chunk)).isZero();
        assertThat(count.occupiedChunks())
                .as("die Struktur schrumpft wirklich und waechst nicht monoton")
                .isZero();
    }

    @Test
    @DisplayName("ein Chunk, in dem nichts steht, antwortet mit null statt mit einem Fehler")
    void anEmptyChunkAnswersZero() {
        assertThat(new ChunkCount().get(NearbyChunks.pack(1, 1))).isZero();
    }

    @Test
    @DisplayName("Austragen aus der Mitte einer Sondierkette laesst nichts dahinter verschwinden")
    void removingFromTheMiddleOfAProbeChainKeepsTheRestFindable() {
        // Der Grund, warum decrement die Kette hinter der Luecke neu einhaengt: ohne das waeren
        // Eintraege dahinter nicht mehr auffindbar, und das Chunk-Budget zaehlte sie nicht mehr mit.
        ChunkCount count = new ChunkCount(16);
        for (int i = 0; i < 40; i++) {
            count.increment(NearbyChunks.pack(i, 0));
        }
        for (int i = 0; i < 40; i += 2) {
            count.decrement(NearbyChunks.pack(i, 0));
        }
        for (int i = 1; i < 40; i += 2) {
            assertThat(count.get(NearbyChunks.pack(i, 0)))
                    .as("Chunk " + i + " muss noch auffindbar sein")
                    .isEqualTo(1);
        }
        assertThat(count.occupiedChunks()).isEqualTo(20);
    }

    @Test
    @DisplayName("die Tabelle waechst, wenn mehr Chunks belegt sind als vorgesehen")
    void theTableGrowsWhenMoreChunksAreOccupiedThanExpected() {
        // Anders als ChunkTable, die bei einer zu kleinen Schaetzung wirft: dort ist die Groesse
        // beim Laden bekannt, hier nicht. Eine Horde darf sich nicht daran stossen, dass jemand
        // die Anfangsgroesse zu knapp gewaehlt hat.
        ChunkCount count = new ChunkCount(16);
        for (int i = 0; i < 500; i++) {
            count.increment(NearbyChunks.pack(i, i));
        }
        assertThat(count.occupiedChunks()).isEqualTo(500);
        assertThat(count.get(NearbyChunks.pack(499, 499))).isEqualTo(1);
    }

    @Test
    @DisplayName("Leeren setzt alles zurueck - beim Herunterfahren")
    void clearingResetsEverything() {
        ChunkCount count = new ChunkCount();
        count.increment(NearbyChunks.pack(3, 4));
        count.clear();

        assertThat(count.occupiedChunks()).isZero();
        assertThat(count.get(NearbyChunks.pack(3, 4))).isZero();
    }
}

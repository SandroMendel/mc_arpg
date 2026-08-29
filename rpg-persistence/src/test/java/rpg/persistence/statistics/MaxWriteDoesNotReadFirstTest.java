package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * ADR-040 — der zweite Schreibweg schreibt, ohne vorher zu lesen.
 *
 * <p>Gegen eine echte PostgreSQL, nicht gegen eine Attrappe: Prinzip VII ist da ausdrücklich, und
 * hier besonders zu Recht. Eine Attrappe würde jedem {@code GREATEST} zustimmen, auch einem, das
 * gar nicht vergleicht — und der Fehler wäre eine Zahl, die zu klein ist und trotzdem plausibel
 * aussieht.
 *
 * <p><b>Warum „ohne zu lesen" mehr ist als eine Sparmaßnahme.</b> Läse der Schreibweg erst den
 * gespeicherten Wert und entschiede dann selbst, kostete das eine Abfrage je Ereignis auf dem
 * heißen Pfad (FR-002) — und zwischen dem Lesen und dem Schreiben könnte ein anderer Thread einen
 * höheren Wert ablegen, den das Schreiben danach still wieder nach unten korrigiert. Die
 * Entscheidung gehört in dieselbe Anweisung wie das Schreiben.
 */
class MaxWriteDoesNotReadFirstTest {

    private static final String METRIC = "damage_max";

    private PersistenceHarness harness;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("ein niedrigerer Wert ueberschreibt einen hoeheren NICHT")
    void alowerValueDoesNotOverwriteAHigherOne() throws Exception {
        harness.statistics.reportMax(playerId, METRIC, 1249);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        harness.statistics.reportMax(playerId, METRIC, 800);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, METRIC).get())
                .as("der groesste Treffer bleibt stehen, auch nach einem kleineren")
                .isEqualTo(1249L);
    }

    @Test
    @DisplayName("ein hoeherer Wert setzt sich durch")
    void ahigherValueWins() throws Exception {
        harness.statistics.reportMax(playerId, METRIC, 800);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        harness.statistics.reportMax(playerId, METRIC, 1249);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(harness.statistics.total(playerId, METRIC).get()).isEqualTo(1249L);
    }

    @Test
    @DisplayName("zwischen zwei Fluesse gemeldete Werte werden im Speicher schon maximiert")
    void valuesBetweenTwoFlushesAreMaximisedInMemory() throws Exception {
        harness.statistics.reportMax(playerId, METRIC, 300);
        harness.statistics.reportMax(playerId, METRIC, 1249);
        harness.statistics.reportMax(playerId, METRIC, 700);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // Waeren die drei aufsummiert worden - der naheliegende Fehler, wenn man die Map fuer
        // Summen mitbenutzt -, stuende hier 2249.
        assertThat(harness.statistics.total(playerId, METRIC).get()).isEqualTo(1249L);
    }

    @Test
    @DisplayName("das Melden selbst loest keinen Datenbankzugriff aus")
    void reportingItselfTouchesNoDatabase() {
        int asyncBefore = harness.scheduler.asyncRuns();

        for (int i = 0; i < 1_000; i++) {
            harness.statistics.reportMax(playerId, METRIC, i);
        }

        // Am Scheduler gemessen, nicht an der Uhr: "kein Datenbankzugriff je Spielereignis"
        // heisst, dass gar nichts eingeplant wird - nicht, dass es schnell ist.
        assertThat(harness.scheduler.asyncRuns()).isEqualTo(asyncBefore);
    }

    @Test
    @DisplayName("im ganzen Schreibpfad steht kein SELECT vor dem Schreiben")
    void noSelectPrecedesTheWriteInTheWholePath() throws IOException {
        // Die Verhaltenstests oben koennen zeigen, dass das Ergebnis stimmt; sie koennen nicht
        // zeigen, WIE es zustande kam. Ein Lesen mit anschliessendem Vergleich in Java saehe in
        // jedem einzelnen von ihnen gleich aus - und waere unter Last falsch.
        Path source =
                repositoryRoot()
                        .resolve(
                                "rpg-persistence/src/main/java/rpg/persistence/jdbc/"
                                        + "JdbcStatisticsRepository.java");
        String code =
                Files.readString(source)
                        .replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("(?m)//.*$", "");

        String writeBody = bodyOf(code, "public List<DirtyMark> write(");

        assertThat(writeBody.toUpperCase(Locale.ROOT))
                .as("der Schreibpfad fragt die gespeicherten Werte nicht ab")
                .doesNotContain("SELECT")
                .doesNotContain("EXECUTEQUERY");
    }

    /**
     * Der Rumpf genau einer Methode, über Klammerzählung abgegrenzt.
     *
     * <p>Der naheliegende Weg - vom Methodenkopf bis ans Dateiende lesen - hat diesen Test beim
     * ersten Lauf fälschlich rot gemacht: dahinter steht {@code queryAsync}, und das ist der
     * <em>Lese</em>pfad, der selbstverständlich ein {@code executeQuery} enthält. Eine
     * Quelltextprüfung, die zu viel liest, meldet Verstöße, die keine sind - und der übliche
     * nächste Schritt ist, ihr eine Ausnahme beizubringen, bis sie gar nichts mehr prüft.
     */
    private static String bodyOf(String code, String signature) {
        int start = code.indexOf(signature);
        assertThat(start).as("die Methode '" + signature + "' wurde gefunden").isGreaterThan(0);

        int open = code.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char character = code.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(open, i + 1);
                }
            }
        }
        throw new IllegalStateException("Methodenrumpf nicht geschlossen: " + signature);
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-core"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("Wurzelverzeichnis nicht gefunden");
        }
        return here;
    }
}

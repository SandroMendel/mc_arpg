package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-019, ADR-041 — <b>Level, XP und Coins werden gelesen, nicht gespiegelt.</b>
 *
 * <p>Sie in die Tagestabelle zu schreiben wäre naheliegend und falsch. Naheliegend, weil dann alles
 * an einem Ort läge und jede Rangliste dieselbe Abfrage benutzen könnte. Falsch, weil ein Zustand
 * keine Tagesform hat: die Summe von sieben Tagen Level 12 ist nicht Level 84, und ein Mittelwert
 * wäre genauso bedeutungslos. Was dabei entstünde, wäre eine zweite Wahrheit über eine Zahl, die
 * B06 und B08b bereits führen — und sie würde ab dem ersten Ausfall des Schreibwegs von der ersten
 * abweichen.
 *
 * <p><b>Warum die Zusage einen Riegel braucht.</b> Ohne ihn steht sie in einem {@code package-info}
 * und in einem ADR. Beides liest niemand, während er einen Zuhörer schreibt, dem gerade auffällt,
 * dass er das Level ja schon zur Hand hat.
 *
 * <p>Der Riegel ist zweiteilig: die Metrikart weist die Zustandswerte an beiden Schreibwegen ab
 * (Verhalten), und keine Aufrufstelle des Blocks reicht sie überhaupt erst hinein (Quelltext).
 */
class StateValuesAreNotMirroredTest {

    private static final Pattern WRITE_CALL =
            Pattern.compile("\\b(count|reportMax|increment)\\s*\\(([^;]{0,200})");

    private static final List<String> STATE_CONSTANTS = List.of("LEVEL", "XP", "COINS");

    @Test
    @DisplayName("FR-019 - kein Zustandswert passt auf einen Schreibweg der Tagestabelle")
    void noStateValueFitsAWritePath() {
        List<Metric> states =
                MetricRegistry.all().values().stream()
                        .filter(metric -> metric.kind() == MetricKind.STATE)
                        .toList();

        assertThat(states)
                .as("ohne Zustandsmetriken prueft dieser Test nichts")
                .isNotEmpty()
                .allSatisfy(
                        state -> {
                            assertThat(rejectionOf(state, MetricKind.SUM))
                                    .as("%s darf nicht summierbar sein", state.key())
                                    .isInstanceOf(IllegalArgumentException.class);
                            assertThat(rejectionOf(state, MetricKind.MAX))
                                    .as("%s darf nicht maximierbar sein", state.key())
                                    .isInstanceOf(IllegalArgumentException.class);
                        });
    }

    @Test
    @DisplayName("FR-019 - keine Aufrufstelle reicht Level, XP oder Coins in einen Schreibweg")
    void noCallSitePassesAStateMetricIntoAWrite() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : SourceGuard.statisticsSources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            Matcher matcher = WRITE_CALL.matcher(code);
            while (matcher.find()) {
                String arguments = matcher.group(2);
                for (String state : STATE_CONSTANTS) {
                    if (arguments.contains("MetricRegistry." + state)) {
                        violations.add(
                                source.getFileName()
                                        + ": "
                                        + matcher.group(1)
                                        + "(... MetricRegistry."
                                        + state
                                        + " ...)");
                    }
                }
            }
        }

        assertThat(violations)
                .as("Zustandswerte gehoeren gelesen, nicht geschrieben (ADR-041)")
                .isEmpty();
    }

    private static Throwable rejectionOf(Metric metric, MetricKind kind) {
        try {
            metric.requireKind(kind);
            return null;
        } catch (RuntimeException thrown) {
            return thrown;
        }
    }
}

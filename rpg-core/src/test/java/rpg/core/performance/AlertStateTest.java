package rpg.core.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AlertStateTest {

    private static final long BUDGET = 5_000_000L;
    private static final AlertPolicy POLICY =
            new AlertPolicy(0.90d, Duration.ofSeconds(60));

    @Test
    void warnsAtNinetyPercentAndBecomesCriticalAfterContinuousBreach() {
        AlertEvaluator evaluator = new AlertEvaluator();
        AlertEvaluator.Evaluation warning =
                evaluator.evaluate(AlertEvaluator.State.initial(), 4_500_000L, BUDGET, 0L, POLICY);

        assertThat(warning.state().state()).isEqualTo(AlertState.WARNING);
        assertThat(warning.transition()).isPresent();

        AlertEvaluator.Evaluation critical =
                evaluator.evaluate(
                        warning.state(),
                        5_100_000L,
                        BUDGET,
                        Duration.ofSeconds(60).toNanos(),
                        POLICY);

        assertThat(critical.state().state()).isEqualTo(AlertState.CRITICAL);
        assertThat(critical.transition()).hasValueSatisfying(t -> assertThat(t.state()).isEqualTo(AlertState.CRITICAL));
    }

    @Test
    void deduplicatesAnOngoingCriticalEpisodeAndReportsRecoveryOnce() {
        AlertEvaluator evaluator = new AlertEvaluator();
        AlertEvaluator.Evaluation first =
                evaluator.evaluate(AlertEvaluator.State.initial(), 5_100_000L, BUDGET, 0L, POLICY);
        AlertEvaluator.Evaluation critical =
                evaluator.evaluate(first.state(), 5_100_000L, BUDGET, Duration.ofSeconds(60).toNanos(), POLICY);
        AlertEvaluator.Evaluation stillCritical =
                evaluator.evaluate(
                        critical.state(),
                        5_100_000L,
                        BUDGET,
                        Duration.ofSeconds(61).toNanos(),
                        POLICY);
        AlertEvaluator.Evaluation recovered =
                evaluator.evaluate(stillCritical.state(), 1_000_000L, BUDGET, Duration.ofSeconds(62).toNanos(), POLICY);
        AlertEvaluator.Evaluation normal =
                evaluator.evaluate(recovered.state(), 1_000_000L, BUDGET, Duration.ofSeconds(63).toNanos(), POLICY);

        assertThat(stillCritical.transition()).isEmpty();
        assertThat(recovered.transition()).hasValueSatisfying(t -> assertThat(t.state()).isEqualTo(AlertState.RECOVERED));
        assertThat(normal.state().state()).isEqualTo(AlertState.NORMAL);
        assertThat(normal.transition()).isEmpty();
    }
}

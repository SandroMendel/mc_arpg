package rpg.core.performance;

import java.util.Objects;
import java.util.Optional;

/** Pure state transition logic for warning, critical and recovery episodes. */
public final class AlertEvaluator {

    private static final long NONE = Long.MIN_VALUE;

    public Evaluation evaluate(
            State previous,
            long observedNanos,
            long budgetNanos,
            long nowNanos,
            AlertPolicy policy) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(policy, "policy");
        if (budgetNanos <= 0) {
            throw new IllegalArgumentException("budgetNanos must be positive");
        }
        if (observedNanos < 0) {
            throw new IllegalArgumentException("observedNanos must not be negative");
        }

        boolean warning = observedNanos >= Math.ceil(budgetNanos * policy.warningRatio());
        boolean overBudget = observedNanos > budgetNanos;
        long violationSince = warning ? previous.violationSinceNanos() : NONE;
        if (warning && violationSince == NONE) {
            violationSince = nowNanos;
        }

        if (!warning) {
            if (previous.state() == AlertState.WARNING || previous.state() == AlertState.CRITICAL) {
                State recovered = new State(AlertState.RECOVERED, NONE, previous.episode());
                return new Evaluation(
                        recovered,
                        Optional.of(
                                new AlertTransition(
                                        AlertState.RECOVERED,
                                        nowNanos,
                                        previous.episode(),
                                        observedNanos,
                                        budgetNanos,
                                        previous.violationSinceNanos())));
            }
            if (previous.state() == AlertState.RECOVERED) {
                return new Evaluation(new State(AlertState.NORMAL, NONE, previous.episode()), Optional.empty());
            }
            return new Evaluation(new State(AlertState.NORMAL, NONE, previous.episode()), Optional.empty());
        }

        if (overBudget
                && nowNanos - violationSince >= policy.criticalAfter().toNanos()
                && previous.state() != AlertState.CRITICAL) {
                State critical = new State(AlertState.CRITICAL, violationSince, previous.episode());
                return new Evaluation(
                        critical,
                        Optional.of(
                                new AlertTransition(
                                        AlertState.CRITICAL,
                                        nowNanos,
                                        previous.episode(),
                                        observedNanos,
                                        budgetNanos,
                                        violationSince)));
        }

        if (previous.state() == AlertState.NORMAL || previous.state() == AlertState.RECOVERED) {
            long episode = previous.episode() + 1;
            State warningState = new State(AlertState.WARNING, violationSince, episode);
            return new Evaluation(
                        warningState,
                        Optional.of(
                                new AlertTransition(
                                        AlertState.WARNING,
                                        nowNanos,
                                        episode,
                                        observedNanos,
                                        budgetNanos,
                                        violationSince)));
        }

        State unchanged = new State(previous.state(), violationSince, previous.episode());
        return new Evaluation(unchanged, Optional.empty());
    }

    public record State(AlertState state, long violationSinceNanos, long episode) {

        public State {
            Objects.requireNonNull(state, "state");
            if (episode < 0) {
                throw new IllegalArgumentException("episode must not be negative");
            }
        }

        public static State initial() {
            return new State(AlertState.NORMAL, NONE, 0L);
        }
    }

    public record Evaluation(State state, Optional<AlertTransition> transition) {

        public Evaluation {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(transition, "transition");
        }
    }
}

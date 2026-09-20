package rpg.core.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Keeps one deduplicated alert episode per registered subsystem across report cycles. */
public final class PerformanceAlertTracker {

    private final AlertEvaluator evaluator;
    private final AlertPolicy policy;
    private final Map<SubsystemId, AlertEvaluator.State> states = new LinkedHashMap<>();

    public PerformanceAlertTracker(AlertPolicy policy) {
        this(new AlertEvaluator(), policy);
    }

    public PerformanceAlertTracker(AlertEvaluator evaluator, AlertPolicy policy) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Evaluates the latest immutable snapshot without emitting repeated unchanged transitions. */
    public synchronized Result evaluate(PerformanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<SubsystemId, PerformanceSnapshot.SubsystemSnapshot> ordered =
                new TreeMap<>(Comparator.comparing(SubsystemId::value));
        ordered.putAll(snapshot.subsystems());

        Map<SubsystemId, AlertState> alertStates = new LinkedHashMap<>();
        List<AlertTransition> transitions = new ArrayList<>();
        Set<SubsystemId> missing = new java.util.LinkedHashSet<>();
        Map<SubsystemId, Long> violationSince = new LinkedHashMap<>();
        for (Map.Entry<SubsystemId, PerformanceSnapshot.SubsystemSnapshot> entry : ordered.entrySet()) {
            SubsystemId id = entry.getKey();
            PerformanceSnapshot.SubsystemSnapshot subsystem = entry.getValue();
            AlertEvaluator.State previous =
                    states.getOrDefault(id, AlertEvaluator.State.initial());
            if (subsystem.window().sampleCount() == 0) {
                states.putIfAbsent(id, previous);
                alertStates.put(id, previous.state());
                missing.add(id);
                if (previous.violationSinceNanos() != Long.MIN_VALUE) {
                    violationSince.put(id, previous.violationSinceNanos());
                }
                continue;
            }

            AlertEvaluator.Evaluation evaluation =
                    evaluator.evaluate(
                            previous,
                            subsystem.window().p95Nanos(),
                            subsystem.budgetNanos(),
                            snapshot.capturedAtNanos(),
                            policy);
            states.put(id, evaluation.state());
            alertStates.put(id, evaluation.state().state());
            if (evaluation.state().violationSinceNanos() != Long.MIN_VALUE) {
                violationSince.put(id, evaluation.state().violationSinceNanos());
            }
            evaluation.transition().ifPresent(transitions::add);
        }
        return new Result(alertStates, transitions, missing, violationSince);
    }

    public synchronized void reset() {
        states.clear();
    }

    public record Result(
            Map<SubsystemId, AlertState> alertStates,
            List<AlertTransition> transitions,
            Set<SubsystemId> missingSubsystems,
            Map<SubsystemId, Long> violationSinceNanos) {

        public Result {
            Objects.requireNonNull(alertStates, "alertStates");
            Objects.requireNonNull(transitions, "transitions");
            Objects.requireNonNull(missingSubsystems, "missingSubsystems");
            Objects.requireNonNull(violationSinceNanos, "violationSinceNanos");
            alertStates = Map.copyOf(alertStates);
            transitions = List.copyOf(transitions);
            missingSubsystems = Set.copyOf(missingSubsystems);
            violationSinceNanos = Map.copyOf(violationSinceNanos);
        }
    }
}

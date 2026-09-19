# Performance Registry Contract

## Purpose

The registry is the only public B15 seam that a subsystem uses to report tick-bound work. It lives
in `rpg-core` and must remain usable without Paper.

## Registration

```text
Registration register(SubsystemId id, Duration budget, String owner)
```

Rules:

- blank IDs, non-positive budgets and blank owners are rejected;
- a second registration of the same ID is rejected without replacing the first registration;
- registration is complete before the first performance snapshot is published;
- the default project budget is 5 ms, but the concrete value is explicit in the registration/config.

## Measurement

```text
MeasurementScope begin(SubsystemId id)
PerformanceSnapshot snapshot(Window window)
```

`MeasurementScope` is a short-lived, synchronous, `AutoCloseable` value. Closing it records one
duration. It MUST NOT schedule work, perform I/O, read Bukkit state, allocate an unbounded object or
emit a log line. A missing or unclosed scope is counted as an invalid sample in tests and never
silently converted to zero.

The registry exposes an immutable snapshot containing all registered sources, including sources
with no samples. A caller can distinguish `DISABLED`, `EMPTY`, `COMPLETE` and `INCOMPLETE` windows.

## Alerting

```text
AlertTransition evaluate(PerformanceSnapshot snapshot, AlertPolicy policy)
```

The core returns transitions and counters; it does not write logs or files. Warning begins at 90 %
of the source budget. A critical transition requires 60 seconds of continuous violation. Recovery
is a separate transition. Consumers must deduplicate on the episode key.

## Threading contract

- `register`, `begin` and `snapshot` are safe for the owning tick and test harness.
- Async consumers receive immutable values and never access mutable ring buffers.
- No registry method may call Paper/Bukkit, block, join a future or perform network/file I/O.

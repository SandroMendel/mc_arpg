# Load-Test Manifest Contract

The external runner writes one JSON manifest per run. It is immutable after status becomes
`PASSED`, `FAILED`, `INVALID` or `ABORTED`.

## Required shape

```json
{
  "runId": "2026-09-11T120000Z-b15-001",
  "status": "PASSED",
  "scenario": {
    "id": "b15-full",
    "revision": 1,
    "players": 150,
    "regions": 6,
    "customMobs": 800,
    "warmupMinutes": 15,
    "measurementMinutes": 30,
    "workloads": ["movement", "combat", "coin-drops", "hordes"]
  },
  "environment": {
    "minecraft": "26.2",
    "paperBuild": "26.2.build.112-stable",
    "java": "25",
    "tool": "mc-pilot",
    "toolVersion": "<pinned-at-run>"
  },
  "hardware": {
    "cpu": "<captured>",
    "cores": "<captured>",
    "memoryBytes": "<captured>",
    "storage": "<captured>"
  },
  "artifacts": {
    "metrics": "metrics.prom",
    "report": "report.json",
    "logs": "server.log",
    "profiler": "<optional URL or file>"
  },
  "result": {
    "meanTps": 19.8,
    "msptP95": 32.1,
    "msptP99": 41.4,
    "peakCustomMobs": 800,
    "firstFailure": null
  }
}
```

## Validation

- `runId`, `scenario`, `environment`, `hardware`, artifact paths and final status are required;
- player count must be at least 150 and Custom-Mob count at least 800 for a full-load claim;
- the measurement duration must be at least 30 minutes after warm-up;
- `PASSED` requires all B15 thresholds; missing metrics force `INVALID`;
- a profiler artifact is optional for an ordinary load run but required for a profiling run;
- tool and server versions are recorded as strings and must be compared before aggregating runs.

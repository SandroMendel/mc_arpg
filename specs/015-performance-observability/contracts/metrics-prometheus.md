# Prometheus-Compatible Metrics Contract

The exporter writes the Prometheus text exposition format to the configured target file. Every
sample ends with a newline. The file is replaced atomically after a complete snapshot has been
rendered; a reader never receives a partial report.

## Names and labels

Names use the `rpg_` prefix. Labels are bounded to the fixed values `overall` and the registered
subsystem IDs. Player names, UUIDs, commands and arbitrary configuration strings are never labels.

Required metrics:

```text
rpg_tick_tps{window="current"} <gauge>
rpg_tick_mspt{quantile="p50|p95|p99"} <gauge>
rpg_tick_duration_seconds{quantile="p50|p95|p99"} <gauge>
rpg_subsystem_budget_seconds{subsystem="...",owner="..."} <gauge>
rpg_subsystem_duration_seconds{subsystem="...",quantile="p50|p95|p99"} <gauge>
rpg_subsystem_budget_ratio{subsystem="..."} <gauge>
rpg_subsystem_samples_total{subsystem="..."} <counter>
rpg_subsystem_alert_state{subsystem="..."} <gauge>
rpg_active_players <gauge>
rpg_active_custom_mobs <gauge>
rpg_performance_reports_total{status="success|failure"} <counter>
rpg_performance_export_errors_total <counter>
```

Values use seconds for durations, as required by Prometheus conventions. The exporter includes a
generation timestamp comment and a `rpg_performance_snapshot_timestamp_seconds` gauge so the
monitoring system can detect stale files.

## Failure behaviour

If the target path cannot be written, the exporter increments its failure counter and emits a
structured log record. The last complete file remains in place when atomic replacement fails.
Measurement, local alerting and local reports continue.

# ClickHouse sink deep dive

1. [x] Review and approve `docs/operations/flink/clickhouse-sink-deepdive.md`, including the target workload and role-based track.
2. [x] Add the isolated `clickhouse-sink-lab` module, connector dependency, typed sink job, and deterministic tests.
3. [x] Add the disposable ClickHouse runtime and a real sink smoke target.
4. [x] Add architecture, modeling, search/API, export, and operations guides around the sink tutorial.
5. [x] Build the reduced representative benchmark and record the first sort-key and sink-replay comparisons. The client comparison remains explicitly open because the local fixture has only exercised HTTP.
6. [ ] Exercise the remaining P0 cases: lateness, backfill, skew, contention, and CSV consistency. Replay/duplicate evidence is covered by the sink smoke.
7. [x] Run repository gates, runtime smoke, and persist the evidence.

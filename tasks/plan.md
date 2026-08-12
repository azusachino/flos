# ClickHouse sink deep dive

1. [ ] Review and approve `docs/operations/flink/clickhouse-sink-deepdive.md`, including the target workload and role-based track.
2. [ ] Add the isolated `clickhouse-sink-lab` module, connector dependency, typed sink job, and deterministic tests.
3. [ ] Add the disposable ClickHouse runtime and a real sink smoke target.
4. [ ] Add architecture, modeling, search/API, export, and operations guides around the sink tutorial.
5. [ ] Build the reduced representative benchmark and record the first table/client/sink comparisons.
6. [ ] Exercise the P0 cases: replay, lateness, backfill, skew, contention, and CSV consistency.
7. [ ] Run repository gates, runtime smoke, and persist the evidence.

---
title: ClickHouse Local Benchmark Baseline
description: Record the first reproducible local sort-key, query-shape, and sink-replay measurements.
created: 2026-08-13 00:00
modified: 2026-08-13 00:00
type: documentation
status: active
maturity: developing
tags:
    - clickhouse
    - benchmarking
    - evidence
    - apache-flink
source: https://clickhouse.com/resources/engineering/clickhouse-query-optimisation-definitive-guide
---

# ClickHouse Local Benchmark Baseline

This is the first measured result for the learning track. It is a reproducible
local baseline, not a 10B-row capacity claim.

## Fixture and controls

Run:

```sh
make clickhouse-workload
```

The harness loads 10,000 identical rows into two `MergeTree` tables:

| Table | `ORDER BY` |
| --- | --- |
| `learning.workload_events` | `(user_id, event_time, symbol, order_id)` |
| `learning.workload_events_symbol_first` | `(symbol, event_time, user_id, order_id)` |

The generated data has 1,000 users, four symbols, 10,000 orders, one August
2026 month, and three repetitions per query. Requests use the ClickHouse HTTP
interface through the repository's Python standard-library client. Results are
measured after both tables are loaded in the same local ClickHouse 26.7
container.

## First result

One run produced these p50 times in milliseconds, with the final repetition's
ClickHouse query-log counters:

| Query shape | User-first p50 / rows / bytes / marks | Symbol-first p50 / rows / bytes / marks |
| --- | --- | --- |
| User plus time range | `3.11 ms / 10000 / 160000 / 1` | `2.92 ms / 10000 / 160000 / 1` |
| Order lookup | `2.37 ms / 10000 / 80000 / 1` | `2.35 ms / 10000 / 80000 / 1` |
| Symbol plus time range | `2.84 ms / 10000 / 90052 / 1` | `2.73 ms / 10000 / 90052 / 1` |
| Date range | `2.31 ms / 1 / 16 / 0` | `2.34 ms / 1 / 16 / 0` |

The result counts were identical: `10`, `1`, `2500`, and `10000`. The harness
gets `read_rows`, `read_bytes`, and `SelectedMarks` from `system.query_log`
after `SYSTEM FLUSH LOGS`; these counters are stronger pruning evidence than
wall-clock time alone. On this tiny fixture, startup, HTTP, and cache effects
still dominate, and both keys read the full small part for the selective
queries. The numbers do not justify a production sort-key decision. The useful
result is that the experiment holds data constant while changing one modeling
variable and records the storage-engine counters.

## Sink baseline

Run:

```sh
make clickhouse-sink-smoke
```

The smoke must prove two things independently:

1. the typed RowBinary sink writes five rows;
2. replaying the same five event identities produces ten rows in plain
`MergeTree`.

One run reported `first_ms=1657` and `replay_ms=1493` for the two five-row
invocations. Those timings include JVM and local Flink startup and are retained
only as a smoke baseline.

The printed `first_ms` and `replay_ms` values include JVM and local Flink
startup, so they are correctness/replay evidence rather than throughput
measurements. A useful ingestion benchmark must keep the JVM and Flink runtime
warm, vary batch row/byte/time limits one at a time, and record rows/s, bytes/s,
request count, p95 latency, retries, and duplicate rate.

## What this baseline decides

- Keep the user-first key as the initial candidate because user-plus-time is a
  first-class search, while retaining symbol-first as a measured alternative.
- Do not add a projection or skipping index yet; this fixture has not shown a
  pruning or latency gap worth paying for.
- Keep the connector's lab batch values as teaching defaults only.
- Keep HTTP as the simple tutorial client; native protocol comparison remains a
  separate experiment when client CPU, compression, or throughput is a measured
  constraint. The selected connector client exposes only `Protocol.HTTP`, so a
  native comparison needs a separately chosen client.

## Next experiment

Scale one dimension at a time: 1M rows, realistic user/symbol cardinality,
hot-key skew, concurrent searches, and a large CSV scan. Capture rows read,
bytes read, selected marks, CPU, memory, active parts, merge backlog, and cold
versus warm cache results before promoting this baseline to an architecture
decision.

See the [capacity and benchmark decision record](clickhouse-capacity-and-benchmark.md)
for the full matrix and evidence template.

## P0 case baseline

Run:

```sh
make clickhouse-cases
```

The current local case run reported:

```text
correctness: late_event_rows=1 replacing_raw_rows=2 replacing_final_rows=1 latest_amount=12
skew: hot_user_rows=8002 cold_user_rows=2 hot_ms=3.03 cold_ms=2.52
backfill: rows_before=2000 rows_after=27000 elapsed_ms=194.82 concurrent_search_samples=47
csv: fixed_cutoff_bytes=456723 stable_after_insert=true slow_export_bytes=465979 slow_export_ms=516.11 search_p50_ms=5.11 search_max_ms=8.31 search_samples=12
```

These observations support the learning contract, not a production decision:

- an event can arrive after its event time, and a versioned replacement view
  needs `FINAL` or an equivalent read contract for deterministic latest state;
- skew and backfill are measurable while searches continue, but this local
  server has no independent ingest/read/export compute pools;
- a fixed export cutoff can make a CSV result stable after later rows arrive,
  while an unbounded current-data export would have a different contract;
- the slow export and search samples are contention evidence, not an SLO.

---
title: ClickHouse Capacity and Benchmark Decision Record
description: Measure ingestion, interactive search, export contention, and operations before choosing ClickHouse capacity.
created: 2026-08-13 00:00
modified: 2026-08-13 00:00
type: documentation
status: active
maturity: developing
tags:
    - clickhouse
    - benchmarking
    - capacity-planning
    - operations
source: https://clickhouse.com/resources/engineering/high-concurrency-sizing-user-analytics
---

# ClickHouse Capacity and Benchmark Decision Record

This is the performance engineer and platform/SRE worksheet. It prevents a
10B-row target from turning into a server-size guess.

The first measured local result is recorded in the [ClickHouse local benchmark
baseline](clickhouse-benchmark-baseline.md). Treat it as a starting point, not
as a production sizing result.

## Freeze the workload

Before comparing configurations, record:

| Dimension | Example to record |
| --- | --- |
| Rows | 10B total; daily ingest and backfill rate |
| Width | Column types, compressed bytes/row, largest row |
| Distributions | User and symbol cardinality, hot keys, time span |
| Search mix | User range, order lookup, symbol range, date range percentages |
| Concurrency | Interactive p50/p95/p99 request rate and export concurrency |
| Freshness | Maximum ingest-to-visible delay |
| Export | Rows, bytes, duration, snapshot semantics, cancellation rate |
| SLO/cost | Latency, failure rate, recovery time, storage and compute ceiling |

The local workload harness is intentionally small:

```sh
make clickhouse-workload
```

It is useful for learning query shape, not for capacity. Scale rows and
distributions independently in the real benchmark; copying 10B uniform rows
does not reproduce a 10B-row production workload.

## Change one variable

Run a baseline, then change exactly one of:

1. sort key and partitioning;
2. projection or skipping index;
3. Flink row/byte/time batch and in-flight limits;
4. HTTP versus native client and compression;
5. ClickHouse memory, merge, quota, or workload settings;
6. compute pool size or Cloud/BYOC versus self-managed topology.

Capture:

- p50/p95/p99 latency and throughput;
- rows read, bytes read, selected marks, and result rows;
- CPU, memory, queue/rejection time, and network bytes;
- insert failures, sink buffer depth, and duplicate/replay rate;
- active parts, merge backlog, mutations, cache hit/miss, and object-storage
  errors;
- export queue age and interactive-query impact;
- cost or resource consumption under the same time window.

Cold-cache and warm-cache runs are separate results. A cache-only victory is
not a durable architecture decision.

## Minimum case matrix

| Case | Baseline question | Promotion evidence |
| --- | --- | --- |
| Accepted write then timeout | Can a retry duplicate a row? | Identity counts and reconciliation after replay |
| Late/out-of-order event | What does a reader see before repair? | Visibility and correction latency |
| Hot user or symbol | Does one key dominate p99 or merges? | Skewed distribution with part/merge metrics |
| Backfill during live traffic | Can history load without starving search? | Concurrent ingest/read SLOs |
| Export during search | Are large scans isolated? | Interactive SLO under export load |
| Schema evolution | Can a Flink restore write safely after a change? | Compatibility and rollback rehearsal |
| Node/cache/object-store failure | What is the recovery boundary? | SLO and restore evidence under one injected failure |

P0 cases are architecture gates. Corrections, retention, authorization,
upgrade/rollback, and backups are release-readiness gates. Full-text search and
cost/egress refinements can remain separate until required by the product.

## Decision record template

```text
Decision:
Date and revision:
Workload fixture and distributions:
Candidate changed:
Unchanged controls:
Cold-cache result:
Warm-cache result:
Ingest result:
Interactive result:
Export/contention result:
Failure or replay result:
Cost/resource result:
Decision:
Rejected alternatives:
Evidence level: local / integration / staging / production
Open risks:
Next experiment:
```

Use “hypothesis” until the evidence level is at least the environment where
the decision will operate. A local smoke is strong evidence for wiring and weak
evidence for capacity.

## Operational ownership

The dashboard and alerts should cover query p95/p99, rejected and queued
queries, insert failures, sink buffer depth, duplicate rate, active parts,
merge and mutation backlog, cache behavior, object-storage errors, and export
queue age. Name who responds to each signal and what evidence must be retained
after an incident.

## Reading

- [ClickHouse query optimization](https://clickhouse.com/resources/engineering/clickhouse-query-optimisation-definitive-guide)
- [ClickHouse workload scheduling](https://clickhouse.com/docs/operations/workload-scheduling)
- [ClickHouse backup and restore](https://clickhouse.com/docs/operations/backup)
- [High-concurrency sizing for user analytics](https://clickhouse.com/resources/engineering/high-concurrency-sizing-user-analytics)
- [ClickHouse Academy](https://learn.clickhouse.com/)

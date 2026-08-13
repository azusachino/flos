---
title: ClickHouse Search, Clients, and CSV Exports
description: Turn user and export requirements into bounded ClickHouse query contracts and isolated workflows.
created: 2026-08-13 00:00
modified: 2026-08-13 00:00
type: documentation
status: active
maturity: developing
tags:
    - clickhouse
    - api
    - query-design
    - exports
source: https://clickhouse.com/docs/interfaces/http
---

# ClickHouse Search, Clients, and CSV Exports

This guide is for the query/API engineer and export/workflow engineer. It
starts from four user-visible filters and refuses to treat a CSV request as
just another interactive query.

## Define the query contract

Every endpoint should specify:

| Request | Required behavior |
| --- | --- |
| `user_id` plus time range | Parameterized equality and bounded time range; keyset pagination |
| `order_id` | Exact lookup with an explicit maximum result size |
| `symbol` plus time range | Parameterized equality; test both common and rare symbols |
| Date range | Maximum span, selected columns, and an asynchronous path beyond the interactive limit |

Use explicit columns, not `SELECT *`. Use a stable tuple for keyset
pagination, for example `(event_time, order_id) > (?, ?)` when the table's
ordering and tie-breaker support it. Avoid deep `OFFSET`: it makes the server
walk past rows the user will never receive.

The API layer owns authorization, parameter binding, query IDs, timeouts,
maximum rows/bytes, and cancellation. The ClickHouse client owns protocol and
compression details. The table owns pruning; do not hide a sort-key problem in
the client.

## HTTP versus native protocol

Start with the HTTP interface for the tutorial and service boundary because it
is easy to inspect, proxy, and stream. Compare the native protocol when
throughput, compression, metadata, or client feature support becomes a measured
constraint.

The ClickHouse client bundled by the selected Flink connector exposes only its
HTTP protocol enum (`Protocol.HTTP`). Therefore this track does not pretend that
the connector's Java client benchmark compares HTTP with native TCP. A native
comparison requires a separately selected client and dependency; keep that
choice explicit rather than silently changing the sink's transport.

The comparison must hold SQL and workload constant:

| Variable | Measure |
| --- | --- |
| Protocol | p50/p95/p99 latency and rows/bytes per second |
| Compression | Network bytes, CPU, and end-to-end latency |
| Client pool | In-flight requests, queue time, and server concurrency |
| Timeout/cancel | Whether abandoned requests stop consuming server resources |
| Result format | Decode CPU, memory, and streaming behavior |

Never interpolate user values into SQL. Bind them using the selected client's
parameter facility and emit a query ID that lets operators correlate API logs
with `system.query_log`.

## CSV is a workflow, not a button

An export path should look like:

```text
request -> authorize -> estimate -> queue -> execute -> stream to object storage
                                      \-> cancel / expire / retry
```

Define separately:

- maximum rows, bytes, and time per export;
- snapshot semantics: best-effort current data or a repeatable point-in-time
  result;
- column and ordering contract;
- destination, retention, and download authorization;
- cancellation and cleanup behavior;
- concurrency and resource group or compute-pool assignment.

Use a streaming result format such as `CSVWithNames` only after the query has
been authorized and bounded. Do not let a browser request hold an unbounded
HTTP connection while it scans the entire fact store. Queue large jobs and
give them a separate resource budget from interactive search.

## Contention experiment

Run the executable local case:

```sh
make clickhouse-cases
```

It starts a slow `CSVWithNames` scan and issues twelve user searches while the
scan is active. One local run produced a `516.11 ms` export and search latency
of `5.11 ms` p50 / `8.31 ms` maximum. This is a wiring and measurement
baseline only: the case has no independent compute pool, quota, or production
traffic mix.

For a meaningful capacity decision, repeat the case with a much larger table
and compare p95/p99 latency, rejected or queued queries, read bytes, merge
backlog, and export age with and without workload separation.

The result decides whether quotas are sufficient or whether independent read
compute is required. It is not safe to infer isolation from a quiet local
server.

## Cases that change the API design

- **Duplicate rows:** expose a documented identity/version policy; do not
  silently add `FINAL` to every query.
- **Corrections:** choose whether the API returns facts, latest state, or a
  reconciliation view.
- **Authorization:** prove tenant or user isolation with negative tests before
  adding cache layers.
- **Empty and huge results:** test zero rows, one row, the maximum interactive
  page, and export handoff.
- **Cancellation:** verify both client cancellation and server-side query
  termination using the same query ID.

The case harness also proves a narrower CSV contract: an export with a fixed
`event_time` cutoff remains byte-for-byte stable after rows outside that cutoff
are inserted. That is a snapshot boundary chosen by the workflow, not a claim
that ClickHouse provides a full transaction snapshot for arbitrary exports.

## Reading

- [ClickHouse HTTP interface](https://clickhouse.com/docs/interfaces/http)
- [ClickHouse async inserts](https://clickhouse.com/docs/optimize/asynchronous-inserts)
- [ClickHouse workload scheduling](https://clickhouse.com/docs/operations/workload-scheduling)
- [ClickHouse high-concurrency sizing](https://clickhouse.com/resources/engineering/high-concurrency-sizing-user-analytics)

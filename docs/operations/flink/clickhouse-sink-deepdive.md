---
title: "ClickHouse Sink Lab: Specification and Tuning Decisions"
description: Runnable Flink DataStream sink into ClickHouse, including format, batching, delivery semantics, and learning resources.
created: 2026-08-12 00:00
modified: 2026-08-13 00:00
type: documentation
status: active
maturity: developing
tags:
    - apache-flink
    - clickhouse
    - data-warehouse
    - sink
    - tuning
source: https://github.com/ClickHouse/flink-connector-clickhouse
---

# ClickHouse Sink Lab: Specification and Tuning Decisions

The first implementation is now runnable. The design remains deliberately
experimental where the evidence is local or bounded; production choices still
require workload and failure measurements.

This lab adds a second sink path to Flos: a small, bounded Flink DataStream job
that writes typed events to ClickHouse. It is intentionally separate from the
existing Kafka-to-MySQL billing pipeline so that sink behavior can be learned
without mixing in event-time windows, JDBC transactions, or the billing
domain.

## Scope

The learning track now includes a small executable lab plus role-based guides:

- `modules/flink/clickhouse-sink-lab`, a Java 17 Maven module;
- the official ClickHouse Flink DataStream connector;
- a deterministic bounded job with a typed `DataMapper` and a ClickHouse
  `MergeTree` table;
- an isolated ClickHouse container and a Make target that runs the packaged job
  against it;
- a runnable tutorial for the first successful insert;
- this sink deep dive covering format, batching, retries, delivery semantics,
  and measurement;
- companion guides for architecture, data modeling, query/search design,
  client/API behavior, export workloads, and operations/capacity planning;
- a benchmark decision record that records measured results instead of
  presenting guessed defaults as architecture.
- a curated set of official ClickHouse and Apache Flink resources.

The change will not:

- migrate or rewrite the current MySQL billing sink;
- add Kafka ingestion to the first ClickHouse lab;
- claim exactly-once delivery;
- claim production-ready ClickHouse settings from a toy workload;
- add a generic sink abstraction for future databases.

## Target workload to explore

The learning track must be able to reason about this target, without claiming
that a local laptop proves it:

- 10B+ immutable or mostly append-only records;
- interactive searches by `user_id`, `order_id`, `event_time` range, and
  `symbol`;
- bounded result sets for user-facing requests;
- CSV generation requests that may scan far more data than interactive search;
- independent scaling or throttling for ingestion, interactive search, and
  exports;
- replay, late data, and duplicate delivery from the upstream Flink job.

The first executable dataset will be a reduced, distribution-preserving sample.
The benchmark will scale row count, distinct-user ratio, symbol cardinality,
time span, result-set size, and concurrent request count separately. A row
count copied from production without its distributions is not a useful sizing
workload.

## Role-based learning track

One page can explain the sink API. It cannot teach the system. The completed
track should have these reading paths:

| Role | Question | Planned artifact |
| --- | --- | --- |
| Data architect | Is ClickHouse the right analytical store, and what is the storage/compute topology? | Architecture and deployment decision record |
| Data modeler | How should 10B+ facts be partitioned, ordered, typed, retained, and deduplicated? | MergeTree modeling guide |
| Query/API engineer | Which access paths serve user, order, date-range, and symbol searches? | Search workload and query-contract guide |
| Streaming engineer | How do Flink batches, retries, checkpoints, and replay interact with ClickHouse? | This sink deep dive and tutorial |
| Application engineer | Which client/protocol, timeout, parameterization, pagination, and result limits are safe? | Client and API guide |
| Export/workflow engineer | How do CSV jobs stream, queue, cancel, and avoid starving interactive queries? | Export workload guide |
| Platform/SRE | How are compute pools, object storage, cache, merges, quotas, backups, and upgrades operated? | Capacity and operations guide |
| Performance engineer | Which change improved read bytes, latency, throughput, or cost? | Reproducible benchmark decision record |

The artifacts should cross-link to one glossary and one evidence table. Each
recommendation must say whether it is a ClickHouse invariant, a workload
assumption, a measured result, or an unverified production hypothesis.

## Architecture decision path

Compute/storage separation is an architecture choice before it is a server
setting. The track will compare two explicit options:

1. **Managed or BYOC ClickHouse Cloud:** SharedMergeTree and shared object
   storage provide the intended separation, while independent compute pools can
   isolate ingest, hot reads, and cold/export reads.
2. **Self-managed ClickHouse:** ReplicatedMergeTree with ClickHouse Keeper and
   local storage plus S3-backed storage policies is a different operational
   model. It should not be described as equivalent to Cloud's stateless
   compute without an acceptance test for metadata, cache, failover, restore,
   and cost behavior.

The initial recommendation for a hard compute/storage-separation requirement is
to benchmark ClickHouse Cloud or BYOC first, then compare self-management only
if data residency, control, or cost makes it necessary. The decision record
must capture storage cost, cache warm-up, ingest scale-up, query scale-up,
failure recovery, export isolation, and operational ownership.

## Data-model candidates, not premature conclusions

There is no single `ORDER BY` that makes all four access paths equally strong.
The benchmark should begin with a base table candidate such as:

```sql
PARTITION BY toYYYYMM(event_time)
ORDER BY (user_id, event_time, symbol, order_id)
```

and compare it with a symbol-first candidate for symbol-heavy workloads. An
`order_id` access path is a candidate for a projection or a deliberately narrow
lookup table, not automatically a bloom filter. The benchmark must compare:

- user plus time range;
- order ID only;
- symbol plus time range;
- time range only;
- mixed filters and bounded pagination.

Partitioning is primarily for lifecycle and coarse pruning; it must not create
one partition per user or order. Projections and skipping indexes are
alternatives with write, storage, and merge costs, so they enter only after a
baseline query shows the base sort order is insufficient.

The guide will also cover numeric ID types, `DateTime64`, symbol cardinality,
`LowCardinality`, nullable columns, codecs, TTL, and the cost of wide rows.

## Exploration and decision loop

Every server, client, and sink choice follows the same loop:

1. Define query and export SLOs: p50/p95/p99 latency, freshness, throughput,
   maximum result size, maximum export duration, and cost ceiling.
2. Record the data distributions and concurrency model.
3. Build a baseline table with one sort order and no speculative projections.
4. Run the representative workload with cold and warm cache measurements.
5. Inspect `EXPLAIN`, `system.query_log`, read rows, read bytes, selected marks,
   memory, CPU, and queued/failed requests.
6. Change one variable: sort order, partitioning, projection, client setting,
   sink batch, or compute size.
7. Re-run the same workload and record the result in the decision record.
8. Test failure, replay, backpressure, export cancellation, and scale-up before
   promoting a local result to an architecture recommendation.

The benchmark must avoid `SELECT *`, unbounded offsets, unbounded exports, and
cache-only victories. User search should use parameterized SQL, explicit
columns, bounded results, and keyset pagination. CSV generation should be a
streaming or queued workflow with a separate resource budget, not a giant
interactive request.

## Configuration surface to explore

The guides will keep these layers separate:

| Layer | Decisions to measure |
| --- | --- |
| ClickHouse table | Engine, partition key, `ORDER BY`, data types, codecs, projections, skip indexes, TTL, materialized views |
| ClickHouse server | Memory and execution limits, merge capacity, cache policy, workload scheduling, query quotas, async inserts, object-storage/cache settings |
| Interactive client | HTTP versus native protocol, compression, parameter binding, query IDs, timeouts, progress/cancellation, result limits, keyset pagination |
| Export client | `CSVWithNames`, streaming, compression, queueing, cancellation, destination object storage, per-export limits |
| Flink sink | Typed RowBinary versus raw JSONEachRow, batch row/byte/time limits, in-flight requests, buffer ceiling, retries, request size, checkpoint/replay behavior |
| Deployment | Cloud/BYOC versus self-managed, compute pools, replicas, Keeper, object storage, cache warm-up, backups, restore, network path |

The first sink lab will set only connector values. It will not pretend that a
sink batch setting can solve a bad table key, an undersized compute pool, or an
unbounded CSV request.

## Additional cases to fold into the track

These cases deserve explicit experiments because they can change the table
engine, ingestion contract, or deployment shape. They are ordered by priority.

| Priority | Case | Decision to make | Minimum evidence |
| --- | --- | --- | --- |
| P0 | Accepted write followed by client timeout | How are retries, duplicate rows, and reconciliation handled? | Replay the same batch and compare event identity counts |
| P0 | Late or out-of-order event | Is the fact table immutable, or do corrections update a latest-state view? | Inject late events across partitions and measure visibility/repair cost |
| P0 | Backfill and reprocessing | Can historical data be loaded without starving live search and ingest? | Run a bounded backfill concurrently with the normal workload |
| P0 | Hot-key and symbol skew | Can one user, order, or symbol create a read or write hotspot? | Generate Zipf-like distributions and inspect p99 plus part/merge behavior |
| P0 | Search versus export contention | Are exports isolated by compute pool, workload, quota, or queue? | Run interactive searches during concurrent large CSV exports |
| P0 | CSV snapshot semantics | Does an export need a point-in-time view, repeatable ordering, or merely best-effort current data? | Export while inserts and corrections are active; verify duplicates and gaps |
| P1 | Schema evolution | How do new columns, defaults, renamed fields, and Flink checkpoint restores work? | Restore an older job state with a new mapper and evolved table |
| P1 | Corrections and deletes | Use tombstones, lightweight deletes/updates, replacement versions, or rebuilds? | Measure read overhead, merge backlog, and physical cleanup delay |
| P1 | Retention and tiering | What is hot, warm, cold, archived, or legally held? | Exercise TTL/partition movement and query behavior across tiers |
| P1 | Dimension joins | Denormalize user/order attributes, use dictionaries, or join at query time? | Compare freshness, memory, and query latency for representative joins |
| P1 | Tenant and authorization isolation | Is isolation by database, table, role, row policy, or separate service? | Prove that a user can only search/export authorized rows |
| P1 | Node/cache/storage failure | What survives a compute loss, cold cache, object-storage slowdown, or replica loss? | Meet the minimum SLO with one failure injected |
| P1 | Upgrade and rollback | Which table, connector, protocol, and checkpoint changes are compatible? | Run an upgrade/rollback rehearsal with a non-empty sink buffer |
| P2 | Full-text or fuzzy symbol search | Is exact symbol equality enough, or is a text index/search service required? | Compare exact, prefix, token, and substring requirements |
| P2 | Cost and egress control | What is the cost per stored record, search, export, and replay? | Attribute storage, compute, cache, network, and export costs |

The P0 cases are part of the first architecture decision. P1 cases become
release-readiness gates. P2 cases are allowed to remain separate until a real
product requirement appears.

### Correctness cases

The track must distinguish three different questions:

1. **Was the row accepted by ClickHouse?** A client timeout does not answer this
   reliably.
2. **Can the same event be inserted again?** The Flink sink currently has no
   exactly-once claim, so event identity and reconciliation are required.
3. **What should a reader see after a correction?** Append-only facts,
   replacement versions, tombstones, and lightweight deletes have different
   visibility and merge behavior.

We should model an immutable event identity, an event version or correction
sequence, and a read contract such as `argMax`/latest-state. `ReplacingMergeTree`
or lightweight deletes may help specific cases, but neither should be adopted
as a generic replacement for an ingestion correctness protocol. ClickHouse
describes lightweight deletes as read-time masks that are physically cleaned
later, which makes merge pressure and cleanup timing part of the decision.

### Shape and skew cases

The benchmark must include more than uniform random data:

- a few users with extremely high event volume;
- symbols with both very high and very low frequency;
- one order ID lookup versus a range returning millions of rows;
- empty, one-row, and maximum-size CSV results;
- bursty ingestion followed by quiet periods;
- concurrent schema changes or projection materialization.

These cases reveal whether a design is limited by primary-index pruning, a hot
part, merge debt, memory, admission control, object-storage latency, or simply
an API contract that permits an unsafe query.

### Operational cases

The operations guide must include dashboards and alerts for query p95/p99,
read bytes, rejected/queued queries, insert failures, sink buffer depth,
duplicate rate, active parts, merge backlog, mutation backlog, cache hit/miss,
object-storage errors, and export queue age. Workload scheduling can protect
interactive queries, but the current ClickHouse guidance notes that separate
compute is stronger isolation because CPU scheduling does not cover every
background activity such as merges and mutations.

## Proposed topology

```text
bounded Flink source
  -> typed Event
  -> ClickHouseAsyncSink
  -> ClickHouse MergeTree table
```

The job emits a small deterministic set of events. The table contains
an immutable event identity, event time, a low-cardinality dimension, and a
decimal measure, for example:

```sql
CREATE TABLE learning.sink_events
(
    event_id String,
    occurred_at DateTime64(3, 'UTC'),
    customer_id LowCardinality(String),
    amount Decimal(12, 2)
)
ENGINE = MergeTree
ORDER BY (customer_id, occurred_at, event_id);
```

`MergeTree` is deliberate for the first exercise: every successful insert is
visible as an append, so retries and duplicates remain observable. The guide
will compare this with `ReplacingMergeTree` and explain why that is a separate
deduplication design rather than a substitute for delivery guarantees.

## Decisions to implement

| Decision | Choice | Why | Boundary |
| --- | --- | --- | --- |
| Connector | `com.clickhouse.flink:flink-connector-clickhouse-2.0.0:0.2.0:all`, subject to Maven compatibility verification | Official DataStream sink and the API used by the current connector source | No Table API path in this lab |
| Row representation | Typed `DataMapper<Event>` | Keeps the Java-to-ClickHouse column contract explicit and avoids hand-written JSON serialization | Schema changes require changing the mapper and table deliberately |
| Wire format | `RowBinaryWithNamesAndTypes` through typed mode | The connector forces this format for typed rows; configuring CSV or JSONEachRow in typed mode is misleading because it is ignored | Explain raw/string mode separately |
| Raw format alternative | `JSONEachRow` for `ClickHouseConvertor<String>` examples | Readable and interoperable when another system already owns JSON serialization | Not the default path; JSON serialization and escaping become application concerns |
| Table engine | `MergeTree` | Makes append-only behavior and duplicate rows visible during the lesson | Read-side deduplication and `ReplacingMergeTree` are a later design branch |
| Client buffering | Explicit connector batch limits | The connector is already an async sink; first measure its buffering and concurrency independently | Do not enable server-side `async_insert` in the first lab, or the two queues become hard to reason about |
| Delivery claim | At-least-once / duplicates possible | The connector documents no exactly-once semantics and retries can replay accepted writes | A business key and reconciliation query are required for production use |
| Runtime | One disposable ClickHouse container plus a local packaged Flink job | Smallest real network path that proves the sink reaches ClickHouse | This is a smoke test, not a distributed Flink HA test |

## Initial tuning profile

These are teaching defaults chosen to make the buffering controls visible on a
small local workload. They are not production recommendations:

| Connector setting | Lab value | What it teaches |
| --- | ---: | --- |
| `maxBatchSize` | `100` | Row-count trigger for a request |
| `maxInFlightRequests` | `2` | Concurrent ClickHouse requests per sink writer |
| `maxBufferedRequests` | `500` | Memory/backpressure ceiling before more input is accepted |
| `maxBatchSizeInBytes` | `1048576` | Byte-size trigger for wide rows |
| `maxTimeInBufferMS` | `1000` | Latency ceiling when traffic is below the row/byte thresholds |
| `maxRecordSizeInBytes` | `65536` | Guardrail for unexpectedly large records |

The deep dive should tune one variable at a time and record throughput,
end-to-end latency, ClickHouse inserted rows, failed requests, and duplicate
counts. It will explain why increasing all six values at once is not a useful
experiment: the cause of an improvement or regression would be unknowable.

## Tutorial contract

The [Flink ClickHouse Sink Tutorial](../../concepts/flink/connectors/clickhouse-sink.md)
lets a learner complete this loop:

1. Start ClickHouse with one Make target.
2. Create or reset the `learning.sink_events` table.
3. Package the new module.
4. Run the bounded job with the default endpoint and credentials.
5. Query the row count and ordered event identities.
6. Repeat the job and observe that append-only replay creates duplicates.
7. Stop the disposable runtime and clean up.

The [local benchmark baseline](clickhouse-benchmark-baseline.md) and
`make clickhouse-cases` extend the loop with late-arrival/correction, skew,
backfill, search/export contention, and fixed-cutoff CSV experiments. Their
results remain local evidence; distributed Flink watermark recovery,
checkpoint replay, and production compute-pool isolation are separate gates.

Every command must use a repository Make target or a documented command owned
by the module. The tutorial must label local build evidence, runtime smoke
evidence, and production guidance separately.

## Deep-dive contract

This guide answers:

- What does the official sink actually do between `invoke` and ClickHouse?
- How do row count, byte size, time, concurrency, and buffering interact?
- Why is typed `RowBinaryWithNamesAndTypes` the default for this Java lab?
- When would `JSONEachRow` be preferable, and what serialization risks does it
  introduce?
- Why can a retry produce a duplicate even when Flink checkpoints are enabled?
- How should an event identity, version, and reconciliation query be designed?
- When is `ReplacingMergeTree` useful, and why is `FINAL` not a free read-time
  deduplication strategy?
- How should client-side connector buffering be compared with ClickHouse
  server-side asynchronous inserts?
- Which measurements are needed before changing a tuning value?
- What evidence would be required before calling this production-ready?

The guide will include a small failure/replay thought experiment rather than
pretending that the local bounded smoke proves distributed recovery.

## Verification criteria

The implementation is complete only when all of these are true:

- Maven resolves the selected connector artifact against Flink 2.2.1 and Java
  17.
- The module's deterministic tests cover mapper column order, representative
  values, and invalid/null input behavior.
- The packaged job runs against the disposable ClickHouse container.
- A ClickHouse query proves the expected event identities and values arrived.
- A second run demonstrates the documented duplicate behavior.
- `make check` passes, and `make validate` passes if the full documentation and
  package gate is required by the repository change.
- The tutorial and deep dive do not describe the smoke test as an HA,
  checkpoint-restore, or exactly-once acceptance test.

## Learning resources

The final tutorial will link these primary resources with a short note about
what to read and why:

- [ClickHouse Flink connector](https://github.com/ClickHouse/flink-connector-clickhouse)
  — connector API, supported formats, and async sink limits.
- [Apache Flink DataStream sinks](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/dev/datastream/sinks/)
  — the Sink API and the difference between a sink writer and a committer.
- [Flink async sink base API](https://nightlies.apache.org/flink/flink-docs-release-2.2/api/java/org/apache/flink/connector/base/sink/package-summary.html)
  — the buffering and in-flight request model used by the connector.
- [ClickHouse MergeTree family](https://clickhouse.com/docs/engines/table-engines/mergetree-family)
  — parts, ordering keys, and the storage engine behind the lab table.
- [ClickHouse ReplacingMergeTree](https://clickhouse.com/docs/engines/table-engines/mergetree-family/replacingmergetree)
  — eventual replacement semantics and the tradeoffs for replay handling.
- [ClickHouse async inserts](https://clickhouse.com/docs/optimize/asynchronous-inserts)
  — server-side buffering, which we will compare with connector-side batching.
- [ClickHouse workload scheduling](https://clickhouse.com/docs/operations/workload-scheduling)
  — CPU, I/O, query-slot, quota, and workload isolation controls.
- [ClickHouse lightweight deletes](https://clickhouse.com/docs/sql-reference/statements/delete)
  — correction and deletion semantics that must be tested against merge load.
- [ClickHouse backup and restore](https://clickhouse.com/docs/operations/backup)
  — recovery evidence for a large analytical store.
- [ClickHouse high-concurrency sizing](https://clickhouse.com/resources/engineering/high-concurrency-sizing-user-analytics)
  — a practical performance-reading exercise for throughput and concurrency.
- [ClickHouse Academy](https://learn.clickhouse.com/)
  — structured lessons for SQL, data modeling, and operating ClickHouse.

## Review questions

Before implementation, confirm these choices:

1. Is an isolated bounded sink lab the right first slice, leaving the existing
   MySQL pipeline unchanged?
2. Is typed `RowBinaryWithNamesAndTypes` the desired default, with
   `JSONEachRow` taught as the explicit raw/string alternative?
3. Is `MergeTree` plus observable duplicates the right way to teach delivery
   semantics before introducing `ReplacingMergeTree`?
4. Are the six initial tuning values acceptable as lab defaults, provided the
   guide labels them as experimental rather than production settings?

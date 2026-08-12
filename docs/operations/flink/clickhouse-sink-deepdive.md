---
title: "ClickHouse Sink Lab: Specification and Tuning Decisions"
description: Draft design for a runnable Flink DataStream sink into ClickHouse, including format, batching, delivery semantics, and learning resources.
created: 2026-08-12 00:00
modified: 2026-08-12 00:00
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

> Draft specification. Implementation starts after this contract is reviewed.

This lab adds a second sink path to Flos: a small, bounded Flink DataStream job
that writes typed events to ClickHouse. It is intentionally separate from the
existing Kafka-to-MySQL billing pipeline so that sink behavior can be learned
without mixing in event-time windows, JDBC transactions, or the billing
domain.

## Scope

The change will add:

- `modules/flink/clickhouse-sink-lab`, a Java 17 Maven module;
- the official ClickHouse Flink DataStream connector;
- a deterministic bounded job with a typed `DataMapper` and a ClickHouse
  `MergeTree` table;
- an isolated ClickHouse container and a Make target that runs the packaged job
  against it;
- a runnable tutorial for the first successful insert;
- this deeper guide covering format, batching, retries, delivery semantics,
  schema design, and measurement;
- a curated set of official ClickHouse and Apache Flink resources.

The change will not:

- migrate or rewrite the current MySQL billing sink;
- add Kafka ingestion to the first ClickHouse lab;
- claim exactly-once delivery;
- claim production-ready ClickHouse settings from a toy workload;
- add a generic sink abstraction for future databases.

## Proposed topology

```text
bounded Flink source
  -> typed Event
  -> ClickHouseAsyncSink
  -> ClickHouse MergeTree table
```

The job will emit a small deterministic set of events. The table will contain
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

The deep dive will tune one variable at a time and record throughput,
end-to-end latency, ClickHouse inserted rows, failed requests, and duplicate
counts. It will explain why increasing all six values at once is not a useful
experiment: the cause of an improvement or regression would be unknowable.

## Tutorial contract

The tutorial must let a learner complete this loop:

1. Start ClickHouse with one Make target.
2. Create or reset the `learning.sink_events` table.
3. Package the new module.
4. Run the bounded job with the default endpoint and credentials.
5. Query the row count and ordered event identities.
6. Repeat the job and observe that append-only replay creates duplicates.
7. Stop the disposable runtime and clean up.

Every command must use a repository Make target or a documented command owned
by the module. The tutorial must label local build evidence, runtime smoke
evidence, and production guidance separately.

## Deep-dive contract

The completed guide will answer:

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

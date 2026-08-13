---
title: Flink ClickHouse Sink Tutorial
description: Write typed Flink events to a local ClickHouse MergeTree and observe replay behavior.
created: 2026-08-13 00:00
modified: 2026-08-13 00:00
type: course
status: active
maturity: developing
tags:
    - apache-flink
    - clickhouse
    - sink
    - executable-tutorial
source: https://github.com/ClickHouse/flink-connector-clickhouse
---

# Flink ClickHouse Sink Tutorial

This is the first executable lesson in the ClickHouse track. A bounded Flink
job creates five typed events and writes them through the official ClickHouse
DataStream connector to a disposable local ClickHouse server.

It proves a networked sink path and makes replay visible. It does not prove
exactly-once delivery, checkpoint restore, high availability, or 10B-row
capacity.

The standalone lab exits after Flink reports successful completion. This is a
process-lifecycle workaround for the selected connector version: its sink
writer closes the write path but leaves the ClickHouse client's operation
executor alive. A long-running Flink deployment must validate connector
shutdown behavior separately; this `System.exit` is not a production cleanup
strategy.

## Run the lesson

From `vendor/flos/`:

```sh
make clickhouse-sink-smoke
```

The command packages the module, starts ClickHouse on `localhost:18123`,
truncates the lesson table, runs the job twice, and checks the row count after
each run. The verified output is:

```text
clickhouse sink smoke: inserted 5 rows, replay produced 10 append-only rows
```

Inspect the rows:

```sh
xh -b POST 'http://localhost:18123/?database=learning' \
  <<< 'SELECT event_id, occurred_at, customer_id, symbol, amount
        FROM sink_events ORDER BY occurred_at, event_id FORMAT PrettyCompact'
```

The runtime is disposable but its named volume survives a normal `down`.
Remove the container and volume only when you want a completely fresh lesson:

```sh
make clickhouse-down
podman volume rm clickhouse_clickhouse-sink-data
```

## Follow one event through the code

The path is deliberately short:

```text
ClickHouseSinkLabJob.events()
  -> ClickHouseSinkEvent
  -> ClickHouseSinkEventMapper
  -> ClickHouseConvertor
  -> ClickHouseAsyncSink
  -> learning.sink_events
```

`ClickHouseSinkEventMapper` owns the column contract. The typed connector path
uses `RowBinaryWithNamesAndTypes`, so the lesson does not hand-build JSON or
depend on column order accidentally.

The table uses:

```sql
ENGINE = MergeTree
ORDER BY (customer_id, occurred_at, symbol, event_id)
```

That is an intentionally observable append-only table. Running the same
bounded job again writes the same event identities again. A retry can therefore
be accepted by ClickHouse even when the client did not receive the response.

## Change one knob

The job keeps the connector limits as named constants so the experiment is
easy to locate:

| Knob | Lesson value | Question to ask |
| --- | ---: | --- |
| `maxBatchSize` | `100` | Does row count trigger a request? |
| `maxBatchSizeInBytes` | `1 MiB` | What happens when rows become wide? |
| `maxTimeInBufferMS` | `1000` | What latency does a low-rate stream pay? |
| `maxInFlightRequests` | `2` | Does concurrency improve throughput or pressure ClickHouse? |
| `maxBufferedRequests` | `500` | Where does backpressure appear? |

Change one value, run `make flink-clickhouse-package`, and repeat the smoke.
Do not change all values together: the result would not identify the cause.

For the small query-shape experiment:

```sh
make clickhouse-workload
```

This loads 10,000 deterministic rows into `learning.workload_events` and
compares user-plus-time, order, symbol-plus-time, and date-range filters. It is
a local comparison harness, not a capacity claim.

## What to learn next

- [Sink deep dive and tuning decisions](../../../operations/flink/clickhouse-sink-deepdive.md)
  explains formats, buffering, retry ambiguity, and evidence levels.
- [Architecture and MergeTree modeling](../../../operations/flink/clickhouse-architecture-and-modeling.md)
  separates compute/storage topology from table-key decisions.
- [Search, clients, and CSV exports](../../../operations/flink/clickhouse-search-and-export.md)
  turns user-facing requests into bounded query contracts.
- [Capacity and benchmark decisions](../../../operations/flink/clickhouse-capacity-and-benchmark.md)
  defines the experiment matrix for a 10B+ workload.
- [Local benchmark baseline](../../../operations/flink/clickhouse-benchmark-baseline.md)
  records the first sort-key and replay measurements.

## Official reading

- [ClickHouse Flink connector](https://github.com/ClickHouse/flink-connector-clickhouse)
  — connector API and supported conversion paths.
- [Flink DataStream sinks](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/dev/datastream/sinks/)
  — the Flink sink lifecycle.
- [ClickHouse MergeTree](https://clickhouse.com/docs/engines/table-engines/mergetree-family)
  — parts, ordering, and primary-index pruning.

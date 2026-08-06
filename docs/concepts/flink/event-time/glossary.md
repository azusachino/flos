---
title: Flink Streaming Glossary
description: Official Flink terminology translated into the billing example and production operations.
created: 2026-07-30 00:00
modified: 2026-08-06 00:00
type: concept
status: maintained
maturity: developing
tags:
    - apache-flink
    - devops
    - event-time
    - operations
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/concepts/glossary/
---

# Flink Streaming Glossary

This glossary follows Apache Flink 2.2 terminology and adds two local interpretations:

- **Billing meaning** connects the term to the five-minute user-fee example.
- **DevOps meaning** explains what the term changes during deployment, monitoring, failure recovery, or capacity planning.

Read [Event Time Fundamentals](fundamentals.md) for the concepts in execution order. Return here when a Flink API, Web UI, metric, or incident report uses an unfamiliar term.

## Data and time

### Record or event

A record is one element flowing through a data stream. “Event” emphasizes that the record describes something that happened.

**Billing meaning:** one order DTO containing `userId`, fee, sequence, and `timestampTs`.

**DevOps meaning:** record rates and serialization size drive network, CPU, and checkpoint pressure. Track input/output record rates before assuming a slow watermark is itself the bottleneck.

### Bounded stream

A bounded stream has a known end.

**Billing meaning:** the in-memory lab contains five records and then finishes.

**DevOps meaning:** a bounded source emits final progress when it ends, allowing remaining event-time windows to close. A passing bounded lab does not prove that an unbounded Kafka job will advance watermarks correctly.

### Unbounded stream

An unbounded stream has no expected end and may continue producing records indefinitely.

**Billing meaning:** the later Kafka order topic is unbounded.

**DevOps meaning:** there is no final source watermark during normal operation. Window completion depends continuously on source activity, watermark configuration, and idleness handling.

### Event timestamp

The event timestamp is the millisecond value attached to a record for event-time operations.

**Billing meaning:** `dto.timestampTs` or `event.occurredAt().toEpochMilli()` determines the event's position on the business timeline.

**DevOps meaning:** validate timestamp units, timezone conversion, clock behavior, and default values at ingestion. A seconds-versus-milliseconds error can create windows near 1970 or far in the future and make watermark symptoms misleading.

### Event time

Event time describes when the event happened according to data inside or attached to the record.

**Billing meaning:** a `12:03` order belongs to `[12:00,12:05)` even if Flink processes it at `12:04`.

**DevOps meaning:** replaying historical data should reproduce the same window assignment. Event-time lag may be intentional and should not be confused with CPU saturation.

### Processing time

Processing time is the wall clock of the machine executing the operator.

**Billing meaning:** it answers when Flink handles the order, not which historical fee report owns it.

**DevOps meaning:** processing-time timers depend on worker clocks and restart timing. Ensure time synchronization, but do not expect NTP to repair bad event timestamps.

### Watermark

A watermark is Flink's progress estimate for event time. An event timestamp behind the current watermark may be late.

**Billing meaning:** when the effective watermark passes the end of `[12:00,12:05)`, the window can emit Alice's report.

**DevOps meaning:** inspect `currentInputWatermark` and `currentOutputWatermark` per task. A watermark that stops moving can indicate a quiet input, a lagging partition, a blocked upstream operator, or missing idleness—not necessarily a failed job.

### WatermarkStrategy

`WatermarkStrategy` combines a timestamp assigner with a watermark generator.

**Billing meaning:** the lab extracts `occurredAt` and assumes at most 30 seconds of timestamp disorder.

**DevOps meaning:** the disorder bound is an operational service-level assumption. Measure real arrival disorder before setting it, document the accepted report delay, and alert when observed lateness violates it.

### TimestampAssigner

A timestamp assigner extracts or assigns the event timestamp for each record.

**Billing meaning:** it maps `dto.timestampTs` to Flink's internal event timestamp.

**DevOps meaning:** treat this as an ingestion contract. Add validation and telemetry for missing, future, malformed, or unit-mismatched timestamps.

### WatermarkGenerator

A watermark generator observes event timestamps and emits watermarks according to a progress policy.

**Billing meaning:** bounded out-of-orderness holds the watermark behind the greatest timestamp observed by the configured delay.

**DevOps meaning:** watermarks are emitted periodically, so output delay includes both the strategy's delay and the periodic emission interval. Custom generators need correctness tests and operational dashboards.

### Idle input

An input is idle when it has produced no records for a configured duration and is temporarily excluded from downstream watermark calculation.

**Billing meaning:** a quiet Kafka partition should not hold every five-minute report open forever.

**DevOps meaning:** choose the idle timeout from normal traffic gaps. Too short can exclude a temporarily quiet input aggressively; too long delays every downstream event-time result.

### Watermark alignment

Watermark alignment limits how far one source or split may advance ahead of slower members of an alignment group.

**Billing meaning:** it can stop a fast order partition or source from creating excessive downstream window state while another source lags.

**DevOps meaning:** alignment is a flow-control tool, not the same as idleness. Monitor whether alignment pauses healthy sources and trades throughput for bounded state growth.

## Partitioning and execution

### Kafka partition

A Kafka partition is an ordered log shard. Kafka ordering exists within one partition, not across the topic.

**Billing meaning:** using `userId` as the Kafka record key keeps one user's orders in one Kafka partition.

**DevOps meaning:** partition count bounds Kafka source parallelism and affects skew, consumer assignment, recovery time, and per-partition watermark progress. A hot user can still create a hot partition.

### Key

A Flink key is the value selected by `keyBy` to partition keyed state.

**Billing meaning:** `userId` is the key. `timestampTs` is not; it selects the event-time window.

**DevOps meaning:** key distribution determines load and state skew. Watch subtask-level throughput, busy time, and state size rather than only job-wide averages.

### Keyed stream

A keyed stream is logically partitioned so records with the same key are processed by the same parallel operator instance.

**Billing meaning:** every Alice order reaches Alice's keyed window state.

**DevOps meaning:** changing key serialization or key selection can change state distribution and restore compatibility. Treat key schema as a stateful contract.

### Operator

An operator is the runtime implementation of a stream operation such as source, map, window, or sink.

**Billing meaning:** timestamp assignment, keyed window aggregation, and printing are operators in the job graph.

**DevOps meaning:** name operators and assign stable UIDs before production. Operator names help humans navigate metrics; UIDs map state back during savepoint restore.

### Transformation

A transformation is the API-level operation that produces a new stream, such as `map`, `keyBy`, or `aggregate`.

**Billing meaning:** each chained Java call describes a transformation.

**DevOps meaning:** transformations may become separate operators or be chained at runtime. The source code line count does not directly predict task count or network exchanges.

### Parallelism

Parallelism is the number of parallel instances executing an operator.

**Billing meaning:** the concept lab uses parallelism one for deterministic output.

**DevOps meaning:** parallelism controls concurrency, resource demand, and state distribution. It should be chosen alongside Kafka partitions, maximum parallelism, available slots, and sink capacity.

### Subtask

A subtask is one parallel instance of an operator or operator chain processing a partition of the stream.

**Billing meaning:** at parallelism 16, a keyed window operator has 16 subtasks and each owns a subset of user keys.

**DevOps meaning:** diagnose skew at subtask granularity. Compare `busyTimeMsPerSecond`, `idleTimeMsPerSecond`, `backPressuredTimeMsPerSecond`, records, watermarks, and state size across subtasks.

### Task slot

A task slot is a TaskManager resource allocation unit. With slot sharing, one slot can contain a pipeline of subtasks from the same job.

**Billing meaning:** slots provide capacity for the source, window, and sink subtasks.

**DevOps meaning:** slots partition managed memory but do not provide CPU isolation. More slots per TaskManager improve utilization while increasing shared-JVM contention and failure blast radius. The [Slot Sharing Groups and Parallelism Lab](../operators/slot-sharing-and-parallelism.md) proves exactly how many slots a chain of operators needs, and how pulling one into its own slot sharing group changes that count.

### Backpressure

Backpressure occurs when a downstream operator cannot accept data as quickly as upstream produces it.

**Billing meaning:** a slow report sink can propagate pressure back through the window operator to the source.

**DevOps meaning:** use the Web UI and `backPressuredTimeMsPerSecond`, `busyTimeMsPerSecond`, and `idleTimeMsPerSecond`. A stalled watermark may be a consequence of backpressure, but watermark lag and backpressure are different signals.

## Windows and state

### Window

A window groups records into a finite bucket for computation.

**Billing meaning:** state is effectively scoped by `(userId, [windowStart, windowEnd))`.

**DevOps meaning:** window size, allowed lateness, key cardinality, and traffic determine how much concurrent state remains open.

### Window assigner

A window assigner determines which window or windows receive an event.

**Billing meaning:** `TumblingEventTimeWindows.of(Duration.ofMinutes(5))` uses the assigned event timestamp to select one clock-aligned bucket.

**DevOps meaning:** changing window type, size, or offset changes state layout and output semantics. Treat it as a migration, not a harmless tuning flag.

### Tumbling window

A tumbling window has fixed size and does not overlap adjacent windows.

**Billing meaning:** `[12:00,12:05)` and `[12:05,12:10)` cannot count the same order.

**DevOps meaning:** larger windows keep more keyed state open and increase recovery work. The reporting interval and accepted output delay are separate settings.

### Trigger

A trigger decides when a window produces a result.

**Billing meaning:** the default event-time trigger fires when the watermark reaches the end of the window.

**DevOps meaning:** early or repeated firing changes sink traffic and update semantics. Ensure the sink supports inserts, upserts, or retractions required by the trigger policy.

### AggregateFunction

An `AggregateFunction` incrementally updates a compact accumulator as records arrive.

**Billing meaning:** the lab stores total fee and event count rather than all five minutes of raw orders.

**DevOps meaning:** compact accumulators reduce managed state, checkpoint size, and recovery work. Their schema is still state and must remain restore-compatible.

### Window state

Window state is managed state scoped to a key and window until cleanup.

**Billing meaning:** Alice's `12:00–12:05` accumulator is independent from Bob's and from Alice's next window.

**DevOps meaning:** rising state size may reflect watermark lag, excessive allowed lateness, high key cardinality, or traffic growth. Diagnose the cause before only adding memory.

### State backend

The state backend determines how working state is represented locally and participates in checkpoint snapshots.

**Billing meaning:** the fee accumulators become managed window state.

**DevOps meaning:** backend selection affects heap usage, serialization cost, throughput, checkpoint behavior, and recovery. Checkpoint storage is a separate concern from the local state representation.

### Late event

A late event belongs to a window whose end is already behind the current watermark.

**Billing meaning:** an order for `12:03` arriving after the effective watermark passes `12:05` is late for the first window.

**DevOps meaning:** monitor `numLateRecordsDropped` and business reconciliation counts. A low processing latency does not guarantee a low late-event rate if source timestamps or partitions lag.

### Allowed lateness

Allowed lateness retains window state beyond the first event-time firing so some late events can update it.

**Billing meaning:** the report may emit once at `12:05` and emit a correction when another permitted order arrives later.

**DevOps meaning:** allowed lateness increases state retention and sink updates. Define idempotent upsert keys and distinguish provisional from final reports.

### Side output

A side output is a separately typed output stream emitted by an operator.

**Billing meaning:** irrecoverably late orders can go to a reconciliation stream instead of disappearing.

**DevOps meaning:** monitor, retain, and alert on the side-output sink. Creating a side output without operating its consumer merely relocates data loss.

## Reliability and lifecycle

### Checkpoint

A checkpoint is a runtime-managed consistent snapshot of state and source positions used for failure recovery.

**Billing meaning:** it protects the open fee accumulators and the corresponding source progress.

**DevOps meaning:** monitor checkpoint success, duration, size, alignment, storage health, and restore time. Checkpoints are normally automatic operational recovery artifacts.

### Savepoint

A savepoint is a deliberately created consistent state image used for controlled stop/resume, upgrades, forks, or rescaling.

**Billing meaning:** take one before deploying a compatible revision of the stateful billing job.

**DevOps meaning:** assign stable operator UIDs, store savepoints in durable storage accessible to JobManager and TaskManagers, test restore procedures, and manage snapshot ownership.

### Operator UID

An operator UID is a stable identifier used to map saved state back to a stateful operator.

**Billing meaning:** the keyed window operator should retain the same UID across compatible releases.

**DevOps meaning:** generated IDs are sensitive to topology changes. Explicit UIDs are part of the upgrade and rollback contract.

## Operational diagnosis

| Symptom | Inspect first | Common interpretation |
| --- | --- | --- |
| Five-minute report never appears | `currentInputWatermark`, `currentOutputWatermark`, source activity | A partition or upstream operator is holding back event-time progress |
| Watermark is old but records are flowing | Per-subtask watermarks and timestamp values | One split is lagging, timestamps are malformed, or disorder exceeds the configured bound |
| Watermark stops when traffic becomes sparse | Source split activity and idleness configuration | A quiet input remains active and constrains the minimum |
| Late drops increase | `numLateRecordsDropped`, source delay distribution | The completeness bound is too small or an upstream timestamp/partition is delayed |
| One subtask is hot | Busy time, records, state size per subtask | Key skew or Kafka partition skew |
| State grows continuously | Watermark progress, open windows, allowed lateness, key cardinality | Windows are not cleaning up or the workload/state policy expanded |
| Checkpoints slow down | Checkpoint duration/size, backpressure, state backend IO | Growing state, barrier delay, storage saturation, or backend pressure |
| Restore fails after a code change | Operator UIDs and state serializer compatibility | Saved state can no longer map safely to the revised topology or schema |

Do not use one metric as proof. Correlate event-time progress, traffic, task activity, state, checkpoint health, and business output.

## Official references

- [Flink glossary](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/concepts/glossary/)
- [Generating watermarks](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/dev/datastream/event-time/generating_watermarks/)
- [Flink architecture](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/concepts/flink-architecture/)
- [Metrics](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/metrics/)
- [Debugging event time](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/debugging/debugging_event_time/)
- [State backends](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/state_backends/)
- [Checkpoints](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/checkpoints/)
- [Savepoints](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/savepoints/)

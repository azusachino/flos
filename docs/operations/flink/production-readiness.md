---
title: Flink Production Readiness
description: Define state, recovery, event-time, capacity, sink, and security contracts before deployment.
created: 2026-07-30 19:29
modified: 2026-07-30 19:29
type: documentation
status: maintained
maturity: developing
tags:
    - apache-flink
    - production-readiness
    - reliability
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/production_ready/
---

# Flink Production Readiness

A job is production-ready only when its correctness and recovery contracts are explicit and tested. A green Maven build is necessary, but it does not answer how state survives failure or how duplicate external writes are reconciled.

## Readiness record

Maintain one reviewed record per job:

```yaml
job:
    name: user-fee-report
    owner: billing-platform
    artifact: pipeline-lab.jar
    flinkVersion: 2.2.1
    javaVersion: 17
    parallelism: 16
    maxParallelism: 128

source:
    type: kafka
    topic: order-events
    partitions: 16
    recordKey: userId
    timestampField: timestampTs
    timestampUnit: epoch-milliseconds
    replayStartPolicy: committed-offsets

eventTime:
    window: 5m-tumbling
    maxOutOfOrderness: 30s
    idleTimeout: 1m
    lateEventPolicy: reconciliation-side-output

state:
    backend: rocksdb
    checkpointInterval: 30s
    checkpointStorage: s3://example-flink/checkpoints
    savepointStorage: s3://example-flink/savepoints
    restoreDrillRequired: true

sink:
    type: jdbc-upsert
    delivery: at-least-once
    idempotencyKey:
        - userId
        - windowStart

operations:
    availabilitySlo: 99.9%
    reportDelaySlo: 2m
    recoveryTimeObjective: 15m
    recoveryPointObjective: last-completed-checkpoint
```

This is an example contract, not a committed production configuration. Endpoint names, credentials, and storage policies must come from the target environment.

## Stable operator identity

Flink maps saved state back to operators by operator UID. Auto-generated IDs depend on topology and are fragile across code changes.

Name operators for humans and assign UIDs for state restoration:

```java
environment
        .fromSource(source, watermarks, "order-source")
        .name("order-source")
        .uid("order-source-v1")
        .keyBy(OrderEvent::customerId)
        .window(TumblingEventTimeWindows.of(Duration.ofMinutes(5)))
        .aggregate(new FeeAggregate(), new FeeWindow())
        .name("five-minute-user-fee")
        .uid("five-minute-user-fee-v1")
        .sinkTo(reportSink)
        .name("bill-fee-report-sink")
        .uid("bill-fee-report-sink-v1");
```

UID rules:

- Do not derive UIDs from display names or class names.
- Do not rename a UID merely because code was reorganized.
- Record intentional state removal in the change review.
- Fail submission when a production job contains an unintended auto-generated UID.
- Treat accumulator and serializer changes as state migrations.

## Parallelism contract

Parallelism describes current concurrency. Maximum parallelism determines the number of key groups and constrains future rescaling.

```java
environment.setParallelism(16);
environment.setMaxParallelism(128);
```

Review:

- Kafka partition count and expected partition growth
- key cardinality and skew
- state size per keyed-window subtask
- sink concurrency limits
- available task slots and TaskManager failure domains
- restore duration after redistributing key groups

For 16 Kafka partitions, source parallelism above 16 does not create more active Kafka readers for that topic. Downstream keyed operators may use different parallelism if measured independently.

## Checkpoint contract

The current repository pipeline calls:

```java
environment.enableCheckpointing(10_000);
```

That only selects a checkpoint interval. Production adoption must also define durable storage, timeout, minimum pause, concurrency, retention, state backend, and failure policy.

An illustrative programmatic configuration is:

```java
environment.enableCheckpointing(Duration.ofSeconds(30).toMillis());

var checkpoints = environment.getCheckpointConfig();
checkpoints.setCheckpointTimeout(Duration.ofMinutes(10).toMillis());
checkpoints.setMinPauseBetweenCheckpoints(Duration.ofSeconds(10).toMillis());
checkpoints.setMaxConcurrentCheckpoints(1);
checkpoints.setExternalizedCheckpointRetention(
        CheckpointConfig.ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);
```

Prefer environment-owned paths and backend selection in deployment configuration:

```yaml
flinkConfiguration:
    state.backend.type: rocksdb
    execution.checkpointing.dir: s3://example-flink/checkpoints
    execution.checkpointing.savepoint-dir: s3://example-flink/savepoints
    execution.checkpointing.interval: 30s
    execution.checkpointing.timeout: 10m
    execution.checkpointing.min-pause: 10s
    execution.checkpointing.max-concurrent-checkpoints: "1"
```

Do not copy placeholder buckets into a deployment. The JobManager and TaskManagers require the correct filesystem plugin, credentials, network access, encryption, lifecycle policy, and write/read permissions.

Checkpoint acceptance requires evidence:

1. Several checkpoints complete under representative load.
2. State size and duration are recorded.
3. A TaskManager failure restores from a checkpoint.
4. The source resumes from the corresponding position.
5. The sink result remains correct after replay.

## Event-time contract

Document:

```text
timestamp source       dto.timestampTs
timestamp unit         epoch milliseconds
window                 five-minute tumbling
out-of-order bound     measured and approved
idle timeout           measured normal quiet period
allowed lateness       explicit duration
too-late handling      side output, correction, or drop
report finality        provisional or final
```

Monitor the contract:

- `currentInputWatermark`
- `currentOutputWatermark`
- `numLateRecordsDropped`
- source and partition lag
- reconciliation stream volume
- business report delay

Watermark delay and allowed lateness are correctness/product policies, not throughput tuning knobs.

## Sink contract

Flink state recovery does not undo external writes. The sink must define behavior under replay.

For an idempotent five-minute report:

```sql
CREATE TABLE bill_fee_report (
    user_id VARCHAR(128) NOT NULL,
    window_start TIMESTAMP(3) NOT NULL,
    window_end TIMESTAMP(3) NOT NULL,
    total_fee DECIMAL(19, 2) NOT NULL,
    event_count BIGINT NOT NULL,
    report_version BIGINT NOT NULL,
    PRIMARY KEY (user_id, window_start)
);
```

An upsert key of `(user_id, window_start)` permits replaying the same logical report without appending duplicates. It does not deduplicate duplicate order events already present in the source.

Review:

- delivery guarantee supported by the actual connector
- transaction boundaries
- idempotency key
- correction/version semantics
- retry and timeout behavior
- downstream visibility during partial failure
- reconciliation procedure

## Restart and high availability

Define a restart strategy rather than inheriting an unknown cluster default:

```yaml
flinkConfiguration:
    restart-strategy.type: exponential-delay
    restart-strategy.exponential-delay.initial-backoff: 1s
    restart-strategy.exponential-delay.max-backoff: 2m
    restart-strategy.exponential-delay.backoff-multiplier: "2.0"
    restart-strategy.exponential-delay.reset-backoff-threshold: 10m
    restart-strategy.exponential-delay.jitter-factor: "0.1"
```

For Kubernetes production deployments, configure JobManager high availability and durable HA metadata. Confirm that cluster IDs, storage paths, RBAC, and cleanup ownership are unique and intentional.

Restart policy questions:

- Which failures should restart automatically?
- When should repeated failure page a human?
- How long may the job remain unavailable?
- Which checkpoint will recovery use?
- Can the sink safely observe replay?

## Security contract

Flink can submit and execute user code. Do not expose the REST API or dashboard directly to the public internet.

Require:

- authenticated and authorized access
- namespace/service-account least privilege
- secrets outside source control
- TLS where traffic crosses trust boundaries
- restricted object-store and Kafka permissions
- image provenance and vulnerability review
- dependency and Flink CVE review
- audit trail for deploy, savepoint, restore, cancel, and delete operations

## Readiness gate

Do not approve production deployment until every item is answered:

- [ ] Source schema, timestamp, key, ordering, replay, and retention contracts are documented.
- [ ] Sink delivery, idempotency, correction, and reconciliation contracts are tested.
- [ ] Stateful operators have stable UIDs.
- [ ] Parallelism and maximum parallelism are explicit.
- [ ] State backend and durable checkpoint/savepoint storage are configured.
- [ ] Checkpoint completion and failure recovery are tested under load.
- [ ] Watermark, lateness, and idleness policies are measured.
- [ ] Dashboards and alerts cover availability, traffic, event time, state, and recovery.
- [ ] Restart, HA, security, and secret management are reviewed.
- [ ] Upgrade and rollback procedures have a compatible restore artifact.
- [ ] Business output is verified independently of Flink job status.

## Official references

- [Production readiness checklist](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/production_ready/)
- [Checkpoints](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/checkpoints/)
- [State backends](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/state_backends/)
- [Savepoints](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/savepoints/)

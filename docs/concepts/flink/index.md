---
title: Apache Flink
description: Learn how Flink turns stream transformations into distributed, stateful computations.
created: 2026-07-29 00:00
modified: 2026-08-06 00:00
type: map
status: active
maturity: developing
tags:
    - apache-flink
    - stream-processing
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/
---

# Apache Flink

Apache Flink executes stateful computations over bounded and unbounded streams. This concept begins with operators because they are the vocabulary used to express a dataflow.

The first lab processes purchase events through validation, normalization, keyed partitioning, and incremental reduction. Each transformation has a direct unit test, and the assembled job can run on a local Flink session cluster.

The repository pins Flink 2.2.1 and Java 17 to match the Flink Kubernetes Operator 1.15.0 compatibility line used by the production-oriented environment. The local Podman Compose cluster teaches the same Flink runtime without pretending to execute Kubernetes reconciliation.

The [Checkpoint and Recovery Lab](state/checkpoint-recovery.md) builds on the event-time lesson with an actual completed checkpoint, injected task failure, automatic restart, and restored five-minute window.

The [Keyed State TTL and Backend Choice Lab](state/ttl-and-backends.md) teaches the complementary half of state: expiring it on purpose, and proving that logic holds behind either the HashMap or RocksDB state backend.

The [Restart Strategies and the Give-Up Boundary Lab](state/restart-strategies.md) isolates the retry policy itself: how many times a job actually retries before it is declared failed, and whether that safety net exists at all by default.

The [Slot Sharing Groups and Parallelism Lab](operators/slot-sharing-and-parallelism.md) proves how default slot sharing packs an entire operator chain into one slot per parallel instance, and how isolating an operator into its own group changes the concurrent slot requirement — against a real, fixed-size local cluster.

The [Flink ClickHouse Sink Tutorial](connectors/clickhouse-sink.md) writes typed
events through the official asynchronous ClickHouse connector, then repeats the
bounded job so append-only replay is visible. Continue with the [ClickHouse
architecture and modeling guide](../../operations/flink/clickhouse-architecture-and-modeling.md)
for the 10B+ workload decisions.

After learning the transformations and event-time model, continue with the [Flink Operations Handbook](../../operations/flink/index.md). It connects the code to production readiness, observability, recovery, upgrades, rescaling, incident response, and retirement.

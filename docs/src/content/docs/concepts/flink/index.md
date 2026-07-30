---
title: Apache Flink
description: Learn how Flink turns stream transformations into distributed, stateful computations.
created: 2026-07-29 00:00
modified: 2026-07-30 19:29
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

After learning the transformations and event-time model, continue with the [Flink Operations Handbook](../../../operations/flink/). It connects the code to production readiness, observability, recovery, upgrades, rescaling, incident response, and retirement.

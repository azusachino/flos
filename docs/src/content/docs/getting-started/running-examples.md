---
title: Running examples
description: Build and execute concept modules through the project Makefile.
created: 2026-07-29 00:00
modified: 2026-07-30 21:51
type: documentation
status: maintained
maturity: stable
tags:
    - setup
    - executable-tutorial
---

# Running examples

Run the repository checks:

```sh
make check
```

Run the bounded event-time concepts lab without external services:

```sh
make flink-event-time
```

Run the checkpoint, artificial failure, and recovery integration test:

```sh
make flink-recovery
```

Run the revision A → canonical savepoint → rescaled revision B experiment:

```sh
make flink-savepoint-upgrade
```

This starts an embedded Flink MiniCluster, so the process must be allowed to bind local ephemeral ports.

Build and start the Flink operator lab:

```sh
make flink-up
make flink-smoke
make flink-billing-smoke
make flink-down
```

The bounded smoke test waits for a real TaskManager, submits the packaged job through Flink's command-line client, and verifies that the REST API reports the job as `FINISHED`.

The billing smoke creates a temporary 16-partition Kafka topic, publishes monotonic per-partition sequences, runs the event-time billing job, verifies Kafka consumer-group coverage and exact MySQL report totals, cancels the unbounded job, and deletes its topic.

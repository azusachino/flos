---
title: Running examples
description: Build and execute concept modules through the project Makefile.
created: 2026-07-29 00:00
modified: 2026-08-06 00:00
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

Run the keyed state TTL and backend-choice lab:

```sh
make flink-state-ttl
```

Run the restart-strategy lab, which recovers from two artificial failures and prints both events:

```sh
make flink-restart-strategy
```

Run the slot sharing lab, which schedules two subtasks concurrently on a fixed-size local cluster:

```sh
make flink-slot-sharing
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
make flink-observability-smoke
make flink-down
```

The bounded smoke test waits for a real TaskManager, submits the packaged job through Flink's command-line client, and verifies that the REST API reports the job as `FINISHED`.

The billing smoke creates a temporary 16-partition Kafka topic, publishes monotonic per-partition sequences, runs the event-time billing job, verifies an initial report and a late correction, routes one event beyond allowed lateness, reconciles source audit versus report and rejection totals, cancels the unbounded job, and deletes its topic.

The observability target runs that same billing acceptance and additionally verifies two live Prometheus scrape targets, four loaded alert rules, job metrics, and the six-panel provisioned Grafana dashboard.

Run the Netty concept labs, each a standalone server with no external services:

```sh
make netty-event-loop
make netty-framing
make netty-backpressure
make netty-lifecycle
```

Each process listens on its own port until stopped with `Ctrl+C`. The [event loop](../concepts/netty/event-loop.md), [framing](../concepts/netty/framing.md), [backpressure](../concepts/netty/backpressure.md), and [connection lifecycle](../concepts/netty/connection-lifecycle.md) tutorials document what to send each one and what to expect back.

Build, run, and health-check the event loop lab as a real container image:

```sh
make netty-up
make netty-smoke
make netty-down
```

The smoke test connects to the running container the same way the manual `nc` instructions do, proving the containerized server, not just the jar. See [Containerizing a Netty Service](../operations/netty/deployment.md) for what the Dockerfile and health check actually do.

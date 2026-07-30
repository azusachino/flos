---
title: Running examples
description: Build and execute concept modules through the project Makefile.
created: 2026-07-29 00:00
modified: 2026-07-30 21:34
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

This starts an embedded Flink MiniCluster, so the process must be allowed to bind local ephemeral ports.

Build and start the Flink operator lab:

```sh
make flink-up
make flink-smoke
make flink-down
```

The smoke test waits for a real TaskManager, submits the packaged job through Flink's command-line client, and verifies that the REST API reports the job as `FINISHED`.

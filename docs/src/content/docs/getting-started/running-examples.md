---
title: Running examples
description: Build and execute concept modules through the project Makefile.
created: 2026-07-29 00:00
modified: 2026-07-29 00:00
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

Build and start the Flink operator lab:

```sh
make flink-up
make flink-smoke
make flink-down
```

The smoke test waits for a real TaskManager, submits the packaged job through Flink's command-line client, and verifies that the REST API reports the job as `FINISHED`.

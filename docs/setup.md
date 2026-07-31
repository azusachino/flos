---
title: Setup
description: Provision tools, then verify source and documentation build correctly.
created: 2026-07-29 00:00
modified: 2026-07-31 00:00
type: documentation
status: maintained
maturity: stable
tags:
  - setup
  - tooling
---

# Setup

## Provision tools

```sh
mise install
make setup
```

The required tool versions live in `mise.toml`. Mise is optional for contributors who already have compatible Java 17, Maven, Python, uv, and Podman installations.

Examples target Flink 2.2.1. Kubernetes deployment material targets Flink Kubernetes Operator 1.15.0.

## Verify source and documentation

```sh
make check
make docs-check
```

## Run the Flink lab

```sh
make flink-up
make flink-smoke
make flink-down
```

`make flink-smoke` proves the real submission route. A passing Maven test alone proves operator function behavior but not cluster execution.

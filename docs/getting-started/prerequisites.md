---
title: Prerequisites
description: Install the portable toolchain used to build, test, and run Flos.
created: 2026-07-29 00:00
modified: 2026-07-31 00:00
type: documentation
status: maintained
maturity: stable
tags:
    - setup
    - tooling
---

# Prerequisites

The shortest setup uses [mise](https://mise.jdx.dev/) to install Java 17, Maven, Python, and uv:

```sh
mise install
make setup
make check
```

To browse the upstream implementations at the versions used by the tutorial, initialize the shallow source references:

```sh
git submodule update --init --depth 1
```

Mise is optional. Contributors may install the versions declared in `mise.toml` using their preferred package manager.

Podman 5 or another Compose-compatible container engine is required only for runtime labs. Java examples and the documentation build do not require a running container engine.

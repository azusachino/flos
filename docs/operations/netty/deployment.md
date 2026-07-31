---
title: Containerizing a Netty Service
description: Build, run, and health-check the event loop lab as a real container image, with an executable acceptance smoke test.
created: 2026-07-31 11:05
modified: 2026-07-31 11:05
type: documentation
status: maintained
maturity: developing
tags:
    - netty
    - devops
    - deployment
    - containers
source: https://docs.docker.com/build/building/multi-stage/
---

# Containerizing a Netty Service

Every prior Netty lab runs as `java -jar` on the host. That is enough to learn the pipeline, but it is not how a service actually ships. This page's only question is:

> What does it take to package one of these labs as a real, health-checked, non-root container image — and prove it still works once it's running inside one?

The [Flink environment](../flink/index.md) mounts pre-built jars into official upstream images; Flink itself is third-party software this project configures. The event-loop lab is _our own_ code, so it needs its own image, built from source. That distinction is the first real DevOps decision here: mount config into someone else's image, or build your own.

## Source code map

| File | What to learn from it |
| --- | --- |
| [`Dockerfile`](https://github.com/azusachino/flos/blob/main/environments/netty/event-loop/Dockerfile) | Multi-stage build: a full Maven/JDK stage, discarded, feeding a minimal JRE runtime stage |
| [`compose.yaml`](https://github.com/azusachino/flos/blob/main/environments/netty/compose.yaml) | Builds and runs the image with a health check, the same Podman Compose tooling already used for Flink |
| [`netty_deployment_smoke.py`](https://github.com/azusachino/flos/blob/main/scripts/netty_deployment_smoke.py) | Proves the _running container_, not just the jar, actually echoes correctly |
| [`.dockerignore`](https://github.com/azusachino/flos/blob/main/.dockerignore) | Keeps `vendor/`, `target/`, and `node_modules/` out of the build context |

## A build stage the final image never ships

```dockerfile
FROM docker.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY modules ./modules
RUN mvn -pl modules/netty/event-loop-lab -am -DskipTests package

FROM docker.io/library/eclipse-temurin:17-jre
...
COPY --from=build --chown=flos:flos /workspace/modules/netty/event-loop-lab/target/event-loop-lab.jar app.jar
```

The build stage needs the _whole_ reactor's `pom.xml` files, not just the one module being built: Maven reads the root `pom.xml`'s `<modules>` list before it can restrict the actual build to `event-loop-lab` with `-pl`/`-am`, so every declared module — including the unrelated `modules/flink` tree — has to exist in the build context even though only its `pom.xml` files get read. Only the resulting jar crosses into the second `FROM`. The Maven distribution, the JDK compiler, every dependency's full jar — none of it reaches the image that actually runs.

## A base image tag is not automatically multi-arch

The Dockerfile originally targeted `eclipse-temurin:17-jre-alpine` for a small runtime image. It built and ran fine — until run on an Apple Silicon (arm64) host, where the pull failed:

```text
no image found in image index for architecture "arm64", variant "v8", OS "linux"
```

`podman manifest inspect docker.io/library/eclipse-temurin:17-jre-alpine` confirms why: that specific tag only publishes an `amd64` variant, despite Eclipse Temurin publishing arm64 images under other tags. The fix here is `amazoncorretto:17-alpine` instead — verified multi-arch (`amd64` and `arm64`) with `podman manifest inspect`, and the same JDK vendor `mise.toml` already pins for local development (`java = "corretto-17"`), so the container runs the identical build a contributor tests against on their own machine rather than a different vendor's equivalent. Corretto does not publish a separate JRE-only tag the way Temurin does — every Corretto image ships the full JDK, compiler included — but Alpine keeps it smaller than a Debian-based full JDK would be, and the health check can still use Alpine's built-in BusyBox `nc`:

```dockerfile
HEALTHCHECK --interval=5s --timeout=3s --retries=12 \
    CMD nc -z localhost 9000 || exit 1
```

A base image tag being labeled "Alpine" or "JRE" says nothing about which architectures it actually publishes; `podman manifest inspect` is the only way to know before a teammate's arm64 laptop finds out for you.

## Running as a real user, not root

```dockerfile
RUN groupadd --system flos && useradd --system --gid flos flos
USER flos
```

The default container user is root unless a Dockerfile says otherwise. A process compromised while running as root inside a container is one container-escape bug away from being a compromise of the host; running as an unprivileged, purpose-created user is a floor, not a complete defense, but it costs two lines.

## Proving the container, not just the jar

```python
def verify_echo() -> None:
    with socket.create_connection((HOST, PORT), timeout=5) as sock:
        sock.sendall(b"hello")
        response = sock.recv(5)
    if response != b"HELLO":
        raise RuntimeError(f"expected b'HELLO', got {response!r}")
```

`netty_deployment_smoke.py` connects to `localhost:9000` the same way the [event loop lab's](../../concepts/netty/event-loop.md) manual `nc` instructions do — except now the process on the other end is running inside a container, under its own user, behind Podman's port mapping. A green `make check` proves the Java code is correct; it says nothing about whether the Dockerfile, the exposed port, the health check, or the container user are wired correctly. Only actually building the image, starting it, and connecting to it proves that.

## What's proven, what's not

| Check | Proves |
| --- | --- |
| `podman compose build` | The multi-stage Dockerfile compiles the module and produces a runnable jar |
| Container health check | The process inside the container is listening and accepting connections |
| `netty_deployment_smoke.py` | The containerized server's actual echo behavior is correct, end to end |

What's not proven: multi-container orchestration (this is one service, not several coordinating through a network), resource limits under load, image vulnerability scanning, or a real registry push/pull cycle. Those are the next layer of DevOps maturity, not covered here.

## Run it

```sh
make netty-up
make netty-smoke
make netty-down
```

## Exercises

1. Run `podman manifest inspect` against `eclipse-temurin:21-jre-alpine` and `amazoncorretto:21-alpine` and check whether the same amd64-only gap exists at the next Java version before assuming it's fixed.
2. Add a `.dockerignore` violation on purpose — remove `vendor` from `.dockerignore` — and compare the build context size before and after with `podman build --log-level=debug`.
3. The health check currently only proves the port accepts a TCP connection, not that the echo logic itself works. Change it to something that would fail if `EchoServerHandler`'s logic broke but the port still accepted connections — is that possible with a shell-only `HEALTHCHECK`, or does it need to shell out to something smarter?
4. Containerize a second lab (`framing-lab` or `backpressure-lab`) by copying this pattern. What, if anything, needs to change beyond the module path and port number?

## What's next

This page proves one service can be built, run, and health-checked as a container. It does not cover multi-service orchestration, which the [Flink observability stack](../flink/observability-and-incidents.md) already demonstrates for a different reason — Prometheus and Grafana coordinating with a running job, not one service in isolation.

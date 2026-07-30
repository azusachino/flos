# flos

Flos is a topic-focused, executable learning project for distributed systems concepts. Each concept combines explanatory documentation, compilable source code, deterministic tests, and a containerized experiment.

Apache Flink is the first technology; Netty is the second.

The Flink runtime baseline matches the production compatibility line: Apache Flink 2.2.1, Java 17, and Flink Kubernetes Operator 1.15.0. Podman Compose provides the local learning runtime; the Kubernetes operator remains a distinct deployment target. The Netty labs (event loop, framing, backpressure, connection lifecycle) run as standalone servers with no external services.

## Quick start

```sh
mise install
make setup
make check
make docs
```

Run the Flink lab with Podman:

```sh
make flink-package
make flink-up
make flink-smoke
make flink-down
```

The Flink dashboard is available at <http://localhost:8081> while the environment is running.

Run a Netty lab, no containers required:

```sh
make netty-event-loop
```

## Repository map

- `modules/` contains the Maven technology modules and executable labs.
- `docs/` contains the Astro Starlight tutorial.
- `environments/` contains Podman Compose-compatible environments.
- `vendor/` contains shallow, ignored-worktree submodules pinned to the matching upstream releases.
- `scripts/` contains uv-managed Python automation.
- `Makefile` is the public interface for project commands; Bun builds the tutorial and uv runs Python automation.

See [the setup guide](docs/setup.md), [the architecture](docs/architecture.md), and [the content conventions](docs/src/content/docs/contributing/content-conventions.md).

Initialize the optional upstream source references with:

```sh
git submodule update --init --depth 1
```

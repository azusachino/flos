# AGENTS.md

## Project overview

Flos is a topic-focused, executable learning project for distributed systems. Every mature topic combines public documentation, compilable source, deterministic tests, and a runtime experiment where appropriate.

## Stack and architecture

- Java 17 and a multi-module Maven reactor for examples.
- Apache Flink 2.2.1, the first technology, aligned with Flink Kubernetes Operator 1.15.0.
- Netty 4.2.1.Final, the second technology, taught as four standalone connection-lifecycle labs under `modules/netty`.
- Astro Starlight for the public tutorial.
- Bun for JavaScript dependency management and documentation commands.
- uv-managed Python for automation and smoke checks.
- Podman Compose-compatible runtime environments.
- mise for tool provisioning and Make for all contributor commands.

## Commands

```sh
make setup
make fmt
make check
make validate
make docs
make flink-up
make flink-smoke
make flink-down
make netty-event-loop
make netty-framing
make netty-backpressure
make netty-lifecycle
```

## Coding conventions

- Match existing Java package and test structure.
- Use lowercase kebab-case for documentation paths and tags.
- Keep topic metadata consistent with the validated Starlight schema.
- Keep automation portable and avoid shell-specific behavior.
- Do not duplicate source classification outside `tags`.

## Testing conventions

- Test operator/handler semantics deterministically: fixed values and timestamps, or a virtual clock (for example `EmbeddedChannel.freezeTime()` / `advanceTimeBy()`).
- Use runtime smoke tests only for boundaries that require a real cluster or a real socket.
- A green unit test does not prove job submission, scheduling, connection acceptance, or completion.

## Quality standards

Run `make validate` before committing. Unit tests prove operator/handler semantics; runtime claims require the Podman Compose smoke route (Flink) or a real-socket test (Netty).

## Do

- Use `make <target>` for project operations.
- Provision tools with `mise install`.
- Organize learning material by concept and topic.
- Stage files explicitly by name.
- Preserve the distinction between unit, integration, and runtime evidence.

## Do not

- Push directly to `main`, `master`, or `develop`.
- Use `git add -A` or `git add .`.
- Commit generated `target`, `node_modules`, or `docs/dist` output.
- Introduce date-based journals or calendar tags.
- Commit secrets or credentials.

## Key files

- `pom.xml`
- `Makefile`
- `mise.toml`
- `docs/src/content.config.ts`
- `docs/astro.config.mjs`
- `environments/flink/compose.yaml`

# CLAUDE.md

## Project overview

Flos is a topic-focused, executable learning project for distributed systems. Every mature topic combines public documentation, compilable source, deterministic tests, and a runtime experiment where appropriate.

## Stack and architecture

- Java 17 and a multi-module Maven reactor for examples.
- Apache Flink 2.2.1 for the first technology, aligned with Flink Kubernetes Operator 1.15.0.
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
```

## Coding conventions

- Match existing Java package and test structure.
- Use lowercase kebab-case for documentation paths and tags.
- Keep topic metadata consistent with the validated Starlight schema.
- Keep automation portable and avoid shell-specific behavior.
- Do not duplicate source classification outside `tags`.

## Quality standards

Run `make validate` before committing. Unit tests prove operator semantics; runtime claims require the Podman Compose smoke route.

## Rules

- See `.claude/rules/core.md`.
- See `.claude/rules/testing.md` when editing tests.

## Key files

- `pom.xml`
- `Makefile`
- `mise.toml`
- `docs/src/content.config.ts`
- `environments/flink/compose.yaml`

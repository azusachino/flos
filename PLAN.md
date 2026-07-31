# Project plan

## Current phase

Build the Apache Flink learning path as complete vertical topics.

## Milestones

1. Establish the Maven, uv, mise, Podman Compose, and MkDocs Material foundation.
2. Complete fundamental operator semantics and exercises.
3. Add event time, watermarks, windows, and late-event behavior.
4. Add keyed state, checkpointing, recovery, and savepoints.
5. Add Kubernetes deployment exercises for Flink Kubernetes Operator 1.15.0.
6. Introduce Kafka under `modules/` before integrating it with Flink.
7. Turn the operations handbook's readiness contract into automated deployment, failure-recovery, restore, and reconciliation exercises.

Each milestone requires explanatory content, compilable source, deterministic tests, and a runtime experiment where the concept crosses a process boundary.

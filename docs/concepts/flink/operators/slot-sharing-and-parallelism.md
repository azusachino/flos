---
title: Slot Sharing Groups and Parallelism
description: Prove how default slot sharing packs an operator chain into one slot per parallel instance, and how an isolated slot sharing group changes the concurrent slot requirement.
created: 2026-08-06 00:00
modified: 2026-08-06 00:00
type: concept
status: active
maturity: developing
tags:
    - apache-flink
    - parallelism
    - slot-sharing
    - scheduling
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/deployment/config/#configuring-taskmanager-processing-slots
---

# Slot Sharing Groups and Parallelism

[Operators](index.md) says Flink "decides how that dataflow becomes tasks distributed across available slots" without saying how many slots a job actually needs. That number depends on two separate things: how much parallelism each operator declares, and which slot sharing group each operator belongs to. Neither one alone answers the question.

```mermaid
flowchart LR
    subgraph default["default slot sharing group"]
        Source0["source #0"] --> Map0["map #0"]
        Source1["source #1"] --> Map1["map #1"]
    end
    subgraph isolated["isolated-sink slot sharing group"]
        Sink0["sink #0"]
        Sink1["sink #1"]
    end
    Map0 --> Sink0
    Map1 --> Sink1
```

Two subtask indices, two slot sharing groups: the default group needs 2 slots, the isolated group needs 2 more, for 4 total — even though every operator is declared at the same parallelism.

## Source code map

The complete source is under `modules/flink/slot-sharing-lab`:

| File | What to learn from it |
| --- | --- |
| [`SlotSharingLabJob.java`](https://github.com/azusachino/flos/blob/main/modules/flink/slot-sharing-lab/src/main/java/io/github/azusachino/flos/flink/slotsharing/SlotSharingLabJob.java) | Builds a source → map → sink chain and optionally isolates the sink into its own slot sharing group |
| [`SubtaskIndexSource.java`](https://github.com/azusachino/flos/blob/main/modules/flink/slot-sharing-lab/src/main/java/io/github/azusachino/flos/flink/slotsharing/SubtaskIndexSource.java) | Blocks forever once started, so a subtask's slot stays occupied for as long as the test needs to observe concurrent contention |
| [`SlotSharingLabTest.java`](https://github.com/azusachino/flos/blob/main/modules/flink/slot-sharing-lab/src/test/java/io/github/azusachino/flos/flink/slotsharing/SlotSharingLabTest.java) | Proves the default-sharing slot count, the isolated-group slot count, and the resulting scheduling failure |

## Three parallelism knobs, not one

`job.parallelism` in a Flink UI or metric usually means one of three distinct things in code:

```java
environment.setParallelism(2);              // job-wide default, every operator inherits this
source.setParallelism(1);                   // per-operator override, wins over the default
environment.setMaxParallelism(16);           // key-group count, fixed at job creation
```

The [savepoint upgrade lab](../state/savepoint-upgrade.md) exercises exactly this distinction: runtime parallelism moves from 1 to 2 across a restore, while max parallelism stays fixed at 16 because it is baked into the savepoint's key-group layout. Runtime parallelism is negotiable at every restart; max parallelism is not.

## A slot is a TaskManager resource, not a CPU core

A task slot is how many parallel instances one TaskManager can host, set by `taskmanager.numberOfTaskSlots`. It divides managed memory, not CPU — Flink slots provide no CPU isolation.

## Default slot sharing packs a whole chain into one slot

Every operator that never calls `.slotSharingGroup(...)` lands in the same implicit `"default"` group. One physical slot then holds one subtask index's worth of the *entire* group — not one slot per operator:

```java
var source = env.addSource(new SubtaskIndexSource()).setParallelism(2).name("subtask-index-source");
var doubled = source.map(value -> value * 2).setParallelism(2).name("double");
var sink = doubled.print().setParallelism(2).name("sink");
// no .slotSharingGroup() call anywhere: source, map, and sink share slots 0 and 1
```

With source, map, and sink all in the default group at parallelism 2, the job needs exactly 2 slots — the maximum parallelism in that group — regardless of how many operators are chained into it.

## Pulling an operator into its own group changes the arithmetic

```java
sink.slotSharingGroup("isolated-sink");
```

Now there are two groups: `default` (source + map, needing 2 slots) and `isolated-sink` (needing 2 more). The job needs 4 slots total. `SlotSharingLabTest.isolatingTheSinkExhaustsASlotPoolThatFitsDefaultSharing` runs that exact topology against a real fixed-size local cluster with only 2 slots and gets a real `NoResourceAvailableException` — not a mock, the same failure Flink's own `MiniClusterITCase` uses to test the scheduler.

## The gotcha: a pipelined region is scheduled together, so the source waits too

The naive expectation is that the default group's source and map start running immediately (they only need the 2 available slots) while the isolated sink fails separately. That is not what happens. Flink's default scheduler deploys an entire pipelined region as a unit: because the sink is connected to the map by a pipelined (not blocking) edge, the source **never starts running at all** when the sink's group can't get slots — it is held back by a downstream vertex it has no direct resource conflict with. `SlotSharingLabTest` proves this negatively: after the job fails, `SubtaskIndexSource.awaitAllRunning(0, SECONDS)` is still false. A stuck job with plenty of slots for its source can still be entirely blocked by an unrelated slot sharing group further downstream.

## The gotcha: a fast, bounded lab can make slot exhaustion invisible

The first version of this lab used a source that emitted one record per subtask and finished immediately. It never failed, even with far too few slots, because the default-group subtasks finished and released their slots before the isolated group ever needed them — the scheduler happily reused freed slots across time instead of needing them all at once. Proving a *concurrent* resource requirement requires a source that actually stays alive long enough to hold its slot while the rest of the job tries to schedule; `SubtaskIndexSource` blocks until cancelled for exactly this reason. A green test with a source that finishes in milliseconds does not prove a job's real, sustained slot requirement.

## What's proven, what's not

| Test | Proves |
| --- | --- |
| `defaultSharingFitsAThreeOperatorChainIntoParallelismManySlots` | Default sharing needs only the group's max parallelism in slots, not one slot per operator |
| `isolatingTheSinkExhaustsASlotPoolThatFitsDefaultSharing` | An isolated group adds its own slot requirement on top of the default group's, and Flink's real scheduler enforces it |
| `isolatingTheSinkSucceedsWithEnoughSlots` | The same isolated topology runs once enough slots exist, proving `slotSharingGroup()` isolates rather than merely breaks scheduling |

All three run a real local `MiniCluster` with a genuinely fixed slot count — no mocking of Flink's scheduler. What this lab does not prove: production placement across multiple real TaskManagers, resource-based (CPU/memory) `SlotSharingGroup` requirements instead of plain slot counts, or how the Flink Web UI's subtask/slot visualizations map back to these same groups.

## Run the lab

```sh
make flink-slot-sharing
```

The bundled `main()` runs the default-sharing case, waits for both subtasks to report running, prints a confirmation, then cancels the job.

## Exercises

1. Add a third operator between `map` and `sink` in its own third slot sharing group, and predict the new total slot requirement before running the test.
2. Change `isolatingTheSinkSucceedsWithEnoughSlots` to use exactly 3 slots instead of 4, and confirm it now fails the same way the 2-slot case does — the isolated group's requirement is not negotiable down to "however many are left over."
3. Remove the `.slotSharingGroup("isolated-sink")` call from `isolatingTheSinkExhaustsASlotPoolThatFitsDefaultSharing`'s topology and confirm the same 2-slot cluster now succeeds — proving the failure was about the grouping, not the parallelism.

## What's next

This lab proves slot *arithmetic* and scheduling in isolation. Real capacity planning also has to account for the [Kafka partition ceiling on source parallelism](../connectors/billing-pipeline.md#sixteen-partitions-versus-flink-parallelism) and the [key-group ceiling that max parallelism puts on rescaling](../state/savepoint-upgrade.md) — slots, source partitions, and max parallelism are three independent limits that all have to be sized together.

---
title: Flink Failure and Recovery Drill
description: Prove checkpoint restoration, source progress, sink idempotency, and business reconciliation after a TaskManager failure.
created: 2026-08-10 19:34
modified: 2026-08-10 19:34
type: documentation
status: maintained
maturity: developing
tags:
    - apache-flink
    - checkpointing
    - recovery
    - reconciliation
    - operations
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/checkpoints/
---

# Flink Failure and Recovery Drill

This tutorial answers a narrow operational question:

> After one TaskManager disappears, does the billing job restore from a completed checkpoint and preserve both its source progress and its external result?

The answer requires more than seeing the job become `RUNNING` again. A recovery claim needs evidence from the Flink checkpoint, the source, the sink, and the business reconciliation invariant.

## Know which drill you are running

Flos contains two complementary exercises:

| Exercise | Command | Evidence boundary |
| --- | --- | --- |
| Embedded checkpoint lab | `make flink-recovery` | A real MiniCluster restores an injected source cursor and an open window in memory |
| External billing acceptance | `make flink-billing-smoke` | Real Kafka, Flink, and MySQL execute the billing path, including correction and reconciliation |

The embedded lab does not prove process loss, durable checkpoint storage, Kafka offset restoration, or external sink behavior. The billing smoke does not currently fail a TaskManager or verify checkpoint restoration. Do not combine their claims into an end-to-end recovery claim.

## The prerequisite the local Compose file does not provide

The billing job enables checkpointing every five seconds and assigns stable operator UIDs. The current Compose environment does not configure a durable, shared checkpoint directory for the JobManager and TaskManager. A local filesystem inside one container is not evidence that a checkpoint survives the failure boundary being tested.

Before running this drill, the target deployment must provide:

- checkpoint storage durable across TaskManager replacement
- the same checkpoint path visible to the JobManager and every TaskManager
- credentials, filesystem plugins, permissions, and retention appropriate to the target
- a restart strategy that permits the job to recover
- a sink contract that is safe under replay

The current `make flink-up` path is therefore a prerequisite environment, not by itself a pass for this drill. Configure the target environment first, then record the exact configuration revision in the evidence.

## Establish the baseline

Run the repository's existing checks before introducing a failure:

```sh
make check
make flink-up
make flink-billing-smoke
```

The billing smoke creates its own 16-partition topic and consumer group, verifies the initial and corrected report, routes one event beyond allowed lateness, checks the source-to-sink ledger, and cancels its job. It is a useful baseline; it is not the recovery drill.

For the drill, use a dedicated environment and record:

```text
application revision
Flink image and configuration revision
checkpoint storage URI
Kafka topic and consumer group
job ID
drill start time in UTC
```

Do not run this procedure against a shared topic or a production job without an approved change and rollback plan.

## Execute the failure drill

The commands below describe the boundary. Adapt the deployment command to the target environment; the important part is the order and the evidence, not a particular shell wrapper.

### 1. Start one long-lived billing job

Build and start the existing environment, then submit `pipeline-lab.jar` with a unique topic and consumer group. The job is the existing `BillingPipelineJob`; this tutorial does not introduce a second application.

```sh
make flink-up

TOPIC="billing-recovery-<run-id>"
GROUP="flos-billing-recovery-<run-id>"

podman compose -f environments/flink/compose.yaml exec -T jobmanager \
  flink run -d \
  -c io.github.azusachino.flos.flink.pipeline.BillingPipelineJob \
  -Dexecution.checkpointing.dir=<durable-checkpoint-uri> \
  /opt/flink/usrlib/pipeline-lab.jar
```

Use the deployment's supported configuration mechanism for the checkpoint URI if command-line configuration is not accepted. Do not replace `<durable-checkpoint-uri>` with a placeholder and call the result verified.

Record the returned job ID and confirm the job is `RUNNING` with the expected parallelism and stable UIDs.

### 2. Publish a baseline and wait for a completed checkpoint

Publish the existing initial fixture, then wait for at least one completed checkpoint after the fixture is processed. Record the checkpoint ID, external path, completion time, state size, and the source-position metadata exposed by the connector or deployment.

```sh
podman compose -f environments/flink/compose.yaml exec -T jobmanager \
  java -cp /opt/flink/usrlib/pipeline-lab.jar \
  io.github.azusachino.flos.flink.pipeline.BillingFixtureProducer \
  kafka:9092 "$TOPIC" initial
```

The checkpoint must be completed, not merely triggered or acknowledged as in progress. If the source connector does not expose a trustworthy source position in the checkpoint evidence, record that gap and do not claim source restoration.

### 3. Fail only the TaskManager

Capture the failure timestamp, then terminate one TaskManager abruptly. Keep the JobManager, Kafka, MySQL, and checkpoint storage available.

```sh
date -u +%Y-%m-%dT%H:%M:%SZ
podman compose -f environments/flink/compose.yaml kill taskmanager
```

Restore the TaskManager capacity using the target platform's normal operation. For this single-node Compose exercise that is:

```sh
podman compose -f environments/flink/compose.yaml up -d taskmanager
```

This is a failure-injection boundary, not a graceful deployment. Preserve the JobManager logs and the Flink job/checkpoint history before cleaning up.

### 4. Prove restoration, not just liveness

The following must all be true:

- the same job reaches `RUNNING` after a restart attempt
- the restart reason identifies the TaskManager loss
- the job reports a completed checkpoint restored after the failure
- the restored checkpoint predates the failure and is the recorded checkpoint
- the source resumes from the checkpointed position rather than an untracked guess
- the open event-time window continues with the pre-failure accumulator
- new records are accepted after recovery

`RUNNING` alone proves only liveness. A restarted job with an empty state, an unverified source cursor, or a new consumer group is not a pass.

### 5. Prove the external result

Publish the existing correction and watermark-advance fixtures after the job recovers. Then query the sink and the audit tables using the same logical window as the billing smoke.

The current sink uses MySQL upserts keyed by report identity and by `(source_partition, sequence_number)` for event audit rows. That makes replayed writes converge to one stored logical row. It is application-level idempotency, not an exactly-once guarantee for the whole pipeline.

The required invariant is:

```text
audit fee - too-late fee - report fee = 0.00
audit count - too-late count - report count = 0
```

Also verify:

- no duplicate logical audit keys exist
- the corrected report has the expected total and event count
- the too-late event is present in the explicit too-late table
- the source position advanced after the post-recovery fixture
- the report key did not produce an append-only duplicate

The exact expected values depend on the fixture. For the repository's existing fixture they are `153.00 / 18` for the corrected report and `9.00 / 1` for the too-late path, with zero reconciliation deltas.

## Evidence record

Persist one record per drill. Keep it beside the run output, not as a claim in prose alone:

```yaml
recoveryDrill:
    revision: <application-revision>
    configurationRevision: <deployment-configuration-revision>
    jobId: <flink-job-id>
    topic: <kafka-topic>
    consumerGroup: <kafka-consumer-group>
    checkpoint:
        id: <completed-checkpoint-id>
        path: <durable-checkpoint-path>
        completedAt: <utc-timestamp>
        sourcePosition: <connector-evidence>
    failure:
        component: taskmanager
        injectedAt: <utc-timestamp>
        restoredAt: <utc-timestamp>
    verification:
        jobRunning: true
        checkpointRestored: true
        sourceProgressVerified: true
        duplicateLogicalKeys: 0
        reportFee: <decimal>
        reportCount: <integer>
        tooLateFee: <decimal>
        tooLateCount: <integer>
        reconciliationFeeDelta: 0.00
        reconciliationCountDelta: 0
```

Treat missing fields as `unknown`, not `true`.

## Pass and fail criteria

Pass only when the completed checkpoint, failure boundary, restoration, source position, sink idempotency, and both reconciliation deltas are evidenced in the same run.

Fail the drill when:

- no completed checkpoint exists before failure
- checkpoint storage was local to the lost process
- the job merely restarted from the beginning
- source progress is inferred only from a `RUNNING` status
- report totals look correct but duplicate logical keys were not checked
- the money delta is zero but the count delta is non-zero, or vice versa
- the job recovered but the source, sink, or checkpoint evidence belongs to another run

## What this still does not prove

One successful TaskManager drill does not prove:

- JobManager high availability or whole-cluster recovery
- checkpoint storage durability under a storage outage
- exactly-once effects across Kafka, Flink, and MySQL
- recovery time under production state size and traffic
- savepoint compatibility across a code or Flink version change
- correctness under source partition loss, poison records, or sink schema failure

Use the [Production Readiness](production-readiness.md), [Observability and Incident Response](observability-and-incidents.md), and [Recovery, Upgrade, and Rescaling](recovery-upgrade-rescaling.md) pages to extend the evidence without promoting this tutorial's result beyond its tested boundary.

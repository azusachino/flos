---
title: Flink Job Lifecycle SOP
description: Build, deploy, verify, maintain, change, and retire a stateful Flink job safely.
created: 2026-07-30 19:29
modified: 2026-07-30 19:29
type: documentation
status: maintained
maturity: developing
tags:
    - apache-flink
    - deployment
    - runbook
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/production_ready/
---

# Flink Job Lifecycle SOP

This SOP is the normal path for a stateful job. Incident response may shorten the path to protect data or restore service, but emergency actions must still preserve evidence and reconcile external effects.

## Change classification

Classify the change before deployment:

| Class | Examples | State action |
| --- | --- | --- |
| Stateless | Logging, metric labels, stateless validation | Normal deployment after topology/UID review |
| State-compatible | Function logic change with unchanged keyed state serializers and UIDs | Restore from checkpoint/savepoint after test |
| State migration | Key, accumulator, serializer, window, or topology changes | Designed migration or new job; do not guess |
| Runtime upgrade | Flink version, connector version, Java runtime | Compatibility review and savepoint restore drill |
| Platform upgrade | Kubernetes Operator, CRD, Helm values | Separate operator procedure; preserve running jobs |

## 1. Prepare the change

Record:

```text
artifact digest
git commit
Flink and connector versions
configuration revision
source and sink schema revisions
operator UID changes
state serializer changes
parallelism/maxParallelism changes
watermark/window changes
expected output change
rollback artifact
```

Run repository gates:

```sh
make check
make validate
```

These prove local semantics, packaging, and documentation. Add connector, restore, and environment-specific tests before production.

## 2. Review topology and state

Compare old and new job graphs:

- operator added, removed, or reordered
- UID changed
- keyed state type changed
- key selector changed
- window assigner, trigger, or lateness changed
- parallelism or maximum parallelism changed
- source start policy changed
- sink delivery or transaction behavior changed

Create a state-compatibility decision:

```yaml
stateCompatibility:
    classification: compatible
    reviewedOperators:
        - uid: order-source-v1
          change: none
        - uid: five-minute-user-fee-v1
          change: logic-only
          serializer: unchanged
        - uid: bill-fee-report-sink-v1
          change: retry-policy
    restoreEvidence: test-savepoint-2026-07-30
```

## 3. Establish the rollback point

For a healthy stateful job, use a savepoint when the deployment method requires controlled state transfer:

```sh
flink stop \
  --savepointPath s3://example-flink/savepoints \
  --type canonical \
  "$JOB_ID"
```

Record:

```text
savepoint path
old artifact and image digest
old configuration
source positions
time processing stopped
last completed business window
```

A savepoint does not reverse writes already emitted after an earlier snapshot. Prefer stop-with-savepoint for one active processing timeline and define sink reconciliation before using a non-stopping snapshot.

## 4. Deploy

For a Kubernetes Operator application deployment, a production-oriented shape is:

```yaml
apiVersion: flink.apache.org/v1beta1
kind: FlinkDeployment
metadata:
    name: user-fee-report
spec:
    image: registry.example/flos/user-fee-report@sha256:REPLACE
    flinkVersion: v2_2
    serviceAccount: flink-user-fee-report
    flinkConfiguration:
        state.backend.type: rocksdb
        execution.checkpointing.dir: s3://example-flink/checkpoints
        execution.checkpointing.savepoint-dir: s3://example-flink/savepoints
        high-availability.type: kubernetes
        high-availability.storageDir: s3://example-flink/ha
    job:
        jarURI: local:///opt/flink/usrlib/pipeline-lab.jar
        parallelism: 16
        upgradeMode: savepoint
        state: running
```

This is illustrative. The repository's current manifest creates a learning session cluster and does not implement this application deployment.

Before applying:

```sh
kubectl diff -f user-fee-report.yaml
kubectl apply --server-side -f user-fee-report.yaml
kubectl get flinkdeployment user-fee-report -w
```

Never paste credentials into the manifest or command history.

## 5. Verify runtime health

Runtime acceptance:

- deployment reconciles without repeated errors
- job reaches `RUNNING`
- expected operators, UIDs, parallelism, and task slots appear
- TaskManagers remain registered
- checkpoints complete
- restart count remains stable
- source offsets and records advance
- sink records advance
- input and output watermarks advance
- late records remain within policy
- backpressure is understood

Example REST reads:

```sh
xh get "$FLINK_REST_URL/jobs/overview"
xh get "$FLINK_REST_URL/jobs/$JOB_ID/checkpoints"
xh get "$FLINK_REST_URL/jobs/$JOB_ID/vertices"
```

Do not log bearer tokens or embed them in committed scripts.

## 6. Verify business correctness

Choose a traceable canary event:

```json
{
    "orderId": "ops-canary-20260730-001",
    "userId": "ops-canary",
    "sequence": 1,
    "fee": 12.5,
    "timestampTs": 1785405600000
}
```

Verify independently:

1. The source accepted the event once.
2. The expected event-time window is calculated.
3. The report appears under `(userId, windowStart)`.
4. Fee, count, and report version are correct.
5. No unexpected late or reconciliation record appears.
6. Replaying the same logical report does not create a duplicate row.

Remove or clearly label canary data according to the data-retention policy.

## 7. Declare stable

Observe for a defined stability window. Compare against the previous revision:

```text
throughput
watermark lag
late records
busy/idle/backpressure distribution
state size
checkpoint size and duration
restart count
sink latency/errors
business report delay
```

Record:

```yaml
deployment:
    revision: sha256:REPLACE
    startedAt: 2026-07-30T10:00:00Z
    declaredStableAt: 2026-07-30T10:30:00Z
    savepoint: s3://example-flink/savepoints/savepoint-REPLACE
    verification:
        checkpointsCompleted: 30
        canaryReportVerified: true
        watermarkLagP99: 42s
        lateRecordsDropped: 0
```

## 8. Routine maintenance

Daily:

- job and deployment status
- checkpoint age and failures
- watermark/report delay
- late/reconciliation volume
- restart loops and sink errors

Weekly:

- per-subtask skew
- state and checkpoint growth
- Kafka partition/consumer lag
- resource saturation and cost
- unresolved alerts and runbook accuracy

Per release:

- dependency and CVE review
- connector compatibility
- savepoint restore test for stateful changes
- rollback artifact availability
- dashboard and alert changes

Quarterly or after material changes:

- TaskManager failure drill
- JobManager/HA recovery drill
- savepoint restore drill
- sink replay/idempotency test
- RTO measurement

## 9. Roll back

Rollback triggers:

- repeated reconciliation or restart failure
- checkpoint failure beyond the agreed limit
- watermark/report delay outside SLO
- incorrect or duplicate business output
- state growth inconsistent with expectations
- unacceptable resource regression

Procedure:

1. Stop further automated changes.
2. Preserve logs, metrics, deployment status, checkpoint history, and output evidence.
3. Decide whether the current job can stop with a valid savepoint.
4. Restore the previous artifact and compatible configuration.
5. Restore from the agreed checkpoint/savepoint.
6. Verify runtime and business output.
7. Reconcile writes produced by the failed revision.
8. Record the incident and follow-up gates.

Do not use `--allowNonRestoredState` merely to make deployment succeed. That flag authorizes state loss and requires an explicit data-correctness decision.

## 10. Retire

Before retirement:

- identify downstream consumers and owners
- define the final accepted source position
- allow or force final windows according to business policy
- reconcile late/correction streams
- stop with a final savepoint if retention is required
- record artifact, configuration, offsets, snapshot, and final outputs
- disable producers/consumers in the agreed order
- remove alerts only after the job is intentionally stopped
- apply checkpoint/savepoint retention and deletion policy
- remove credentials, RBAC, manifests, and storage only after recovery obligations expire

Retirement is complete when both compute and data ownership are resolved.

## Official references

- [Production readiness checklist](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/production_ready/)
- [Upgrading applications and Flink](https://nightlies.apache.org/flink/flink-docs-master/docs/ops/upgrading/)
- [Savepoints](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/ops/state/savepoints/)
- [Operator upgrade process](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-release-1.15/docs/operations/upgrade/)

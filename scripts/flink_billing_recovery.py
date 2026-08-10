#!/usr/bin/env python3

from __future__ import annotations

import contextlib
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import UTC, datetime
from pathlib import Path

from flink_billing_smoke import (
    EXPECTED_PARTITIONS,
    FLINK_JOBS_URL,
    JOB_NAME,
    PROJECT_ROOT,
    TIMEOUT_SECONDS,
    cancel_job,
    compose,
    delete_topic,
    prepare_database,
    publish_fixture,
    query_reconciliation,
    query_report_summary,
    request_json,
    wait_for_flink,
    wait_for_partition_assignments,
    wait_for_query,
)

CHECKPOINT_ROOT = PROJECT_ROOT / "artifacts" / "flink-billing-recovery"
FLINK_REST_URL = "http://localhost:8081"
JOB_JAR = "/opt/flink/usrlib/pipeline-lab.jar"
JOB_CLASS = "io.github.azusachino.flos.flink.pipeline.BillingPipelineJob"
FIXTURE_CLASS = "io.github.azusachino.flos.flink.pipeline.BillingFixtureProducer"


def utc_now() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def persist(path: Path, manifest: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")


def git_revision() -> str:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=PROJECT_ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def active_job_ids() -> set[str]:
    jobs = request_json(FLINK_JOBS_URL).get("jobs", [])
    return {
        str(job["jid"])
        for job in jobs
        if job.get("name") == JOB_NAME
        and job.get("state") not in {"CANCELED", "FAILED", "FINISHED"}
    }


def wait_for_new_job(previous_ids: set[str]) -> str:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        for job_id in active_job_ids() - previous_ids:
            return job_id
        time.sleep(1)
    raise RuntimeError("submitted billing job was not visible through Flink REST")


def checkpoint_snapshot(job_id: str) -> dict[str, object]:
    return request_json(f"{FLINK_REST_URL}/jobs/{job_id}/checkpoints")


def latest_checkpoint(payload: dict[str, object], name: str) -> dict[str, object] | None:
    latest = payload.get("latest", {})
    if not isinstance(latest, dict):
        return None
    checkpoint = latest.get(name)
    return checkpoint if isinstance(checkpoint, dict) else None


def wait_for_completed_checkpoint(job_id: str) -> tuple[dict[str, object], dict[str, object]]:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last_payload: dict[str, object] = {}
    while time.monotonic() < deadline:
        last_payload = checkpoint_snapshot(job_id)
        completed = latest_checkpoint(last_payload, "completed")
        if completed and completed.get("id") is not None and completed.get("external_path"):
            return completed, last_payload
        time.sleep(2)
    raise RuntimeError(f"no completed external checkpoint appeared: {last_payload}")


def wait_for_restored_checkpoint(
    job_id: str, completed: dict[str, object]
) -> tuple[dict[str, object], list[str], dict[str, object]]:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    states: list[str] = []
    last_payload: dict[str, object] = {}
    expected_id = str(completed["id"])
    expected_path = completed.get("external_path")

    while time.monotonic() < deadline:
        job = request_json(f"{FLINK_REST_URL}/jobs/{job_id}")
        state = str(job.get("state", "UNKNOWN"))
        if not states or states[-1] != state:
            states.append(state)
        last_payload = checkpoint_snapshot(job_id)
        restored = latest_checkpoint(last_payload, "restored")
        if restored and state == "RUNNING":
            restored_id = str(restored.get("id", ""))
            restored_path = restored.get("external_path")
            if restored_id == expected_id or restored_path == expected_path:
                return restored, states, last_payload
        time.sleep(2)

    raise RuntimeError(
        f"job did not report restoration of checkpoint {expected_id}; "
        f"states={states}, checkpoints={last_payload}"
    )


def consumer_offsets(group: str) -> dict[str, dict[str, int | None]]:
    output = compose(
        "exec",
        "-T",
        "kafka",
        "/opt/kafka/bin/kafka-consumer-groups.sh",
        "--bootstrap-server",
        "kafka:9092",
        "--describe",
        "--group",
        group,
        capture_output=True,
    ).stdout
    offsets: dict[str, dict[str, int | None]] = {}
    for line in output.splitlines():
        fields = line.split()
        if len(fields) < 5 or not fields[2].isdigit():
            continue
        current = int(fields[3]) if fields[3].isdigit() else None
        log_end = int(fields[4]) if fields[4].isdigit() else None
        offsets[fields[2]] = {"current": current, "logEnd": log_end}
    return offsets


def wait_for_committed_offsets(group: str) -> dict[str, dict[str, int | None]]:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last_offsets: dict[str, dict[str, int | None]] = {}
    while time.monotonic() < deadline:
        last_offsets = consumer_offsets(group)
        if set(last_offsets) == {str(partition) for partition in EXPECTED_PARTITIONS} and all(
            value["current"] is not None for value in last_offsets.values()
        ):
            return last_offsets
        time.sleep(2)
    raise RuntimeError(
        f"consumer group offsets were not committed for all partitions: {last_offsets}"
    )


def taskmanager_container() -> str:
    output = compose("ps", "-q", "taskmanager", capture_output=True).stdout.strip()
    if not output:
        raise RuntimeError("TaskManager container was not found")
    return output.splitlines()[0]


def inspect_container(container_id: str) -> dict[str, object]:
    return json.loads(
        subprocess.run(
            ["podman", "inspect", container_id],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    )[0]["State"]


def idempotency_summary() -> str:
    query = """
    SELECT
      (SELECT COUNT(*) - COUNT(DISTINCT CONCAT_WS(':', source_partition, sequence_number))
       FROM flos.billing_event_audit),
      (SELECT COUNT(*) - COUNT(DISTINCT CONCAT_WS(':', source_partition, sequence_number))
       FROM flos.billing_too_late_events),
      (SELECT COUNT(*) - COUNT(DISTINCT CONCAT_WS(':', customer_id, window_start, window_end))
       FROM flos.fee_reports);
    """
    return compose(
        "exec",
        "-T",
        "mysql",
        "mysql",
        "--batch",
        "--skip-column-names",
        "--user=flos",
        "--password=flos",
        "--execute",
        query,
        capture_output=True,
    ).stdout.strip()


def main() -> int:
    run_id = f"{datetime.now(UTC).strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:8]}"
    topic = f"billing-recovery-{run_id}"
    group = f"flos-billing-recovery-{run_id}"
    evidence_path = CHECKPOINT_ROOT / run_id / "run-manifest.json"
    manifest: dict[str, object] = {
        "schemaVersion": 1,
        "status": "RUNNING",
        "runId": run_id,
        "startedAt": utc_now(),
        "revision": git_revision(),
        "topic": topic,
        "consumerGroup": group,
        "checkpointStorageType": "filesystem",
        "checkpointStorage": "file:///opt/flink/checkpoints",
        "evidencePath": str(evidence_path),
    }
    job_id: str | None = None
    persist(evidence_path, manifest)

    try:
        wait_for_flink()
        prepare_database()
        previous_jobs = active_job_ids()
        publish_fixture(topic, "initial")
        compose(
            "exec",
            "-T",
            "-e",
            f"FLOS_KAFKA_TOPIC={topic}",
            "-e",
            f"FLOS_KAFKA_GROUP_ID={group}",
            "jobmanager",
            "flink",
            "run",
            "-d",
            "-c",
            JOB_CLASS,
            JOB_JAR,
        )
        job_id = wait_for_new_job(previous_jobs)
        manifest["jobId"] = job_id
        persist(evidence_path, manifest)

        wait_for_query("16\t150.00\t17", query_report_summary, "initial billing report summary")
        wait_for_partition_assignments(group)
        completed, checkpoint_payload = wait_for_completed_checkpoint(job_id)
        before_failure_offsets = wait_for_committed_offsets(group)
        manifest["checkpoint"] = completed
        manifest["checkpointSummaryBeforeFailure"] = checkpoint_payload
        manifest["sourceOffsetsBeforeFailure"] = before_failure_offsets
        persist(evidence_path, manifest)

        before_container = taskmanager_container()
        manifest["failure"] = {
            "component": "taskmanager",
            "injectedAt": utc_now(),
            "containerBefore": before_container,
            "stateBefore": inspect_container(before_container),
        }
        persist(evidence_path, manifest)
        compose("kill", "taskmanager")
        compose("up", "-d", "taskmanager")
        after_container = taskmanager_container()
        manifest["failure"].update(
            {
                "restoredAt": utc_now(),
                "containerAfter": after_container,
                "stateAfter": inspect_container(after_container),
            }
        )
        persist(evidence_path, manifest)

        restored, states, restored_payload = wait_for_restored_checkpoint(job_id, completed)
        after_recovery_offsets = wait_for_committed_offsets(group)
        if any(
            after_recovery_offsets[partition]["current"]
            < before_failure_offsets[partition]["current"]
            for partition in before_failure_offsets
        ):
            raise RuntimeError(
                f"source offsets regressed after restore: before={before_failure_offsets}, "
                f"after={after_recovery_offsets}"
            )
        manifest["recovery"] = {
            "jobStates": states,
            "restoredCheckpoint": restored,
            "checkpointSummaryAfterRecovery": restored_payload,
            "sourceOffsetsAfterRecovery": after_recovery_offsets,
        }
        persist(evidence_path, manifest)

        publish_fixture(topic, "correction")
        wait_for_query("16\t153.00\t18", query_report_summary, "corrected billing report summary")
        publish_fixture(topic, "advance")
        publish_fixture(topic, "too_late")
        wait_for_query(
            "162.00\t19\t9.00\t1\t153.00\t18\t0.00\t0",
            query_reconciliation,
            "source-to-sink reconciliation",
        )
        wait_for_query("0\t0\t0", idempotency_summary, "idempotent sink keys")
        final_offsets = wait_for_committed_offsets(group)
        if final_offsets["0"]["current"] <= before_failure_offsets["0"]["current"]:
            raise RuntimeError(
                "source partition 0 did not advance after recovery: "
                f"before={before_failure_offsets}, "
                f"after={final_offsets}"
            )
        manifest["sourceOffsetsAfterReplay"] = final_offsets
        manifest["outputs"] = {
            "report": query_report_summary(),
            "reconciliation": query_reconciliation(),
            "idempotency": idempotency_summary(),
        }
        manifest["verification"] = {
            "completedCheckpoint": True,
            "taskManagerFailureInjected": True,
            "sameJobRestored": True,
            "checkpointRestored": True,
            "sourceProgressVerified": True,
            "idempotentSinkKeys": True,
            "reportReconciled": True,
        }
        manifest["status"] = "PASS"
        manifest["finishedAt"] = utc_now()
        persist(evidence_path, manifest)
        print(f"Flink billing recovery passed; evidence: {evidence_path}")
        return 0
    except (
        RuntimeError,
        subprocess.CalledProcessError,
        urllib.error.URLError,
        ValueError,
    ) as error:
        manifest["status"] = "FAIL"
        manifest["error"] = str(error)
        manifest["finishedAt"] = utc_now()
        persist(evidence_path, manifest)
        print(f"Flink billing recovery failed; evidence: {evidence_path}: {error}", file=sys.stderr)
        return 1
    finally:
        cleanup: dict[str, str] = {}
        if job_id is not None:
            with contextlib.suppress(subprocess.CalledProcessError):
                cancel_job(job_id)
            cleanup["job"] = "cancel attempted"
        with contextlib.suppress(subprocess.CalledProcessError):
            delete_topic(topic)
        cleanup["topic"] = "delete attempted"
        manifest["cleanup"] = cleanup
        persist(evidence_path, manifest)


if __name__ == "__main__":
    raise SystemExit(main())

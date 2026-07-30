#!/usr/bin/env python3

from __future__ import annotations

import contextlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from collections.abc import Callable
from pathlib import Path

from flink_observability_smoke import SUCCESS_MESSAGE, wait_for_observability

PROJECT_ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = PROJECT_ROOT / "environments" / "flink" / "compose.yaml"
JOB_JAR = "/opt/flink/usrlib/pipeline-lab.jar"
JOB_CLASS = "io.github.azusachino.flos.flink.pipeline.BillingPipelineJob"
FIXTURE_CLASS = "io.github.azusachino.flos.flink.pipeline.BillingFixtureProducer"
JOB_NAME = "flos-five-minute-billing-pipeline"
FLINK_OVERVIEW_URL = "http://localhost:8081/overview"
FLINK_JOBS_URL = "http://localhost:8081/jobs/overview"
EXPECTED_PARTITIONS = set(range(16))
TIMEOUT_SECONDS = 90
CHECK_OBSERVABILITY = os.environ.get("FLINK_OBSERVABILITY_SMOKE") == "1"


def compose(*arguments: str, capture_output: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["podman", "compose", "-f", str(COMPOSE_FILE), *arguments],
        cwd=PROJECT_ROOT,
        check=True,
        text=True,
        capture_output=capture_output,
    )


def request_json(url: str) -> dict[str, object]:
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.load(response)


def wait_for_flink() -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            overview = request_json(FLINK_OVERVIEW_URL)
            if int(overview.get("taskmanagers", 0)) >= 1:
                return
        except (OSError, ValueError, urllib.error.URLError):
            pass
        time.sleep(2)
    raise RuntimeError("Flink did not report a ready TaskManager before the timeout")


def prepare_database() -> None:
    ddl = """
    CREATE TABLE IF NOT EXISTS flos.fee_reports (
      customer_id VARCHAR(128) NOT NULL,
      window_start TIMESTAMP(3) NOT NULL,
      window_end TIMESTAMP(3) NOT NULL,
      total_fee DECIMAL(19, 2) NOT NULL,
      event_count BIGINT NOT NULL,
      PRIMARY KEY (customer_id, window_start, window_end)
    );
    CREATE TABLE IF NOT EXISTS flos.billing_event_audit (
      source_partition INT NOT NULL,
      sequence_number BIGINT NOT NULL,
      customer_id VARCHAR(128) NOT NULL,
      fee DECIMAL(19, 2) NOT NULL,
      occurred_at TIMESTAMP(3) NOT NULL,
      PRIMARY KEY (source_partition, sequence_number)
    );
    CREATE TABLE IF NOT EXISTS flos.billing_too_late_events (
      source_partition INT NOT NULL,
      sequence_number BIGINT NOT NULL,
      customer_id VARCHAR(128) NOT NULL,
      fee DECIMAL(19, 2) NOT NULL,
      occurred_at TIMESTAMP(3) NOT NULL,
      PRIMARY KEY (source_partition, sequence_number)
    );
    TRUNCATE TABLE flos.fee_reports;
    TRUNCATE TABLE flos.billing_event_audit;
    TRUNCATE TABLE flos.billing_too_late_events;
    """
    compose(
        "exec",
        "-T",
        "mysql",
        "mysql",
        "--user=flos",
        "--password=flos",
        "--execute",
        ddl,
    )


def publish_fixture(topic: str, phase: str) -> None:
    compose(
        "exec",
        "-T",
        "jobmanager",
        "java",
        "-cp",
        JOB_JAR,
        FIXTURE_CLASS,
        "kafka:9092",
        topic,
        phase,
    )
    if phase == "initial":
        description = compose(
            "exec",
            "-T",
            "kafka",
            "/opt/kafka/bin/kafka-topics.sh",
            "--bootstrap-server",
            "kafka:9092",
            "--describe",
            "--topic",
            topic,
            capture_output=True,
        ).stdout
        if "PartitionCount: 16" not in description:
            raise RuntimeError(f"topic does not have 16 partitions:\n{description}")


def submit_job(topic: str, group: str) -> None:
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


def find_job_id() -> str | None:
    jobs = request_json(FLINK_JOBS_URL).get("jobs", [])
    matching = [
        job
        for job in jobs
        if job.get("name") == JOB_NAME and job.get("state") not in {"CANCELED", "FAILED"}
    ]
    return str(matching[0]["jid"]) if matching else None


def query_report_summary() -> str:
    query = """
    SELECT COUNT(*), CAST(SUM(total_fee) AS CHAR), SUM(event_count)
    FROM flos.fee_reports
    WHERE window_start = '2026-07-30 12:00:00.000'
      AND window_end = '2026-07-30 12:05:00.000';
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


def wait_for_query(expected: str, query: Callable[[], str], description: str) -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last_value = ""
    while time.monotonic() < deadline:
        last_value = query()
        if last_value == expected:
            return
        time.sleep(2)
    raise RuntimeError(f"{description} did not become {expected!r}; last value was {last_value!r}")


def query_reconciliation() -> str:
    query = """
    SELECT
      CAST(a.audit_fee AS CHAR),
      a.audit_count,
      CAST(l.late_fee AS CHAR),
      l.late_count,
      CAST(r.report_fee AS CHAR),
      r.report_count,
      CAST(a.audit_fee - l.late_fee - r.report_fee AS CHAR),
      a.audit_count - l.late_count - r.report_count
    FROM (
      SELECT SUM(fee) AS audit_fee, COUNT(*) AS audit_count
      FROM flos.billing_event_audit
      WHERE occurred_at >= '2026-07-30 12:00:00.000'
        AND occurred_at < '2026-07-30 12:05:00.000'
    ) a
    CROSS JOIN (
      SELECT COALESCE(SUM(fee), 0) AS late_fee, COUNT(*) AS late_count
      FROM flos.billing_too_late_events
      WHERE occurred_at >= '2026-07-30 12:00:00.000'
        AND occurred_at < '2026-07-30 12:05:00.000'
    ) l
    CROSS JOIN (
      SELECT SUM(total_fee) AS report_fee, SUM(event_count) AS report_count
      FROM flos.fee_reports
      WHERE window_start = '2026-07-30 12:00:00.000'
        AND window_end = '2026-07-30 12:05:00.000'
    ) r;
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


def wait_for_partition_assignments(group: str) -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last_output = ""
    while time.monotonic() < deadline:
        last_output = compose(
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
        partitions = {
            int(fields[2])
            for line in last_output.splitlines()
            if len(fields := line.split()) >= 3 and fields[2].isdigit()
        }
        if partitions == EXPECTED_PARTITIONS:
            return
        time.sleep(2)
    raise RuntimeError(f"consumer group did not cover partitions 0-15:\n{last_output}")


def cancel_job(job_id: str) -> None:
    compose("exec", "-T", "jobmanager", "flink", "cancel", job_id)


def delete_topic(topic: str) -> None:
    compose(
        "exec",
        "-T",
        "kafka",
        "/opt/kafka/bin/kafka-topics.sh",
        "--bootstrap-server",
        "kafka:9092",
        "--delete",
        "--topic",
        topic,
    )


def main() -> int:
    suffix = uuid.uuid4().hex[:8]
    topic = f"billing-events-{suffix}"
    group = f"flos-billing-smoke-{suffix}"
    job_id: str | None = None

    try:
        wait_for_flink()
        prepare_database()
        publish_fixture(topic, "initial")
        submit_job(topic, group)

        deadline = time.monotonic() + TIMEOUT_SECONDS
        while time.monotonic() < deadline and job_id is None:
            job_id = find_job_id()
            time.sleep(1)
        if job_id is None:
            raise RuntimeError("submitted billing job was not visible through Flink REST")

        wait_for_query(
            "16\t150.00\t17",
            query_report_summary,
            "initial billing report summary",
        )
        wait_for_partition_assignments(group)
        if CHECK_OBSERVABILITY:
            wait_for_observability()
            print(SUCCESS_MESSAGE)

        publish_fixture(topic, "correction")
        wait_for_query(
            "16\t153.00\t18",
            query_report_summary,
            "corrected billing report summary",
        )

        publish_fixture(topic, "advance")
        publish_fixture(topic, "too_late")
        wait_for_query(
            "162.00\t19\t9.00\t1\t153.00\t18\t0.00\t0",
            query_reconciliation,
            "source-to-sink reconciliation",
        )

        print(
            "billing smoke: 16 partitions, corrected report 153.00 / 18 events, "
            "one 9.00 too-late event, reconciliation delta 0.00 / 0 events"
        )
        return 0
    except (
        RuntimeError,
        subprocess.CalledProcessError,
        urllib.error.URLError,
        ValueError,
    ) as error:
        print(f"Flink billing smoke test failed: {error}", file=sys.stderr)
        return 1
    finally:
        if job_id is not None:
            with contextlib.suppress(subprocess.CalledProcessError):
                cancel_job(job_id)
        with contextlib.suppress(subprocess.CalledProcessError):
            delete_topic(topic)


if __name__ == "__main__":
    raise SystemExit(main())

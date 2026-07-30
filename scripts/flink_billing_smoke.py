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
from pathlib import Path

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
    TRUNCATE TABLE flos.fee_reports;
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


def publish_fixture(topic: str) -> None:
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
    )
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


def wait_for_reports() -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if query_report_summary() == "16\t150.00\t17":
            return
        time.sleep(2)
    raise RuntimeError(
        "billing report summary did not become 16 rows / 150.00 fee / 17 events; "
        f"last value was {query_report_summary()!r}"
    )


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
        publish_fixture(topic)
        submit_job(topic, group)

        deadline = time.monotonic() + TIMEOUT_SECONDS
        while time.monotonic() < deadline and job_id is None:
            job_id = find_job_id()
            time.sleep(1)
        if job_id is None:
            raise RuntimeError("submitted billing job was not visible through Flink REST")

        wait_for_reports()
        wait_for_partition_assignments(group)
        print("billing smoke: 16 partitions assigned, 16 reports, total fee 150.00, 17 events")
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

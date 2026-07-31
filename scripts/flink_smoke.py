#!/usr/bin/env python3

from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = PROJECT_ROOT / "environments" / "flink" / "compose.yaml"
JOB_JAR = "/opt/flink/usrlib/operator-lab.jar"
JOB_NAME = "flos-flink-operator-lab"
FLINK_OVERVIEW_URL = "http://localhost:8081/overview"
FLINK_JOBS_URL = "http://localhost:8081/jobs/overview"
STARTUP_TIMEOUT_SECONDS = 90
JOB_TIMEOUT_SECONDS = 90


def request_json(url: str) -> dict[str, object]:
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.load(response)


def wait_for_flink() -> None:
    deadline = time.monotonic() + STARTUP_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            overview = request_json(FLINK_OVERVIEW_URL)
            if int(overview.get("taskmanagers", 0)) >= 1:
                return
        except OSError, ValueError, urllib.error.URLError:
            pass
        time.sleep(2)
    raise RuntimeError("Flink did not report a ready TaskManager before the timeout")


def submit_job() -> None:
    subprocess.run(
        [
            "podman",
            "compose",
            "-f",
            str(COMPOSE_FILE),
            "exec",
            "-T",
            "jobmanager",
            "flink",
            "run",
            "-d",
            JOB_JAR,
        ],
        cwd=PROJECT_ROOT,
        check=True,
    )


def wait_for_success() -> None:
    deadline = time.monotonic() + JOB_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        jobs = request_json(FLINK_JOBS_URL).get("jobs", [])
        matching_jobs = [job for job in jobs if job.get("name") == JOB_NAME]
        if matching_jobs:
            state = matching_jobs[0].get("state")
            if state == "FINISHED":
                print(f"{JOB_NAME}: FINISHED")
                return
            if state in {"FAILED", "CANCELED"}:
                raise RuntimeError(f"{JOB_NAME} ended in state {state}")
        time.sleep(2)
    raise RuntimeError(f"{JOB_NAME} did not finish before the timeout")


def main() -> int:
    try:
        wait_for_flink()
        submit_job()
        wait_for_success()
    except (RuntimeError, subprocess.CalledProcessError, urllib.error.URLError) as error:
        print(f"Flink smoke test failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

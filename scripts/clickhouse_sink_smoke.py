#!/usr/bin/env python3

from __future__ import annotations

import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = PROJECT_ROOT / "environments" / "clickhouse" / "compose.yaml"
JOB_JAR = (
    PROJECT_ROOT
    / "modules"
    / "flink"
    / "clickhouse-sink-lab"
    / "target"
    / "clickhouse-sink-lab.jar"
)
CLICKHOUSE_URL = "http://localhost:18123"
TIMEOUT_SECONDS = 60


def compose(*arguments: str, capture_output: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["podman", "compose", "-f", str(COMPOSE_FILE), *arguments],
        cwd=PROJECT_ROOT,
        check=True,
        text=True,
        capture_output=capture_output,
    )


def query(sql: str) -> str:
    request = urllib.request.Request(
        f"{CLICKHOUSE_URL}/?database=learning",
        data=sql.encode(),
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return response.read().decode().strip()


def wait_for_clickhouse() -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            if query("SELECT 1") == "1":
                return
        except OSError, urllib.error.URLError:
            pass
        time.sleep(2)
    raise RuntimeError("ClickHouse did not become ready before the timeout")


def run_job() -> float:
    started = time.perf_counter()
    subprocess.run(
        ["java", "-jar", str(JOB_JAR), "--url", CLICKHOUSE_URL, "--records", "5"],
        cwd=PROJECT_ROOT,
        check=True,
        timeout=TIMEOUT_SECONDS,
    )
    return (time.perf_counter() - started) * 1000


def main() -> int:
    try:
        wait_for_clickhouse()
        query("TRUNCATE TABLE learning.sink_events")
        first_ms = run_job()
        first_count = query("SELECT count() FROM learning.sink_events")
        if first_count != "5":
            raise RuntimeError(f"first sink run inserted {first_count} rows, expected 5")
        replay_ms = run_job()
        replay_count = query("SELECT count() FROM learning.sink_events")
        if replay_count != "10":
            raise RuntimeError(
                f"replay inserted {replay_count} rows, expected 10; duplicate behavior changed"
            )
        print(
            "clickhouse sink smoke: inserted 5 rows, replay produced 10 append-only rows "
            f"(first_ms={first_ms:.0f}, replay_ms={replay_ms:.0f})"
        )
    except (
        RuntimeError,
        subprocess.CalledProcessError,
        subprocess.TimeoutExpired,
        urllib.error.URLError,
    ) as error:
        print(f"ClickHouse sink smoke failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

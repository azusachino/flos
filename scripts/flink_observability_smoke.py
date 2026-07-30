#!/usr/bin/env python3

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request

PROMETHEUS_URL = "http://localhost:9090"
GRAFANA_URL = "http://localhost:3000"
TIMEOUT_SECONDS = 90
EXPECTED_TARGETS = {"jobmanager:9249", "taskmanager:9249"}
EXPECTED_ALERTS = {
    "FlinkMetricsTargetDown",
    "FlinkTaskMostlyBackpressured",
    "FlinkLateRecordsDetected",
    "FlinkCheckpointFailures",
}
EXPECTED_METRICS = {
    "flink_jobmanager_numRegisteredTaskManagers",
    "flink_jobmanager_job_uptime",
    "flink_taskmanager_job_task_backPressuredTimeMsPerSecond",
    "flink_taskmanager_job_task_operator_numRecordsIn",
}
DASHBOARD_UID = "flink-billing-operations"
SUCCESS_MESSAGE = (
    "observability smoke: 2 Flink targets up, 4 alert rules loaded, "
    "4 runtime metrics present, 6 dashboard panels provisioned"
)


def request_json(url: str) -> dict[str, object]:
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.load(response)


def prometheus(path: str) -> dict[str, object]:
    return request_json(f"{PROMETHEUS_URL}{path}")


def wait_for_observability() -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last_error = "not checked"
    while time.monotonic() < deadline:
        try:
            verify_targets()
            verify_rules()
            verify_metrics()
            verify_dashboard()
            return
        except (KeyError, RuntimeError, OSError, ValueError, urllib.error.URLError) as error:
            last_error = str(error)
        time.sleep(2)
    raise RuntimeError(f"observability stack did not become verifiable: {last_error}")


def verify_targets() -> None:
    payload = prometheus("/api/v1/targets")
    targets = payload["data"]["activeTargets"]
    flink_targets = {
        target["labels"]["instance"]: target["health"]
        for target in targets
        if target["labels"].get("job") == "flink"
    }
    if set(flink_targets) != EXPECTED_TARGETS:
        raise RuntimeError(f"unexpected Flink scrape targets: {flink_targets}")
    unhealthy = {target: health for target, health in flink_targets.items() if health != "up"}
    if unhealthy:
        raise RuntimeError(f"unhealthy Flink scrape targets: {unhealthy}")


def verify_rules() -> None:
    payload = prometheus("/api/v1/rules?type=alert")
    rules = {
        rule["name"]
        for group in payload["data"]["groups"]
        for rule in group["rules"]
        if rule["type"] == "alerting"
    }
    missing = EXPECTED_ALERTS - rules
    if missing:
        raise RuntimeError(f"Prometheus did not load alert rules: {sorted(missing)}")


def verify_metrics() -> None:
    payload = prometheus("/api/v1/label/__name__/values")
    metrics = set(payload["data"])
    missing = EXPECTED_METRICS - metrics
    if missing:
        raise RuntimeError(f"Flink metrics are missing: {sorted(missing)}")


def verify_dashboard() -> None:
    query = urllib.parse.urlencode({"query": "Flink Billing Operations"})
    search = request_json(f"{GRAFANA_URL}/api/search?{query}")
    dashboards = {entry["uid"] for entry in search}
    if DASHBOARD_UID not in dashboards:
        raise RuntimeError(f"Grafana dashboard {DASHBOARD_UID!r} was not provisioned")

    dashboard = request_json(f"{GRAFANA_URL}/api/dashboards/uid/{DASHBOARD_UID}")
    panels = dashboard["dashboard"]["panels"]
    if len(panels) != 6:
        raise RuntimeError(f"expected 6 dashboard panels but found {len(panels)}")


def main() -> int:
    try:
        wait_for_observability()
    except (RuntimeError, urllib.error.URLError, ValueError) as error:
        print(f"Flink observability smoke test failed: {error}")
        return 1

    print(SUCCESS_MESSAGE)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

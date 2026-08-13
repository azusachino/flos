#!/usr/bin/env python3

from __future__ import annotations

import argparse
import concurrent.futures
import json
import statistics
import time
import urllib.parse
import urllib.request

CLICKHOUSE_URL = "http://localhost:18123"
EVENT_COLUMNS = "event_id, user_id, order_id, event_time, ingested_at, symbol, version, amount"


def request(sql: str, body: bytes | None = None) -> bytes:
    query = urllib.parse.urlencode({"database": "learning"})
    request = urllib.request.Request(
        f"{CLICKHOUSE_URL}/?{query}",
        data=(body if body is not None else sql.encode()),
        method="POST",
        headers={"Content-Type": "text/plain"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def scalar(sql: str) -> str:
    return request(sql).decode().strip()


def event_rows(
    count: int,
    *,
    prefix: str,
    event_day: int,
    ingested_day: int,
    user_fn=None,
    symbol_fn=None,
) -> list[dict[str, object]]:
    symbols = ("BTC", "ETH", "SOL", "XRP")
    return [
        {
            "event_id": f"{prefix}-{index}",
            "user_id": user_fn(index) if user_fn else index % 1000,
            "order_id": index + 1,
            "event_time": f"2026-08-{event_day:02d}T00:{index % 60:02d}:00.000Z",
            "ingested_at": f"2026-08-{ingested_day:02d}T01:{index % 60:02d}:00.000Z",
            "symbol": symbol_fn(index) if symbol_fn else symbols[index % len(symbols)],
            "version": 1,
            "amount": f"{(index % 10000) / 100:.2f}",
        }
        for index in range(count)
    ]


def insert(table: str, rows: list[dict[str, object]]) -> None:
    payload = "\n".join(json.dumps(row, separators=(",", ":")) for row in rows) + "\n"
    request(
        "",
        body=(
            f"INSERT INTO learning.{table} ({EVENT_COLUMNS}) FORMAT JSONEachRow\n" + payload
        ).encode(),
    )


def create_tables() -> None:
    request(
        """
        CREATE TABLE IF NOT EXISTS learning.case_events
        (
            event_id String,
            user_id UInt64,
            order_id UInt64,
            event_time DateTime64(3, 'UTC'),
            ingested_at DateTime64(3, 'UTC'),
            symbol LowCardinality(String),
            version UInt32,
            amount Decimal(12, 2)
        )
        ENGINE = MergeTree
        PARTITION BY toYYYYMM(event_time)
        ORDER BY (user_id, event_time, symbol, order_id, event_id)
        """
    )
    request(
        """
        CREATE TABLE IF NOT EXISTS learning.case_latest
        (
            event_id String,
            user_id UInt64,
            order_id UInt64,
            event_time DateTime64(3, 'UTC'),
            ingested_at DateTime64(3, 'UTC'),
            symbol LowCardinality(String),
            version UInt32,
            amount Decimal(12, 2)
        )
        ENGINE = ReplacingMergeTree(version)
        ORDER BY event_id
        """
    )
    request(
        """
        CREATE TABLE IF NOT EXISTS learning.case_export_events
        (
            event_id String,
            user_id UInt64,
            order_id UInt64,
            event_time DateTime64(3, 'UTC'),
            ingested_at DateTime64(3, 'UTC'),
            symbol LowCardinality(String),
            version UInt32,
            amount Decimal(12, 2)
        )
        ENGINE = MergeTree
        PARTITION BY toYYYYMM(event_time)
        ORDER BY (user_id, event_time, symbol, order_id, event_id)
        """
    )


def truncate(*tables: str) -> None:
    for table in tables:
        scalar(f"TRUNCATE TABLE learning.{table}")


def correctness_case() -> None:
    truncate("case_events", "case_latest")
    late = event_rows(1, prefix="late", event_day=1, ingested_day=3)
    insert("case_events", late)
    correction = event_rows(1, prefix="order", event_day=1, ingested_day=2)
    correction[0]["amount"] = "10.00"
    insert("case_latest", correction)
    correction[0]["version"] = 2
    correction[0]["ingested_at"] = "2026-08-04T01:00:00.000Z"
    correction[0]["amount"] = "12.00"
    insert("case_latest", correction)
    late_count = scalar("SELECT count() FROM learning.case_events WHERE event_time < ingested_at")
    raw_count = scalar("SELECT count() FROM learning.case_latest")
    latest_count = scalar("SELECT count() FROM learning.case_latest FINAL")
    latest_amount = scalar(
        "SELECT toString(amount) FROM learning.case_latest FINAL WHERE event_id = 'order-0'"
    )
    print(
        "correctness: late_event_rows="
        f"{late_count} replacing_raw_rows={raw_count} "
        f"replacing_final_rows={latest_count} latest_amount={latest_amount}"
    )


def latency_ms(sql: str) -> float:
    started = time.perf_counter()
    request(sql)
    return (time.perf_counter() - started) * 1000


def concurrent_search_case() -> tuple[float, float, int]:
    sql = """
        SELECT count()
        FROM learning.case_export_events
        WHERE user_id = 42 AND event_time >= '2026-08-01' AND event_time < '2026-09-01'
    """
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        samples = list(pool.map(lambda _: latency_ms(sql), range(12)))
    return statistics.median(samples), max(samples), len(samples)


def skew_and_backfill_case(backfill_rows: int) -> None:
    truncate("case_events")
    skew_rows = event_rows(
        10_000,
        prefix="skew",
        event_day=5,
        ingested_day=5,
        user_fn=lambda index: 0 if index < 8_000 else index % 1_000,
    )
    insert("case_events", skew_rows)
    hot = latency_ms("SELECT count() FROM learning.case_events WHERE user_id = 0")
    cold = latency_ms("SELECT count() FROM learning.case_events WHERE user_id = 999")
    hot_rows = scalar("SELECT count() FROM learning.case_events WHERE user_id = 0")
    cold_rows = scalar("SELECT count() FROM learning.case_events WHERE user_id = 999")
    print(
        f"skew: hot_user_rows={hot_rows} cold_user_rows={cold_rows} "
        f"hot_ms={hot:.2f} cold_ms={cold:.2f}"
    )

    truncate("case_events")
    insert("case_events", event_rows(2_000, prefix="live", event_day=20, ingested_day=20))
    backfill = event_rows(
        backfill_rows,
        prefix="backfill",
        event_day=1,
        ingested_day=21,
    )
    backfill_started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        future = pool.submit(insert, "case_events", backfill)
        search_samples = []
        while not future.done():
            search_samples.append(
                latency_ms("SELECT count() FROM learning.case_events WHERE user_id = 42")
            )
        future.result()
    rows_after = scalar("SELECT count() FROM learning.case_events")
    print(
        f"backfill: rows_before={2_000} rows_after={rows_after} "
        f"elapsed_ms={(time.perf_counter() - backfill_started) * 1000:.2f} "
        f"concurrent_search_samples={len(search_samples)}"
    )


def export_case(export_rows: int) -> None:
    truncate("case_export_events")
    before_cutoff = event_rows(
        export_rows,
        prefix="export",
        event_day=1,
        ingested_day=1,
    )
    after_cutoff = event_rows(
        100,
        prefix="future",
        event_day=3,
        ingested_day=3,
    )
    insert("case_export_events", before_cutoff + after_cutoff)
    export_sql = """
        SELECT event_id, event_time, amount
        FROM learning.case_export_events
        WHERE event_time < '2026-08-02'
        ORDER BY event_id
        FORMAT CSVWithNames
    """
    first = request(export_sql)
    insert(
        "case_export_events",
        event_rows(100, prefix="post-snapshot", event_day=3, ingested_day=4),
    )
    second = request(export_sql)
    if first != second:
        raise RuntimeError("fixed-cutoff CSV export changed after post-cutoff inserts")

    slow_export_sql = """
        SELECT event_id, event_time, amount
        FROM learning.case_export_events
        WHERE sleepEachRow(0.00005) = 0
        FORMAT CSVWithNames
    """
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        export_future = pool.submit(request, slow_export_sql)
        median_ms, max_ms, samples = concurrent_search_case()
        export_bytes = len(export_future.result())
    print(
        f"csv: fixed_cutoff_bytes={len(first)} stable_after_insert=true "
        f"slow_export_bytes={export_bytes} "
        f"slow_export_ms={(time.perf_counter() - started) * 1000:.2f} "
        f"search_p50_ms={median_ms:.2f} search_max_ms={max_ms:.2f} search_samples={samples}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Run ClickHouse correctness and contention cases")
    parser.add_argument("--backfill-rows", type=int, default=25_000)
    parser.add_argument("--export-rows", type=int, default=10_000)
    args = parser.parse_args()
    create_tables()
    correctness_case()
    skew_and_backfill_case(args.backfill_rows)
    export_case(args.export_rows)
    print("cases note: local evidence only; no distributed capacity or production SLO claim")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

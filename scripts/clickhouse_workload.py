#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import time
import urllib.parse
import urllib.request

CLICKHOUSE_URL = "http://localhost:18123"
TABLES = ("workload_events", "workload_events_symbol_first")


def request(sql: str, body: bytes | None = None) -> str:
    query = urllib.parse.urlencode({"database": "learning"})
    request = urllib.request.Request(
        f"{CLICKHOUSE_URL}/?{query}",
        data=(body if body is not None else sql.encode()),
        method="POST",
        headers={"Content-Type": "text/plain"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode().strip()


def rows(count: int, table: str) -> bytes:
    symbols = ("BTC", "ETH", "SOL", "XRP")
    records = [
        json.dumps(
            {
                "user_id": index % 1000,
                "order_id": index + 1,
                "event_time": f"2026-08-{(index % 28) + 1:02d}T00:00:00.000Z",
                "symbol": symbols[index % len(symbols)],
                "amount": f"{(index % 10000) / 100:.2f}",
            },
            separators=(",", ":"),
        )
        for index in range(count)
    ]
    return (
        f"INSERT INTO learning.{table} FORMAT JSONEachRow\n" + "\n".join(records) + "\n"
    ).encode()


def measure(table: str, name: str, sql: str, repetitions: int) -> None:
    elapsed = []
    result = ""
    for _ in range(repetitions):
        started = time.perf_counter()
        result = request(sql.format(table=table))
        elapsed.append((time.perf_counter() - started) * 1000)
    print(
        f"{table} / {name}: result={result} "
        f"p50_ms={sorted(elapsed)[len(elapsed) // 2]:.2f} "
        f"min_ms={min(elapsed):.2f} max_ms={max(elapsed):.2f}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a small ClickHouse query-shape benchmark")
    parser.add_argument("--rows", type=int, default=10_000)
    parser.add_argument("--repetitions", type=int, default=3)
    args = parser.parse_args()

    for table, order_by in (
        ("workload_events", "(user_id, event_time, symbol, order_id)"),
        ("workload_events_symbol_first", "(symbol, event_time, user_id, order_id)"),
    ):
        request(
            f"""
            CREATE TABLE IF NOT EXISTS learning.{table}
            (
                user_id UInt64,
                order_id UInt64,
                event_time DateTime64(3, 'UTC'),
                symbol LowCardinality(String),
                amount Decimal(12, 2)
            )
            ENGINE = MergeTree
            PARTITION BY toYYYYMM(event_time)
            ORDER BY {order_by}
            """
        )
        request(f"TRUNCATE TABLE learning.{table}")
        request("", rows(args.rows, table))
    print(f"loaded {args.rows} identical rows into {', '.join(TABLES)}")

    queries = (
        (
            "user plus time range",
            """
        SELECT count()
        FROM learning.{table}
        WHERE user_id = 42
          AND event_time >= '2026-08-01'
          AND event_time < '2026-09-01'
        """,
        ),
        ("order lookup", "SELECT count() FROM learning.{table} WHERE order_id = 4243"),
        (
            "symbol plus time range",
            """
        SELECT count()
        FROM learning.{table}
        WHERE symbol = 'BTC'
          AND event_time >= '2026-08-01'
          AND event_time < '2026-09-01'
        """,
        ),
        (
            "date range",
            """
        SELECT count()
        FROM learning.{table}
        WHERE event_time >= '2026-08-01'
          AND event_time < '2026-09-01'
        """,
        ),
    )
    for table in TABLES:
        for name, sql in queries:
            measure(table, name, sql, args.repetitions)
    print("benchmark note: this is a local sort-key comparison, not 10B-row capacity evidence")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

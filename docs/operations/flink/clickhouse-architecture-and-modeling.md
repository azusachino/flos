---
title: ClickHouse Architecture and MergeTree Modeling
description: Choose a ClickHouse topology, table key, type system, and correction model for large append-heavy facts.
created: 2026-08-13 00:00
modified: 2026-08-13 00:00
type: concept
status: active
maturity: developing
tags:
    - clickhouse
    - data-modeling
    - architecture
    - data-warehouse
source: https://clickhouse.com/docs/engines/table-engines/mergetree-family
---

# ClickHouse Architecture and MergeTree Modeling

This guide is for the data architect and data modeler. The target is a fact
store with 10B+ mostly append-only records, interactive searches by user,
order, time range, and symbol, plus large CSV exports.

The central rule is to decide topology, table layout, and correctness as three
separate decisions. A compute/storage-separated deployment does not rescue a
sort key that cannot prune the user's queries.

## Decide the topology first

Compare these options with the same workload:

| Option | Strength | Cost or risk to measure |
| --- | --- | --- |
| ClickHouse Cloud or BYOC | Native separation of compute, object storage, and independently sized compute pools | Cache warm-up, egress, service limits, residency, and vendor cost |
| Self-managed replicated cluster | Control over placement, storage, versions, and network | Keeper, replicas, object-storage policy, cache, backups, upgrades, and on-call ownership |
| Local single server | Fastest learning loop | Proves neither distributed failure behavior nor capacity |

For a hard compute/storage-separation requirement, benchmark Cloud or BYOC
first. Keep self-management as an explicit alternative when control, residency,
or cost justifies its operational burden. Do not call local `MergeTree` plus a
volume “separated compute and storage.”

The desired production shape is conceptually:

```text
Flink ingest compute ─┐
interactive read pool ├─> shared ClickHouse data + metadata plane
export read pool ────┘       (object storage, cache, replicas as chosen)
```

The isolation boundary must be tested. Workload scheduling can limit query
resources, but it does not automatically isolate every background activity,
including merges and mutations.

## Start with a boring fact table

The benchmark's first candidate is:

```sql
CREATE TABLE facts
(
    user_id UInt64,
    order_id UInt64,
    event_time DateTime64(3, 'UTC'),
    symbol LowCardinality(String),
    amount Decimal(18, 2)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_time)
ORDER BY (user_id, event_time, symbol, order_id);
```

Why this is only a candidate:

- `PARTITION BY` supports month-scale lifecycle and coarse pruning; it is not
  a substitute for the primary sort key.
- `user_id` first helps the user-plus-time query, but may be poor for a
  symbol-first workload.
- `order_id` is useful as a narrow lookup dimension but does not become fast
  merely because it appears at the end of the key.
- `LowCardinality` is a hypothesis for repeated symbols, not a universal rule
  for every string.
- `DateTime64` preserves the precision required by the product contract; use
  the smallest numeric type that cannot overflow the real domain.

Compare this with a symbol-first key only after measuring the actual query
mix. Add projections or skipping indexes after a baseline query demonstrates a
pruning gap. Every extra structure consumes storage and merge work.

## Choose the correction model explicitly

The sink lesson uses immutable `event_id` values and plain `MergeTree`, so
duplicates remain visible. Production facts need one of these contracts:

| Contract | Storage shape | Reader behavior |
| --- | --- | --- |
| Immutable facts | `MergeTree` | Every accepted event remains a fact; reconcile duplicates upstream or in a derived view |
| Latest state by key | `ReplacingMergeTree` with version | Replacement is eventual; queries requiring correctness may need a deliberate deduplication strategy |
| Explicit correction | Original fact plus correction/tombstone | Readers apply the business rule; cleanup and merge pressure are measurable |
| Physical deletion | Lightweight delete or mutation | Read masking and later cleanup become operational concerns |

No table engine turns an ambiguous client timeout into exactly-once delivery.
Keep an immutable business identity and a reconciliation query even when a
replacement engine is selected.

## Modeling exercises

Run the local harness, then change one dimension at a time:

```sh
make clickhouse-workload
```

Next experiments:

1. Change the generated user distribution from uniform to a hot-key/Zipf-like
   distribution.
2. Compare `(user_id, event_time, symbol, order_id)` with
   `(symbol, event_time, user_id, order_id)`.
3. Add a projection for `order_id`, materialize it, and compare write/merge
   cost with a narrow lookup table.
4. Add a late event and a correction version; define what a reader is allowed
   to see before merges finish.
5. Move one month to cold storage or a separate compute pool and measure cache
   warm-up plus export latency.

Record each result as a decision, not as a new default. The minimum evidence
  is query latency, rows read, bytes read, selected marks, memory, CPU, active
  parts, merge backlog, and cost or resource usage.

## Reading

- [MergeTree family](https://clickhouse.com/docs/engines/table-engines/mergetree-family)
- [ReplacingMergeTree](https://clickhouse.com/docs/engines/table-engines/mergetree-family/replacingmergetree)
- [ClickHouse query optimization guide](https://clickhouse.com/resources/engineering/clickhouse-query-optimisation-definitive-guide)
- [ClickHouse Cloud stateless compute](https://clickhouse.com/blog/clickhouse-cloud-stateless-compute)

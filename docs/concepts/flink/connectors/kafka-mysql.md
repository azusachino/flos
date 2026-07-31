---
title: Kafka Source and MySQL Sink
description: Connect the operator chain to an unbounded Kafka source and a durable MySQL sink.
created: 2026-07-30 00:00
modified: 2026-07-30 21:51
type: concept
status: active
maturity: developing
tags:
    - apache-flink
    - kafka
    - mysql
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/connectors/datastream/kafka/
---

# Kafka Source and MySQL Sink

Real Flink jobs sit between systems. The `pipeline-lab` contains two connector paths.

The introductory purchase path keeps the operator semantics from the bounded tutorial and replaces its in-memory edges:

```text
Kafka purchase-events
  -> Jackson deserialization
  -> filter
  -> map
  -> keyBy
  -> reduce
  -> JDBC upsert
  -> MySQL customer_spend
```

The Kafka source starts at the earliest available offset for the tutorial consumer group. The MySQL sink batches idempotent upserts keyed by customer ID. Checkpointing is enabled, but this first sink uses at-least-once delivery. Idempotent writes make retries safe for the accumulated result.

The [16-partition Billing Pipeline](billing-pipeline.md) applies the event-time model to a live connector path:

```text
Kafka billing-events, 16 partitions
  -> metadata-aware OrderEvent deserialization
  -> bounded-out-of-orderness watermarks
  -> keyBy customerId
  -> five-minute tumbling window
  -> JDBC report upsert
  -> MySQL fee_reports
```

## Supporting libraries

- Spring Framework's environment abstraction maps runtime configuration without coupling the Flink job lifecycle to Spring Boot.
- Jackson turns Kafka JSON into the shared `PurchaseEvent` record.
- Lombok removes boilerplate from the immutable configuration value.
- MySQL Connector/J supplies the JDBC driver.

These utilities stay at the integration boundary. The operators remain plain Flink functions so their tests do not need a Spring context, Kafka, or MySQL.

## Configuration

The defaults target service names in `environments/flink/compose.yaml`. Override them through:

- `FLOS_KAFKA_BOOTSTRAP`
- `FLOS_KAFKA_TOPIC`
- `FLOS_KAFKA_GROUP_ID`
- `FLOS_JDBC_URL`
- `FLOS_JDBC_USERNAME`
- `FLOS_JDBC_PASSWORD`

Package both executable labs and start the environment:

```sh
make flink-up
```

The bounded `make flink-smoke` remains the fast runtime acceptance test. Run `make flink-billing-smoke` while the stack is up for the real 16-partition Kafka-to-MySQL acceptance path.

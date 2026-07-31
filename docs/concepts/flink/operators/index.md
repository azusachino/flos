---
title: Operators
description: Understand Flink operators as typed transformations in a distributed dataflow.
created: 2026-07-29 00:00
modified: 2026-07-29 00:00
type: concept
status: active
maturity: developing
aliases:
    - Flink transformations
tags:
    - apache-flink
    - operators
    - stream-processing
source: https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/dev/datastream/operators/overview/
---

# Operators

An operator transforms one or more data streams into a new stream. A chain of operators describes the logical dataflow; Flink decides how that dataflow becomes tasks distributed across available slots.

The initial lab uses:

1. `filter` to reject purchases without a customer, product, or positive amount.
2. `map` to normalize customer and product fields.
3. `keyBy` to partition purchases by customer.
4. `reduce` to maintain each customer's running spend.

## Read the implementation

- `ValidPurchaseFilter` demonstrates one-input-to-zero-or-one-output behavior.
- `NormalizePurchase` demonstrates one-input-to-one-output behavior.
- `RunningSpend` demonstrates incremental keyed aggregation.
- `OperatorLabJob` assembles the transformations into an executable topology.

## Verify the semantics

```sh
make concept-test CONCEPT=flink
```

Then run the same topology on the session cluster:

```sh
make flink-up
make flink-smoke
make flink-down
```

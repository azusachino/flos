CREATE DATABASE IF NOT EXISTS learning;

CREATE TABLE IF NOT EXISTS learning.sink_events
(
    event_id String,
    occurred_at DateTime64(3, 'UTC'),
    customer_id LowCardinality(String),
    symbol String,
    amount Decimal(12, 2)
)
ENGINE = MergeTree
ORDER BY (customer_id, occurred_at, symbol, event_id);

CREATE TABLE IF NOT EXISTS learning.workload_events
(
    user_id UInt64,
    order_id UInt64,
    event_time DateTime64(3, 'UTC'),
    symbol LowCardinality(String),
    amount Decimal(12, 2)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_time)
ORDER BY (user_id, event_time, symbol, order_id);

CREATE TABLE IF NOT EXISTS learning.workload_events_symbol_first
(
    user_id UInt64,
    order_id UInt64,
    event_time DateTime64(3, 'UTC'),
    symbol LowCardinality(String),
    amount Decimal(12, 2)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_time)
ORDER BY (symbol, event_time, user_id, order_id);

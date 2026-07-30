CREATE DATABASE IF NOT EXISTS flos;

CREATE TABLE IF NOT EXISTS flos.customer_spend (
    customer_id VARCHAR(128) PRIMARY KEY,
    amount DECIMAL(19, 2) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS flos.fee_reports (
    customer_id VARCHAR(128) NOT NULL,
    window_start TIMESTAMP(3) NOT NULL,
    window_end TIMESTAMP(3) NOT NULL,
    total_fee DECIMAL(19, 2) NOT NULL,
    event_count BIGINT NOT NULL,
    PRIMARY KEY (customer_id, window_start, window_end)
);

CREATE TABLE IF NOT EXISTS flos.billing_event_audit (
    source_partition INT NOT NULL,
    sequence_number BIGINT NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    fee DECIMAL(19, 2) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (source_partition, sequence_number)
);

CREATE TABLE IF NOT EXISTS flos.billing_too_late_events (
    source_partition INT NOT NULL,
    sequence_number BIGINT NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    fee DECIMAL(19, 2) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (source_partition, sequence_number)
);

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

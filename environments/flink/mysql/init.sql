CREATE DATABASE IF NOT EXISTS flos;

CREATE TABLE IF NOT EXISTS flos.customer_spend (
    customer_id VARCHAR(128) PRIMARY KEY,
    amount DECIMAL(19, 2) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

package io.github.azusachino.flos.flink.operators;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PurchaseEvent(
        String customerId, String product, BigDecimal amount, Instant occurredAt)
        implements Serializable {

    public PurchaseEvent {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

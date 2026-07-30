package io.github.azusachino.flos.flink.eventtime;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record OrderEvent(
        String customerId, int sourcePartition, long sequence, BigDecimal fee, Instant occurredAt)
        implements Serializable {

    public OrderEvent {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

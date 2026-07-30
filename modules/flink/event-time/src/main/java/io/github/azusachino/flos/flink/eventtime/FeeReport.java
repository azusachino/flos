package io.github.azusachino.flos.flink.eventtime;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record FeeReport(
        String customerId,
        Instant windowStart,
        Instant windowEnd,
        BigDecimal totalFee,
        long eventCount)
        implements Serializable {}

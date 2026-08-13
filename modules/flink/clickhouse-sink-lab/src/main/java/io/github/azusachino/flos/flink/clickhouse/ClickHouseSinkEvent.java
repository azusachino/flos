package io.github.azusachino.flos.flink.clickhouse;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ClickHouseSinkEvent(
        String eventId,
        ZonedDateTime occurredAt,
        String customerId,
        String symbol,
        BigDecimal amount)
        implements Serializable {}

package io.github.azusachino.flos.flink.state;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Emitted for every activity, purchase or probe. {@code startedNewOrExpiredCart} is true when the
 * read that produced this snapshot found no live state: either this customer has never purchased,
 * or their previous cart's TTL had already elapsed.
 */
public record CartSnapshot(
        String customerId, BigDecimal total, int count, boolean startedNewOrExpiredCart)
        implements Serializable {}

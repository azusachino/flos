package io.github.azusachino.flos.flink.state;

import java.io.Serializable;
import java.math.BigDecimal;

/** The value actually stored in keyed state, wrapped by Flink's TTL bookkeeping. */
public record CartTotal(BigDecimal total, int count) implements Serializable {}

package io.github.azusachino.flos.flink.eventtime;

import java.io.Serializable;
import java.math.BigDecimal;

public record FeeAccumulator(BigDecimal totalFee, long eventCount) implements Serializable {

    public static FeeAccumulator empty() {
        return new FeeAccumulator(BigDecimal.ZERO, 0);
    }
}

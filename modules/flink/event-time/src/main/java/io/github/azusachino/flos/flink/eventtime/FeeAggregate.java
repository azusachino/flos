package io.github.azusachino.flos.flink.eventtime;

import org.apache.flink.api.common.functions.AggregateFunction;

public final class FeeAggregate
        implements AggregateFunction<OrderEvent, FeeAccumulator, FeeAccumulator> {

    @Override
    public FeeAccumulator createAccumulator() {
        return FeeAccumulator.empty();
    }

    @Override
    public FeeAccumulator add(OrderEvent event, FeeAccumulator accumulator) {
        return new FeeAccumulator(
                accumulator.totalFee().add(event.fee()), accumulator.eventCount() + 1);
    }

    @Override
    public FeeAccumulator getResult(FeeAccumulator accumulator) {
        return accumulator;
    }

    @Override
    public FeeAccumulator merge(FeeAccumulator left, FeeAccumulator right) {
        return new FeeAccumulator(
                left.totalFee().add(right.totalFee()), left.eventCount() + right.eventCount());
    }
}

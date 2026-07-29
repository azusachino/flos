package io.github.azusachino.flos.flink.operators;

import org.apache.flink.api.common.functions.ReduceFunction;

public final class RunningSpend implements ReduceFunction<PurchaseEvent> {

    @Override
    public PurchaseEvent reduce(PurchaseEvent current, PurchaseEvent next) {
        return new PurchaseEvent(
                current.customerId(),
                "running-total",
                current.amount().add(next.amount()),
                next.occurredAt());
    }
}

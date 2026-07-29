package io.github.azusachino.flos.flink.operators;

import org.apache.flink.api.common.functions.FilterFunction;

public final class ValidPurchaseFilter implements FilterFunction<PurchaseEvent> {

    @Override
    public boolean filter(PurchaseEvent event) {
        return !event.customerId().isBlank()
                && !event.product().isBlank()
                && event.amount().signum() > 0;
    }
}

package io.github.azusachino.flos.flink.state;

import java.math.BigDecimal;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public final class CartTotalFunction extends KeyedProcessFunction<String, CartActivity, CartSnapshot> {

    private final StateTtlConfig ttlConfig;
    private transient ValueState<CartTotal> cartState;

    public CartTotalFunction(StateTtlConfig ttlConfig) {
        this.ttlConfig = ttlConfig;
    }

    @Override
    public void open(OpenContext openContext) {
        var descriptor = new ValueStateDescriptor<>("cart-total", CartTotal.class);
        descriptor.enableTimeToLive(ttlConfig);
        cartState = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(CartActivity activity, Context ctx, Collector<CartSnapshot> out)
            throws Exception {
        if (activity.delayBeforeMillis() > 0) {
            Thread.sleep(activity.delayBeforeMillis());
        }

        // Reading state.value() is what triggers Flink's TTL expiry check against this entry's
        // last-access timestamp; a null here means either this customer never purchased, or their
        // previous cart's TTL had already elapsed by the configured StateVisibility.
        CartTotal current = cartState.value();
        boolean startedNewOrExpiredCart = current == null;

        if (activity.probe()) {
            BigDecimal total = current == null ? BigDecimal.ZERO : current.total();
            int count = current == null ? 0 : current.count();
            out.collect(new CartSnapshot(activity.customerId(), total, count, startedNewOrExpiredCart));
            return;
        }

        CartTotal updated =
                startedNewOrExpiredCart
                        ? new CartTotal(activity.amount(), 1)
                        : new CartTotal(current.total().add(activity.amount()), current.count() + 1);
        cartState.update(updated);
        out.collect(
                new CartSnapshot(
                        activity.customerId(), updated.total(), updated.count(), startedNewOrExpiredCart));
    }
}

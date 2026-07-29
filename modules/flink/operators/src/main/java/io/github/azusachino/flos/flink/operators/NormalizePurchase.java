package io.github.azusachino.flos.flink.operators;

import java.util.Locale;
import org.apache.flink.api.common.functions.MapFunction;

public final class NormalizePurchase implements MapFunction<PurchaseEvent, PurchaseEvent> {

    @Override
    public PurchaseEvent map(PurchaseEvent event) {
        return new PurchaseEvent(
                event.customerId().strip().toLowerCase(Locale.ROOT),
                event.product().strip(),
                event.amount(),
                event.occurredAt());
    }
}

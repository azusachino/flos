package io.github.azusachino.flos.flink.state;

import java.util.List;
import org.apache.flink.streaming.api.functions.source.legacy.RichSourceFunction;

/** Emits a fixed sequence of activities as fast as the downstream can accept them. */
public final class CartActivitySource extends RichSourceFunction<CartActivity> {

    private final List<CartActivity> activities;
    private volatile boolean running = true;

    public CartActivitySource(List<CartActivity> activities) {
        this.activities = List.copyOf(activities);
    }

    @Override
    public void run(SourceContext<CartActivity> context) {
        for (CartActivity activity : activities) {
            if (!running) {
                return;
            }
            synchronized (context.getCheckpointLock()) {
                context.collect(activity);
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}

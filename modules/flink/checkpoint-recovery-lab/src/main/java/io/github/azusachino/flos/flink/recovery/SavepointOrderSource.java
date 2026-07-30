package io.github.azusachino.flos.flink.recovery;

import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.apache.flink.streaming.api.functions.source.legacy.RichSourceFunction;

/** A finite checkpointed source that can hold revision A open while a savepoint is requested. */
public final class SavepointOrderSource extends RichSourceFunction<OrderEvent>
        implements ListCheckpointed<Integer> {

    private static volatile CountDownLatch paused = new CountDownLatch(1);

    private final List<OrderEvent> events;
    private final int pauseAfterEvents;
    private final boolean pause;

    private volatile boolean running = true;
    private int nextEventIndex;

    public SavepointOrderSource(List<OrderEvent> events, int pauseAfterEvents, boolean pause) {
        this.events = List.copyOf(events);
        this.pauseAfterEvents = pauseAfterEvents;
        this.pause = pause;
    }

    static void resetProbe() {
        paused = new CountDownLatch(1);
    }

    static boolean awaitPause(long timeout, TimeUnit unit) throws InterruptedException {
        return paused.await(timeout, unit);
    }

    @Override
    public void run(SourceContext<OrderEvent> context) throws InterruptedException {
        while (running && nextEventIndex < events.size()) {
            if (pause && nextEventIndex == pauseAfterEvents) {
                paused.countDown();
                while (running) {
                    Thread.sleep(5);
                }
                return;
            }

            synchronized (context.getCheckpointLock()) {
                context.collect(events.get(nextEventIndex));
                nextEventIndex++;
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
    }

    @Override
    public List<Integer> snapshotState(long checkpointId, long timestamp) {
        return Collections.singletonList(nextEventIndex);
    }

    @Override
    public void restoreState(List<Integer> state) {
        if (state.size() != 1) {
            throw new IllegalStateException("expected exactly one source offset");
        }
        nextEventIndex = state.get(0);
    }
}

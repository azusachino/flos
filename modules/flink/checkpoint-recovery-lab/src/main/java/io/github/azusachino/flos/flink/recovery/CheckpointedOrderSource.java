package io.github.azusachino.flos.flink.recovery;

import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.util.Collections;
import java.util.List;
import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.apache.flink.streaming.api.functions.source.legacy.RichSourceFunction;

/**
 * A finite source that fails once, but only after Flink has completed a checkpoint containing the
 * first part of its input.
 */
public final class CheckpointedOrderSource extends RichSourceFunction<OrderEvent>
        implements ListCheckpointed<Integer>, CheckpointListener {

    private final List<OrderEvent> events;
    private final int failureAfterEvents;

    private volatile boolean running = true;
    private volatile boolean checkpointCompleted;
    private int nextEventIndex;

    public CheckpointedOrderSource(List<OrderEvent> events, int failureAfterEvents) {
        if (failureAfterEvents <= 0 || failureAfterEvents >= events.size()) {
            throw new IllegalArgumentException("failure must split the input");
        }
        this.events = List.copyOf(events);
        this.failureAfterEvents = failureAfterEvents;
    }

    @Override
    public void run(SourceContext<OrderEvent> context) throws Exception {
        boolean firstAttempt = getRuntimeContext().getTaskInfo().getAttemptNumber() == 0;

        while (running && nextEventIndex < events.size()) {
            if (firstAttempt && nextEventIndex == failureAfterEvents) {
                awaitCompletedCheckpoint();
                throw new ArtificialFailureException(
                        "failure after checkpointed event " + nextEventIndex);
            }

            synchronized (context.getCheckpointLock()) {
                context.collect(events.get(nextEventIndex));
                nextEventIndex++;
            }
        }
    }

    private void awaitCompletedCheckpoint() throws InterruptedException {
        while (running && !checkpointCompleted) {
            Thread.sleep(5);
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

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        checkpointCompleted = true;
    }

    private static final class ArtificialFailureException extends Exception {

        private ArtificialFailureException(String message) {
            super(message);
        }
    }
}

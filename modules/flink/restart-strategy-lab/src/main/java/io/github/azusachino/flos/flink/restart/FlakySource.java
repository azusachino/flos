package io.github.azusachino.flos.flink.restart;

import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.util.Collections;
import java.util.List;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.apache.flink.streaming.api.functions.source.legacy.RichSourceFunction;

/**
 * A finite source that fails on every execution attempt before {@code succeedOnAttempt}, then
 * runs to completion. Pass a negative {@code succeedOnAttempt} for a source that never succeeds,
 * to prove a restart strategy's give-up behavior instead of its recovery behavior.
 */
public final class FlakySource extends RichSourceFunction<OrderEvent>
        implements ListCheckpointed<Integer> {

    private final List<OrderEvent> events;
    private final int succeedOnAttempt;

    private volatile boolean running = true;
    private int nextEventIndex;

    public FlakySource(List<OrderEvent> events, int succeedOnAttempt) {
        this.events = List.copyOf(events);
        this.succeedOnAttempt = succeedOnAttempt;
    }

    @Override
    public void run(SourceContext<OrderEvent> context) throws Exception {
        int attempt = getRuntimeContext().getTaskInfo().getAttemptNumber();
        if (succeedOnAttempt < 0 || attempt < succeedOnAttempt) {
            throw new ArtificialFailureException("attempt " + attempt + " fails on purpose");
        }

        while (running && nextEventIndex < events.size()) {
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

    private static final class ArtificialFailureException extends Exception {

        private ArtificialFailureException(String message) {
            super(message);
        }
    }
}

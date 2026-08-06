package io.github.azusachino.flos.flink.slotsharing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.flink.streaming.api.functions.source.legacy.RichParallelSourceFunction;

/**
 * Runs indefinitely once started, signalling a shared latch as each subtask starts. The lab's
 * slot-count arithmetic is about slots held <em>concurrently</em>: a source that finishes
 * quickly would let the scheduler free and reuse its slots for a later slot sharing group,
 * silently hiding a real concurrent shortage. Blocking until cancelled keeps every started
 * subtask's slot occupied for as long as the test needs to observe contention.
 */
final class SubtaskIndexSource extends RichParallelSourceFunction<Long> {

    private static volatile CountDownLatch startedLatch = new CountDownLatch(0);

    private volatile boolean running = true;

    static void resetProbe(int parallelism) {
        startedLatch = new CountDownLatch(parallelism);
    }

    static boolean awaitAllRunning(long timeout, TimeUnit unit) throws InterruptedException {
        return startedLatch.await(timeout, unit);
    }

    @Override
    public void run(SourceContext<Long> context) throws InterruptedException {
        startedLatch.countDown();
        while (running) {
            Thread.sleep(20);
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}

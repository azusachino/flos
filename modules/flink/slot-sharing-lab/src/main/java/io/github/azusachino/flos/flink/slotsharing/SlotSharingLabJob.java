package io.github.azusachino.flos.flink.slotsharing;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.JobManagerOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class SlotSharingLabJob {

    public static final String ISOLATED_SINK_GROUP = "isolated-sink";

    private SlotSharingLabJob() {}

    public static StreamExecutionEnvironment createEnvironment(int parallelism, int taskSlots) {
        var configuration = new Configuration();
        configuration.set(TaskManagerOptions.NUM_TASK_SLOTS, taskSlots);
        // Flink's real slot-pool wait defaults to 5 minutes. A lab that proves a
        // scheduling failure needs that failure to surface in test time, not in
        // CI-timeout time.
        configuration.set(JobManagerOptions.SLOT_REQUEST_TIMEOUT, Duration.ofSeconds(3));
        return StreamExecutionEnvironment.createLocalEnvironment(parallelism, configuration);
    }

    public static JobClient submit(
            StreamExecutionEnvironment env, int parallelism, boolean isolateSink) throws Exception {
        var source =
                env.addSource(new SubtaskIndexSource())
                        .setParallelism(parallelism)
                        .name("subtask-index-source");
        var doubled = source.map(value -> value * 2).setParallelism(parallelism).name("double");
        DataStreamSink<Long> sink = doubled.print().setParallelism(parallelism).name("sink");
        if (isolateSink) {
            sink.slotSharingGroup(ISOLATED_SINK_GROUP);
        }

        return env.executeAsync("flos-slot-sharing-lab");
    }

    public static void main(String[] args) throws Exception {
        var env = createEnvironment(2, 2);
        SubtaskIndexSource.resetProbe(2);
        var jobClient = submit(env, 2, false);

        SubtaskIndexSource.awaitAllRunning(10, TimeUnit.SECONDS);
        System.out.println(
                "Both subtasks running concurrently on 2 slots via the default slot sharing group.");
        Thread.sleep(1_000);
        jobClient.cancel().get();
    }
}

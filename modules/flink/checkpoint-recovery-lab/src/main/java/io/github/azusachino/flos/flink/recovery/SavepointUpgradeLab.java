package io.github.azusachino.flos.flink.recovery;

import io.github.azusachino.flos.flink.eventtime.FeeAggregate;
import io.github.azusachino.flos.flink.eventtime.FeeWindow;
import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.time.Duration;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.StateRecoveryOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

public final class SavepointUpgradeLab {

    static final String SOURCE_UID = "billing-order-source-v1";
    static final String EVENT_TIME_UID = "billing-event-time-v1";
    static final String WINDOW_UID = "billing-five-minute-fee-v1";

    private SavepointUpgradeLab() {}

    static StreamExecutionEnvironment createEnvironment(
            int parallelism, String savepointPath) {
        var configuration = new Configuration();
        if (savepointPath != null) {
            configuration.set(StateRecoveryOptions.SAVEPOINT_PATH, savepointPath);
        }

        var environment = StreamExecutionEnvironment.getExecutionEnvironment(configuration);
        environment.setParallelism(parallelism);
        environment.setMaxParallelism(16);
        environment.enableCheckpointing(50);
        return environment;
    }

    static DataStream<UpgradeResult> buildRevision(
            StreamExecutionEnvironment environment, boolean pauseForSavepoint, String revision) {
        WatermarkStrategy<OrderEvent> watermarks =
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                        .withTimestampAssigner(
                                (event, previousTimestamp) -> event.occurredAt().toEpochMilli());

        return environment
                .addSource(
                        new SavepointOrderSource(
                                CheckpointRecoveryLabJob.events(),
                                CheckpointRecoveryLabJob.FAILURE_AFTER_EVENTS,
                                pauseForSavepoint),
                        "savepoint-order-source")
                .setParallelism(1)
                .uid(SOURCE_UID)
                .assignTimestampsAndWatermarks(watermarks)
                .uid(EVENT_TIME_UID)
                .keyBy(OrderEvent::customerId)
                .window(TumblingEventTimeWindows.of(CheckpointRecoveryLabJob.WINDOW_SIZE))
                .aggregate(new FeeAggregate(), new FeeWindow())
                .uid(WINDOW_UID)
                .map(new RevisionMarker(revision))
                .uid("billing-report-revision-" + revision);
    }
}

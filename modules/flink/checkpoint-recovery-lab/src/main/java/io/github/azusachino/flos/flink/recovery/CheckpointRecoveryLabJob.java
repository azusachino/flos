package io.github.azusachino.flos.flink.recovery;

import io.github.azusachino.flos.flink.eventtime.FeeAggregate;
import io.github.azusachino.flos.flink.eventtime.FeeReport;
import io.github.azusachino.flos.flink.eventtime.FeeWindow;
import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

public final class CheckpointRecoveryLabJob {

    static final Duration WINDOW_SIZE = Duration.ofMinutes(5);
    static final Duration MAX_OUT_OF_ORDERNESS = Duration.ofSeconds(1);
    static final int FAILURE_AFTER_EVENTS = 3;

    private CheckpointRecoveryLabJob() {}

    public static void main(String[] args) throws Exception {
        var environment = createEnvironment();
        buildReports(environment).print();
        environment.execute("flos-checkpoint-recovery-lab");
    }

    static StreamExecutionEnvironment createEnvironment() {
        var configuration = new Configuration();
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 1);
        configuration.set(
                RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ZERO);

        var environment = StreamExecutionEnvironment.getExecutionEnvironment(configuration);
        environment.setParallelism(1);
        environment.setMaxParallelism(16);
        environment.enableCheckpointing(20);
        environment.getCheckpointConfig().setCheckpointTimeout(10_000);
        return environment;
    }

    static DataStream<FeeReport> buildReports(StreamExecutionEnvironment environment) {
        var watermarks =
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(MAX_OUT_OF_ORDERNESS)
                        .withTimestampAssigner(
                                (event, previousTimestamp) -> event.occurredAt().toEpochMilli());

        return environment
                .addSource(
                        new CheckpointedOrderSource(events(), FAILURE_AFTER_EVENTS),
                        "checkpointed-order-source")
                .uid("billing-order-source-v1")
                .assignTimestampsAndWatermarks(watermarks)
                .uid("billing-event-time-v1")
                .keyBy(OrderEvent::customerId)
                .window(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .aggregate(new FeeAggregate(), new FeeWindow())
                .uid("billing-five-minute-fee-v1");
    }

    static List<OrderEvent> events() {
        return List.of(
                event(0, 104, "10.00", "2026-07-30T12:00:40Z"),
                event(1, 201, "4.00", "2026-07-30T12:02:10Z"),
                event(0, 105, "7.50", "2026-07-30T12:03:00Z"),
                event(0, 106, "5.00", "2026-07-30T12:04:50Z"));
    }

    private static OrderEvent event(
            int partition, long sequence, String fee, String occurredAt) {
        return new OrderEvent(
                "alice",
                partition,
                sequence,
                new BigDecimal(fee),
                Instant.parse(occurredAt));
    }
}

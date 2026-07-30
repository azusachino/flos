package io.github.azusachino.flos.flink.eventtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

public final class EventTimeLabJob {

    static final Duration WINDOW_SIZE = Duration.ofMinutes(5);
    static final Duration MAX_OUT_OF_ORDERNESS = Duration.ofSeconds(30);

    private EventTimeLabJob() {}

    public static void main(String[] args) throws Exception {
        var environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setParallelism(1);

        var watermarks =
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(MAX_OUT_OF_ORDERNESS)
                        .withTimestampAssigner(
                                (event, previousTimestamp) -> event.occurredAt().toEpochMilli());

        environment
                .fromData(
                        event(0, 104, "alice", "10.00", "2026-07-30T12:00:40Z"),
                        event(0, 106, "alice", "5.00", "2026-07-30T12:04:50Z"),
                        event(0, 105, "alice", "7.50", "2026-07-30T12:03:00Z"),
                        event(1, 201, "bob", "4.00", "2026-07-30T12:02:10Z"),
                        event(0, 107, "alice", "3.00", "2026-07-30T12:05:31Z"))
                .assignTimestampsAndWatermarks(watermarks)
                .keyBy(OrderEvent::customerId)
                .window(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .aggregate(new FeeAggregate(), new FeeWindow())
                .print();

        environment.execute("flos-event-time-concept-lab");
    }

    private static OrderEvent event(
            int partition, long sequence, String customer, String fee, String occurredAt) {
        return new OrderEvent(
                customer,
                partition,
                sequence,
                new BigDecimal(fee),
                Instant.parse(occurredAt));
    }
}

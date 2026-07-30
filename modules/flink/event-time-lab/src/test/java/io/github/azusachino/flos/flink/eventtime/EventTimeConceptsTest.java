package io.github.azusachino.flos.flink.eventtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.flink.api.common.eventtime.BoundedOutOfOrdernessWatermarks;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkOutput;
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.junit.jupiter.api.Test;

final class EventTimeConceptsTest {

    @Test
    void assignsClockAlignedNonOverlappingWindows() {
        var assigner = TumblingEventTimeWindows.of(EventTimeLabJob.WINDOW_SIZE);
        var context =
                new WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return 0;
                    }
                };

        var beforeBoundary =
                assigner.assignWindows(
                        null, timestamp("2026-07-30T12:04:59.999Z"), context);
        var atBoundary =
                assigner.assignWindows(null, timestamp("2026-07-30T12:05:00Z"), context);

        assertThat(beforeBoundary).singleElement().satisfies(window -> {
            assertThat(window.getStart()).isEqualTo(timestamp("2026-07-30T12:00:00Z"));
            assertThat(window.getEnd()).isEqualTo(timestamp("2026-07-30T12:05:00Z"));
        });
        assertThat(atBoundary).singleElement().satisfies(window -> {
            assertThat(window.getStart()).isEqualTo(timestamp("2026-07-30T12:05:00Z"));
            assertThat(window.getEnd()).isEqualTo(timestamp("2026-07-30T12:10:00Z"));
        });
    }

    @Test
    void aggregatesFeesInArrivalOrderWithoutDependingOnTimestampOrder() {
        var aggregate = new FeeAggregate();
        var accumulator = aggregate.createAccumulator();

        accumulator = aggregate.add(event(106, "5.00", "2026-07-30T12:04:50Z"), accumulator);
        accumulator = aggregate.add(event(105, "7.50", "2026-07-30T12:03:00Z"), accumulator);

        assertThat(accumulator.totalFee()).isEqualByComparingTo("12.50");
        assertThat(accumulator.eventCount()).isEqualTo(2);
    }

    @Test
    void holdsTheWatermarkBehindTheGreatestObservedEventTime() {
        var generator =
                new BoundedOutOfOrdernessWatermarks<OrderEvent>(
                        EventTimeLabJob.MAX_OUT_OF_ORDERNESS);
        var output = new CapturingWatermarkOutput();

        generator.onEvent(null, timestamp("2026-07-30T12:04:50Z"), output);
        generator.onEvent(null, timestamp("2026-07-30T12:03:00Z"), output);
        generator.onPeriodicEmit(output);

        assertThat(output.watermark.getTimestamp())
                .isEqualTo(timestamp("2026-07-30T12:04:19.999Z"));
    }

    private static OrderEvent event(long sequence, String fee, String occurredAt) {
        return new OrderEvent(
                "alice", 0, sequence, new BigDecimal(fee), Instant.parse(occurredAt));
    }

    private static long timestamp(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static final class CapturingWatermarkOutput implements WatermarkOutput {

        private Watermark watermark;

        @Override
        public void emitWatermark(Watermark watermark) {
            this.watermark = watermark;
        }

        @Override
        public void markIdle() {}

        @Override
        public void markActive() {}
    }
}

package io.github.azusachino.flos.flink.eventtime;

import java.time.Instant;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public final class FeeWindow
        extends ProcessWindowFunction<FeeAccumulator, FeeReport, String, TimeWindow> {

    @Override
    public void process(
            String customerId,
            Context context,
            Iterable<FeeAccumulator> accumulators,
            Collector<FeeReport> output) {
        var accumulator = accumulators.iterator().next();
        output.collect(
                new FeeReport(
                        customerId,
                        Instant.ofEpochMilli(context.window().getStart()),
                        Instant.ofEpochMilli(context.window().getEnd()),
                        accumulator.totalFee(),
                        accumulator.eventCount()));
    }
}

package io.github.azusachino.flos.flink.restart;

import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class RestartStrategyLabJob {

    private RestartStrategyLabJob() {}

    public static StreamExecutionEnvironment createEnvironment(Configuration restartConfig) {
        var env = StreamExecutionEnvironment.getExecutionEnvironment(restartConfig);
        env.setParallelism(1);
        return env;
    }

    public static DataStream<OrderEvent> build(
            StreamExecutionEnvironment env, List<OrderEvent> events, int succeedOnAttempt) {
        return env.addSource(new FlakySource(events, succeedOnAttempt));
    }

    public static Configuration fixedDelay(int attempts, Duration delay) {
        var configuration = new Configuration();
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, attempts);
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, delay);
        return configuration;
    }

    public static Configuration failureRate(
            int maxFailuresPerInterval, Duration interval, Duration delay) {
        var configuration = new Configuration();
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "failure-rate");
        configuration.set(
                RestartStrategyOptions.RESTART_STRATEGY_FAILURE_RATE_MAX_FAILURES_PER_INTERVAL,
                maxFailuresPerInterval);
        configuration.set(
                RestartStrategyOptions.RESTART_STRATEGY_FAILURE_RATE_FAILURE_RATE_INTERVAL, interval);
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FAILURE_RATE_DELAY, delay);
        return configuration;
    }

    public static Configuration noRestart() {
        var configuration = new Configuration();
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "none");
        return configuration;
    }

    static List<OrderEvent> events() {
        return List.of(
                event(0, 104, "10.00", "2026-07-30T12:00:40Z"),
                event(0, 105, "7.50", "2026-07-30T12:03:00Z"));
    }

    private static OrderEvent event(int partition, long sequence, String fee, String occurredAt) {
        return new OrderEvent(
                "alice", partition, sequence, new BigDecimal(fee), Instant.parse(occurredAt));
    }

    public static void main(String[] args) throws Exception {
        var env = createEnvironment(fixedDelay(3, Duration.ofMillis(200)));
        build(env, events(), 2).print();
        env.execute("flos-restart-strategy-lab");
    }
}

package io.github.azusachino.flos.flink.clickhouse;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import org.apache.flink.connector.clickhouse.convertor.ClickHouseConvertor;
import org.apache.flink.connector.clickhouse.sink.ClickHouseAsyncSink;
import org.apache.flink.connector.clickhouse.sink.ClickHouseClientConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.ParameterTool;

public final class ClickHouseSinkLabJob {

    static final int MAX_BATCH_SIZE = 100;
    static final int MAX_IN_FLIGHT_REQUESTS = 2;
    static final int MAX_BUFFERED_REQUESTS = 500;
    static final long MAX_BATCH_SIZE_IN_BYTES = 1L * 1024 * 1024;
    static final long MAX_TIME_IN_BUFFER_MS = 1_000;
    static final long MAX_RECORD_SIZE_IN_BYTES = 64L * 1024;

    private ClickHouseSinkLabJob() {}

    public static void main(String[] args) throws Exception {
        ParameterTool parameters = ParameterTool.fromArgs(args);
        var config =
                new ClickHouseClientConfig(
                        parameters.get("url", "http://localhost:18123"),
                        parameters.get("username", "default"),
                        parameters.get("password", ""),
                        parameters.get("database", "learning"),
                        parameters.get("table", "sink_events"));
        var mapper = new ClickHouseSinkEventMapper();
        var converter = new ClickHouseConvertor<>(ClickHouseSinkEvent.class, mapper);
        var sink =
                ClickHouseAsyncSink.<ClickHouseSinkEvent>builder()
                        .setElementConverter(converter)
                        .setMaxBatchSize(MAX_BATCH_SIZE)
                        .setMaxInFlightRequests(MAX_IN_FLIGHT_REQUESTS)
                        .setMaxBufferedRequests(MAX_BUFFERED_REQUESTS)
                        .setMaxBatchSizeInBytes(MAX_BATCH_SIZE_IN_BYTES)
                        .setMaxTimeInBufferMS(MAX_TIME_IN_BUFFER_MS)
                        .setMaxRecordSizeInBytes(MAX_RECORD_SIZE_IN_BYTES)
                        .setClickHouseClientConfig(config)
                        .build();

        var environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setParallelism(1);
        environment.fromData(events(parameters.getInt("records", 5))).sinkTo(sink);
        environment.execute("flos-clickhouse-sink-lab");

        // The connector version used by this lab leaves its client executor
        // alive after a bounded execution. This is a standalone CLI, so exit
        // only after Flink has reported successful completion.
        System.exit(0);
    }

    static List<ClickHouseSinkEvent> events(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        index ->
                                new ClickHouseSinkEvent(
                                        "event-" + index,
                                        ZonedDateTime.parse("2026-08-01T00:00:0" + index + "Z"),
                                        index % 2 == 0 ? "customer-a" : "customer-b",
                                        index % 2 == 0 ? "BTC" : "ETH",
                                        BigDecimal.valueOf(index + 1L, 2)))
                .toList();
    }
}

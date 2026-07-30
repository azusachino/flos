package io.github.azusachino.flos.flink.pipeline;

import io.github.azusachino.flos.flink.eventtime.FeeAggregate;
import io.github.azusachino.flos.flink.eventtime.FeeReport;
import io.github.azusachino.flos.flink.eventtime.FeeWindow;
import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.sql.Timestamp;
import java.time.Duration;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

public final class BillingPipelineJob {

    public static final String JOB_NAME = "flos-five-minute-billing-pipeline";

    private static final String UPSERT_SQL =
            """
            INSERT INTO fee_reports (
                customer_id, window_start, window_end, total_fee, event_count
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                total_fee = VALUES(total_fee),
                event_count = VALUES(event_count)
            """;

    private BillingPipelineJob() {}

    public static void main(String[] args) throws Exception {
        var settings = PipelineSettings.fromEnvironment();
        var environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setParallelism(2);
        environment.setMaxParallelism(128);
        environment.enableCheckpointing(5_000);

        var source =
                KafkaSource.<OrderEvent>builder()
                        .setBootstrapServers(settings.getKafkaBootstrapServers())
                        .setTopics(settings.getKafkaTopic())
                        .setGroupId(settings.getKafkaGroupId())
                        .setStartingOffsets(OffsetsInitializer.earliest())
                        .setDeserializer(new OrderEventKafkaDeserializationSchema())
                        .build();

        var watermarks =
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(
                                Duration.ofSeconds(30))
                        .withTimestampAssigner(
                                (event, previousTimestamp) -> event.occurredAt().toEpochMilli())
                        .withIdleness(Duration.ofSeconds(3));

        environment
                .fromSource(source, watermarks, "kafka-billing-order-source")
                .uid("billing-kafka-source-v1")
                .keyBy(OrderEvent::customerId)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(5)))
                .aggregate(new FeeAggregate(), new FeeWindow())
                .uid("billing-five-minute-fee-v1")
                .sinkTo(reportSink(settings))
                .uid("billing-mysql-report-sink-v1");

        environment.execute(JOB_NAME);
    }

    private static org.apache.flink.api.connector.sink2.Sink<FeeReport> reportSink(
            PipelineSettings settings) {
        return JdbcSink.<FeeReport>builder()
                .withQueryStatement(
                        UPSERT_SQL,
                        (statement, report) -> {
                            statement.setString(1, report.customerId());
                            statement.setTimestamp(2, Timestamp.from(report.windowStart()));
                            statement.setTimestamp(3, Timestamp.from(report.windowEnd()));
                            statement.setBigDecimal(4, report.totalFee());
                            statement.setLong(5, report.eventCount());
                        })
                .withExecutionOptions(
                        JdbcExecutionOptions.builder()
                                .withBatchSize(16)
                                .withBatchIntervalMs(500)
                                .withMaxRetries(3)
                                .build())
                .buildAtLeastOnce(
                        new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                .withUrl(settings.getJdbcUrl())
                                .withDriverName("com.mysql.cj.jdbc.Driver")
                                .withUsername(settings.getJdbcUsername())
                                .withPassword(settings.getJdbcPassword())
                                .build());
    }
}

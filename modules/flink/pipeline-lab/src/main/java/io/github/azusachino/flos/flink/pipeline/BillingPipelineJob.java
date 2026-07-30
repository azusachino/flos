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
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.util.OutputTag;

public final class BillingPipelineJob {

    public static final String JOB_NAME = "flos-five-minute-billing-pipeline";
    public static final Duration ALLOWED_LATENESS = Duration.ofMinutes(2);

    private static final OutputTag<OrderEvent> TOO_LATE_EVENTS =
            new OutputTag<>("billing-too-late-events") {};

    private static final String REPORT_UPSERT_SQL =
            """
            INSERT INTO fee_reports (
                customer_id, window_start, window_end, total_fee, event_count
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                total_fee = VALUES(total_fee),
                event_count = VALUES(event_count)
            """;

    private static final String AUDIT_UPSERT_SQL =
            """
            INSERT INTO billing_event_audit (
                source_partition, sequence_number, customer_id, fee, occurred_at
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                customer_id = VALUES(customer_id),
                fee = VALUES(fee),
                occurred_at = VALUES(occurred_at)
            """;

    private static final String TOO_LATE_UPSERT_SQL =
            """
            INSERT INTO billing_too_late_events (
                source_partition, sequence_number, customer_id, fee, occurred_at
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                customer_id = VALUES(customer_id),
                fee = VALUES(fee),
                occurred_at = VALUES(occurred_at)
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

        var orders =
                environment
                .fromSource(source, watermarks, "kafka-billing-order-source")
                .uid("billing-kafka-source-v1");

        orders.sinkTo(eventSink(settings, AUDIT_UPSERT_SQL))
                .uid("billing-mysql-event-audit-sink-v1");

        SingleOutputStreamOperator<FeeReport> reports =
                orders.keyBy(OrderEvent::customerId)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(5)))
                .allowedLateness(ALLOWED_LATENESS)
                .sideOutputLateData(TOO_LATE_EVENTS)
                .aggregate(new FeeAggregate(), new FeeWindow())
                .uid("billing-five-minute-fee-v1");

        reports.sinkTo(reportSink(settings))
                .uid("billing-mysql-report-sink-v1");

        reports.getSideOutput(TOO_LATE_EVENTS)
                .sinkTo(eventSink(settings, TOO_LATE_UPSERT_SQL))
                .uid("billing-mysql-too-late-sink-v1");

        environment.execute(JOB_NAME);
    }

    private static org.apache.flink.api.connector.sink2.Sink<FeeReport> reportSink(
            PipelineSettings settings) {
        return JdbcSink.<FeeReport>builder()
                .withQueryStatement(
                        REPORT_UPSERT_SQL,
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

    private static org.apache.flink.api.connector.sink2.Sink<OrderEvent> eventSink(
            PipelineSettings settings, String sql) {
        return JdbcSink.<OrderEvent>builder()
                .withQueryStatement(
                        sql,
                        (statement, event) -> {
                            statement.setInt(1, event.sourcePartition());
                            statement.setLong(2, event.sequence());
                            statement.setString(3, event.customerId());
                            statement.setBigDecimal(4, event.fee());
                            statement.setTimestamp(5, Timestamp.from(event.occurredAt()));
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

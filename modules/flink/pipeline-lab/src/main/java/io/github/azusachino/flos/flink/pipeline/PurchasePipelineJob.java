package io.github.azusachino.flos.flink.pipeline;

import io.github.azusachino.flos.flink.operators.NormalizePurchase;
import io.github.azusachino.flos.flink.operators.PurchaseEvent;
import io.github.azusachino.flos.flink.operators.RunningSpend;
import io.github.azusachino.flos.flink.operators.ValidPurchaseFilter;
import java.sql.Timestamp;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class PurchasePipelineJob {

    private static final String UPSERT_SQL =
            """
            INSERT INTO customer_spend (customer_id, amount, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = VALUES(amount), updated_at = VALUES(updated_at)
            """;

    private PurchasePipelineJob() {}

    public static void main(String[] args) throws Exception {
        var settings = PipelineSettings.fromEnvironment();
        var environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.enableCheckpointing(10_000);

        var source =
                KafkaSource.<PurchaseEvent>builder()
                        .setBootstrapServers(settings.getKafkaBootstrapServers())
                        .setTopics(settings.getKafkaTopic())
                        .setGroupId(settings.getKafkaGroupId())
                        .setStartingOffsets(OffsetsInitializer.earliest())
                        .setValueOnlyDeserializer(new PurchaseEventDeserializationSchema())
                        .build();

        environment
                .fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-purchase-source")
                .filter(new ValidPurchaseFilter())
                .name("valid-purchases")
                .map(new NormalizePurchase())
                .name("normalize-purchases")
                .keyBy(PurchaseEvent::customerId)
                .reduce(new RunningSpend())
                .name("running-customer-spend")
                .sinkTo(
                        JdbcSink.<PurchaseEvent>builder()
                                .withQueryStatement(
                                        UPSERT_SQL,
                                        (statement, event) -> {
                                            statement.setString(1, event.customerId());
                                            statement.setBigDecimal(2, event.amount());
                                            statement.setTimestamp(
                                                    3, Timestamp.from(event.occurredAt()));
                                        })
                                .withExecutionOptions(
                                        JdbcExecutionOptions.builder()
                                                .withBatchSize(100)
                                                .withBatchIntervalMs(1_000)
                                                .withMaxRetries(3)
                                                .build())
                                .buildAtLeastOnce(
                                        new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                                .withUrl(settings.getJdbcUrl())
                                                .withDriverName("com.mysql.cj.jdbc.Driver")
                                                .withUsername(settings.getJdbcUsername())
                                                .withPassword(settings.getJdbcPassword())
                                                .build()))
                .name("mysql-customer-spend-sink");

        environment.execute("flos-kafka-mysql-pipeline");
    }
}

package io.github.azusachino.flos.flink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

final class OrderEventKafkaDeserializationSchemaTest {

    @Test
    void derivesTheSourcePartitionFromKafkaMetadata() throws Exception {
        var schema = new OrderEventKafkaDeserializationSchema();
        var output = new ArrayList<io.github.azusachino.flos.flink.eventtime.OrderEvent>();
        var record =
                new ConsumerRecord<byte[], byte[]>(
                        "billing-events",
                        15,
                        42,
                        null,
                        """
                        {
                          "customerId": "customer-15",
                          "sequence": 7,
                          "fee": "16.00",
                          "occurredAt": "2026-07-30T12:00:10Z"
                        }
                        """
                                .getBytes(StandardCharsets.UTF_8));

        schema.deserialize(record, collector(output));

        assertThat(output)
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.customerId()).isEqualTo("customer-15");
                            assertThat(event.sourcePartition()).isEqualTo(15);
                            assertThat(event.sequence()).isEqualTo(7);
                            assertThat(event.fee()).isEqualByComparingTo("16.00");
                            assertThat(event.occurredAt())
                                    .isEqualTo(Instant.parse("2026-07-30T12:00:10Z"));
                        });
    }

    private static <T> Collector<T> collector(ArrayList<T> output) {
        return new Collector<>() {
            @Override
            public void collect(T record) {
                output.add(record);
            }

            @Override
            public void close() {}
        };
    }
}

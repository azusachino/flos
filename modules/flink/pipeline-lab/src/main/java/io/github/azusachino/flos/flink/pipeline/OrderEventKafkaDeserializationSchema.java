package io.github.azusachino.flos.flink.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.azusachino.flos.flink.eventtime.OrderEvent;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class OrderEventKafkaDeserializationSchema
        implements KafkaRecordDeserializationSchema<OrderEvent> {

    private transient ObjectMapper objectMapper;

    @Override
    public void deserialize(
            ConsumerRecord<byte[], byte[]> record, Collector<OrderEvent> output)
            throws IOException {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }
        var wireEvent = objectMapper.readValue(record.value(), WireOrderEvent.class);
        output.collect(
                new OrderEvent(
                        wireEvent.customerId(),
                        record.partition(),
                        wireEvent.sequence(),
                        wireEvent.fee(),
                        wireEvent.occurredAt()));
    }

    @Override
    public TypeInformation<OrderEvent> getProducedType() {
        return TypeInformation.of(OrderEvent.class);
    }

    private record WireOrderEvent(
            String customerId, long sequence, BigDecimal fee, Instant occurredAt)
            implements Serializable {}
}

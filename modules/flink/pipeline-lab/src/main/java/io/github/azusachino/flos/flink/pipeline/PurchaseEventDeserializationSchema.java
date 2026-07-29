package io.github.azusachino.flos.flink.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.azusachino.flos.flink.operators.PurchaseEvent;
import java.io.IOException;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;

public final class PurchaseEventDeserializationSchema
        extends AbstractDeserializationSchema<PurchaseEvent> {

    private static final long serialVersionUID = 1L;
    private transient ObjectMapper objectMapper;

    @Override
    public PurchaseEvent deserialize(byte[] message) throws IOException {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }
        return objectMapper.readValue(message, PurchaseEvent.class);
    }
}

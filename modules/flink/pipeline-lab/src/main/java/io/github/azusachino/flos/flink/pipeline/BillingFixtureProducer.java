package io.github.azusachino.flos.flink.pipeline;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

public final class BillingFixtureProducer {

    public static final int PARTITION_COUNT = 16;

    private BillingFixtureProducer() {}

    public static void main(String[] args) throws Exception {
        String bootstrapServers = args.length > 0 ? args[0] : "kafka:9092";
        String topic = args.length > 1 ? args[1] : "billing-events";

        ensureTopic(bootstrapServers, topic);
        publishFixture(bootstrapServers, topic);
        System.out.printf("published billing fixture to %s with %d partitions%n", topic, PARTITION_COUNT);
    }

    private static void ensureTopic(String bootstrapServers, String topic)
            throws ExecutionException, InterruptedException {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers))) {
            if (!admin.listTopics().names().get().contains(topic)) {
                admin.createTopics(
                                List.of(
                                        new NewTopic(
                                                topic,
                                                PARTITION_COUNT,
                                                (short) 1)))
                        .all()
                        .get();
            }

            int actualPartitions =
                    admin.describeTopics(List.of(topic))
                            .allTopicNames()
                            .get()
                            .get(topic)
                            .partitions()
                            .size();
            if (actualPartitions != PARTITION_COUNT) {
                throw new IllegalStateException(
                        "expected %d partitions but found %d"
                                .formatted(PARTITION_COUNT, actualPartitions));
            }
        }
    }

    private static void publishFixture(String bootstrapServers, String topic) {
        var properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("acks", "all");
        properties.put("key.serializer", ByteArraySerializer.class.getName());
        properties.put("value.serializer", ByteArraySerializer.class.getName());

        try (var producer = new KafkaProducer<byte[], byte[]>(properties)) {
            for (int partition = 0; partition < PARTITION_COUNT; partition++) {
                String customer = "customer-%02d".formatted(partition);
                send(
                        producer,
                        topic,
                        partition,
                        customer,
                        event(customer, 1, partition + 1, "2026-07-30T12:00:10Z"));
                if (partition == 0) {
                    send(
                            producer,
                            topic,
                            partition,
                            customer,
                            event(customer, 2, 14, "2026-07-30T12:04:40Z"));
                    send(
                            producer,
                            topic,
                            partition,
                            customer,
                            event(customer, 3, 1, "2026-07-30T12:06:00Z"));
                } else {
                    send(
                            producer,
                            topic,
                            partition,
                            customer,
                            event(customer, 2, 1, "2026-07-30T12:06:00Z"));
                }
            }
            producer.flush();
        }
    }

    private static void send(
            KafkaProducer<byte[], byte[]> producer,
            String topic,
            int partition,
            String key,
            String value) {
        producer.send(
                new ProducerRecord<>(
                        topic,
                        partition,
                        key.getBytes(StandardCharsets.UTF_8),
                        value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String event(
            String customer, long sequence, long fee, String occurredAt) {
        return """
                {"customerId":"%s","sequence":%d,"fee":"%d.00","occurredAt":"%s"}
                """
                .formatted(customer, sequence, fee, occurredAt)
                .strip();
    }
}

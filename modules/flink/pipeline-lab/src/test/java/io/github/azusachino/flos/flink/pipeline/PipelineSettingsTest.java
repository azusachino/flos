package io.github.azusachino.flos.flink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PipelineSettingsTest {

    @Test
    void readsExplicitPipelineConfiguration() {
        var environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "test",
                                Map.of(
                                        "FLOS_KAFKA_BOOTSTRAP", "broker:19092",
                                        "FLOS_KAFKA_TOPIC", "orders",
                                        "FLOS_KAFKA_GROUP_ID", "learning",
                                        "FLOS_JDBC_URL", "jdbc:mysql://db/flos",
                                        "FLOS_JDBC_USERNAME", "reader",
                                        "FLOS_JDBC_PASSWORD", "secret")));

        var settings = PipelineSettings.from(environment);

        assertThat(settings.getKafkaBootstrapServers()).isEqualTo("broker:19092");
        assertThat(settings.getKafkaTopic()).isEqualTo("orders");
        assertThat(settings.getJdbcUrl()).isEqualTo("jdbc:mysql://db/flos");
    }
}

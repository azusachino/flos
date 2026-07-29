package io.github.azusachino.flos.flink.pipeline;

import java.io.Serializable;
import lombok.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

@Value
public class PipelineSettings implements Serializable {

    String kafkaBootstrapServers;
    String kafkaTopic;
    String kafkaGroupId;
    String jdbcUrl;
    String jdbcUsername;
    String jdbcPassword;

    public static PipelineSettings fromEnvironment() {
        return from(new StandardEnvironment());
    }

    static PipelineSettings from(Environment environment) {
        return new PipelineSettings(
                environment.getProperty("FLOS_KAFKA_BOOTSTRAP", "kafka:9092"),
                environment.getProperty("FLOS_KAFKA_TOPIC", "purchase-events"),
                environment.getProperty("FLOS_KAFKA_GROUP_ID", "flos-pipeline"),
                environment.getProperty(
                        "FLOS_JDBC_URL",
                        "jdbc:mysql://mysql:3306/flos?rewriteBatchedStatements=true"),
                environment.getProperty("FLOS_JDBC_USERNAME", "flos"),
                environment.getProperty("FLOS_JDBC_PASSWORD", "flos"));
    }
}

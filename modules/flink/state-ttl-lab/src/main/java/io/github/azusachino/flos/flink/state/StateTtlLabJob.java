package io.github.azusachino.flos.flink.state;

import java.time.Duration;
import java.util.List;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class StateTtlLabJob {

    /** Recognized shortcuts for {@link StateBackendOptions#STATE_BACKEND}. */
    public static final String HASHMAP_BACKEND = "hashmap";

    public static final String ROCKSDB_BACKEND = "rocksdb";

    private StateTtlLabJob() {}

    public static StreamExecutionEnvironment createEnvironment(String stateBackend) {
        var configuration = new Configuration();
        configuration.set(StateBackendOptions.STATE_BACKEND, stateBackend);
        var env = StreamExecutionEnvironment.getExecutionEnvironment(configuration);
        env.setParallelism(1);
        return env;
    }

    public static DataStream<CartSnapshot> build(
            StreamExecutionEnvironment env, List<CartActivity> activities, StateTtlConfig ttlConfig) {
        return env.addSource(new CartActivitySource(activities))
                .keyBy(CartActivity::customerId)
                .process(new CartTotalFunction(ttlConfig));
    }

    public static void main(String[] args) throws Exception {
        var env = createEnvironment(HASHMAP_BACKEND);

        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(300))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();

        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.purchase("alice", "15.00", 50),
                        CartActivity.purchase("alice", "5.00", 500));

        build(env, activities, ttlConfig).print();
        env.execute("flos-state-ttl-concept-lab");
    }
}

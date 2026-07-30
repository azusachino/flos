package io.github.azusachino.flos.flink.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.junit.jupiter.api.Test;

class StateTtlLabTest {

    @Test
    void accumulatesWithoutCrossingTtl() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofSeconds(5))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.purchase("alice", "15.00", 20));

        var results = run(activities, ttlConfig);

        assertThat(results)
                .containsExactly(
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, true),
                        new CartSnapshot("alice", new BigDecimal("25.00"), 2, false));
    }

    @Test
    void stateExpiresAfterTtlUnderNeverReturnExpired() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(150))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.purchase("alice", "5.00", 400));

        var results = run(activities, ttlConfig);

        assertThat(results)
                .containsExactly(
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, true),
                        new CartSnapshot("alice", new BigDecimal("5.00"), 1, true));
    }

    @Test
    void probeDoesNotExtendTtlUnderOnCreateAndWrite() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(400))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.probe("alice", 100),
                        CartActivity.purchase("alice", "3.00", 350));

        var results = run(activities, ttlConfig);

        // The probe 100ms after the write still sees the live cart (100ms < 400ms TTL), but does
        // not refresh its clock. By the purchase 350ms after that, 450ms have elapsed since the
        // original write, past the 400ms TTL, so the cart is treated as expired.
        assertThat(results)
                .containsExactly(
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, true),
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, false),
                        new CartSnapshot("alice", new BigDecimal("3.00"), 1, true));
    }

    @Test
    void probeExtendsTtlUnderOnReadAndWrite() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(400))
                        .updateTtlOnReadAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.probe("alice", 100),
                        CartActivity.purchase("alice", "3.00", 350));

        var results = run(activities, ttlConfig);

        // Identical schedule and TTL to the OnCreateAndWrite test above, but the probe now
        // refreshes the clock. The purchase arrives only 350ms after that refresh, still under
        // the 400ms TTL, so the cart survives and accumulates.
        assertThat(results)
                .containsExactly(
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, true),
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, false),
                        new CartSnapshot("alice", new BigDecimal("13.00"), 2, false));
    }

    @Test
    void hashMapAndRocksDbBackendsProduceIdenticalOutput() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(150))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.purchase("alice", "5.00", 400));

        var hashMapResults =
                StateTtlLabJob.build(
                                StateTtlLabJob.createEnvironment(StateTtlLabJob.HASHMAP_BACKEND),
                                activities,
                                ttlConfig)
                        .executeAndCollect("state-ttl-hashmap", activities.size());
        var rocksDbResults =
                StateTtlLabJob.build(
                                StateTtlLabJob.createEnvironment(StateTtlLabJob.ROCKSDB_BACKEND),
                                activities,
                                ttlConfig)
                        .executeAndCollect("state-ttl-rocksdb", activities.size());

        assertThat(rocksDbResults).containsExactlyElementsOf(hashMapResults);
    }

    private static List<CartSnapshot> run(List<CartActivity> activities, StateTtlConfig ttlConfig)
            throws Exception {
        var env = StateTtlLabJob.createEnvironment(StateTtlLabJob.HASHMAP_BACKEND);
        return StateTtlLabJob.build(env, activities, ttlConfig)
                .executeAndCollect("state-ttl-test", activities.size());
    }
}

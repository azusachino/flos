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
    void probeOnAnUnknownCustomerNeverCreatesState() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofSeconds(5))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.probe("charlie", 0), CartActivity.purchase("charlie", "7.00", 0));

        var results = run(activities, ttlConfig);

        // The probe finds nothing and, critically, calls value() only -- never update() -- so it
        // must leave no trace. The purchase right after still starts a brand new cart rather than
        // continuing from some phantom state the probe might have created.
        assertThat(results)
                .containsExactly(
                        new CartSnapshot("charlie", BigDecimal.ZERO, 0, true),
                        new CartSnapshot("charlie", new BigDecimal("7.00"), 1, true));
    }

    @Test
    void keyedStateIsIsolatedAcrossCustomers() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(300))
                        .updateTtlOnCreateAndWrite()
                        .neverReturnExpired()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.purchase("bob", "20.00", 100),
                        // 350ms after alice's write, but only 250ms after bob's.
                        CartActivity.purchase("alice", "3.00", 250),
                        CartActivity.probe("bob", 0));

        var results = run(activities, ttlConfig);

        // Alice's own 300ms TTL has elapsed (350ms since her write) despite bob's unrelated write
        // in between -- his activity does not refresh her clock. Bob's own state is still well
        // within his 300ms TTL (250ms since his write) despite alice's write and expiry around it.
        assertThat(results)
                .containsExactly(
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, true),
                        new CartSnapshot("bob", new BigDecimal("20.00"), 1, true),
                        new CartSnapshot("alice", new BigDecimal("3.00"), 1, true),
                        new CartSnapshot("bob", new BigDecimal("20.00"), 1, false));
    }

    @Test
    void returnExpiredIfNotCleanedUpKeepsStaleValueVisibleToTheApplication() throws Exception {
        var ttlConfig =
                StateTtlConfig.newBuilder(Duration.ofMillis(150))
                        .updateTtlOnCreateAndWrite()
                        .returnExpiredIfNotCleanedUp()
                        .build();
        var activities =
                List.of(
                        CartActivity.purchase("alice", "10.00", 0),
                        CartActivity.purchase("alice", "5.00", 400));

        var results = run(activities, ttlConfig);

        // The 150ms TTL has elapsed by the second purchase (400ms later), exactly as in
        // stateExpiresAfterTtlUnderNeverReturnExpired. But value() keeps returning the stale
        // CartTotal instead of null until physical cleanup runs, and CartTotalFunction has no
        // other way to tell the two cases apart: it only ever asks "is current null?" So this
        // purchase is wrongly treated as a continuation, accumulating onto a cart that should
        // have reset. ValueState<T>'s public API exposes no expiry flag alongside the value --
        // ReturnExpiredIfNotCleanedUp is a trade a caller makes deliberately, not a free one.
        assertThat(results)
                .containsExactly(
                        new CartSnapshot("alice", new BigDecimal("10.00"), 1, true),
                        new CartSnapshot("alice", new BigDecimal("15.00"), 2, false));
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

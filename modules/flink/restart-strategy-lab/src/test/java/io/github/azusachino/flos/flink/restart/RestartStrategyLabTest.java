package io.github.azusachino.flos.flink.restart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RestartStrategyLabTest {

    @Test
    void noRestartStrategyFailsOnTheFirstAttempt() {
        var env =
                RestartStrategyLabJob.createEnvironment(RestartStrategyLabJob.noRestart());
        var stream =
                RestartStrategyLabJob.build(env, RestartStrategyLabJob.events(), 1);

        // succeedOnAttempt=1 means attempt 0 fails on purpose. With no restart strategy
        // configured, Flink never tries attempt 1 -- the job fails immediately.
        assertThatThrownBy(() -> stream.executeAndCollect("no-restart", 2))
                .isInstanceOf(RuntimeException.class)
                .rootCause().hasMessageContaining("fails on purpose");
    }

    @Test
    void fixedDelayRecoversWithinConfiguredAttempts() throws Exception {
        var env =
                RestartStrategyLabJob.createEnvironment(
                        RestartStrategyLabJob.fixedDelay(3, Duration.ofMillis(50)));
        var stream =
                RestartStrategyLabJob.build(env, RestartStrategyLabJob.events(), 2);

        // Attempts 0 and 1 fail on purpose; attempt 2 succeeds, well within the 3 configured
        // retries, so the job completes and emits both events.
        var results = stream.executeAndCollect("fixed-delay-recovers", 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void fixedDelayGivesUpAfterExhaustingAttempts() {
        var env =
                RestartStrategyLabJob.createEnvironment(
                        RestartStrategyLabJob.fixedDelay(2, Duration.ofMillis(50)));
        var stream = RestartStrategyLabJob.build(env, RestartStrategyLabJob.events(), -1);

        // The source never succeeds. With only 2 configured retries (3 total attempts: the
        // original try plus 2 retries), the job exhausts them and is declared failed rather
        // than retrying forever.
        assertThatThrownBy(() -> stream.executeAndCollect("fixed-delay-gives-up", 2))
                .isInstanceOf(RuntimeException.class)
                .rootCause().hasMessageContaining("fails on purpose");
    }

    @Test
    void failureRateGivesUpAfterExceedingTheRate() {
        var env =
                RestartStrategyLabJob.createEnvironment(
                        RestartStrategyLabJob.failureRate(
                                2, Duration.ofSeconds(10), Duration.ofMillis(20)));
        var stream = RestartStrategyLabJob.build(env, RestartStrategyLabJob.events(), -1);

        // The source never succeeds. Once more than 2 failures land inside the 10-second
        // measurement window, the failure-rate strategy stops retrying and the job fails --
        // a different give-up trigger than fixed-delay's attempt count, but the same safety net.
        assertThatThrownBy(() -> stream.executeAndCollect("failure-rate-gives-up", 2))
                .isInstanceOf(RuntimeException.class)
                .rootCause().hasMessageContaining("fails on purpose");
    }
}

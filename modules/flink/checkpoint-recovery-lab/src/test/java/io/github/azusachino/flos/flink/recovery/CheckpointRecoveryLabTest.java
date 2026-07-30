package io.github.azusachino.flos.flink.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

final class CheckpointRecoveryLabTest {

    @Test
    void restoresTheSourceAndWindowStateAfterAnInjectedFailure() throws Exception {
        var environment = CheckpointRecoveryLabJob.createEnvironment();

        var reports =
                CheckpointRecoveryLabJob.buildReports(environment)
                        .executeAndCollect("checkpoint-recovery-test", 1);

        assertThat(reports)
                .singleElement()
                .satisfies(
                        report -> {
                            assertThat(report.customerId()).isEqualTo("alice");
                            assertThat(report.windowStart())
                                    .isEqualTo(Instant.parse("2026-07-30T12:00:00Z"));
                            assertThat(report.windowEnd())
                                    .isEqualTo(Instant.parse("2026-07-30T12:05:00Z"));
                            assertThat(report.totalFee()).isEqualByComparingTo("26.50");
                            assertThat(report.eventCount()).isEqualTo(4);
                        });
    }
}

package io.github.azusachino.flos.flink.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.flink.core.execution.SavepointFormatType;
import org.junit.jupiter.api.Test;

final class SavepointUpgradeLabTest {

    @Test
    void restoresAnOpenWindowIntoRevisionBAtHigherParallelism() throws Exception {
        Path savepointDirectory = Files.createTempDirectory("flos-savepoint-upgrade-");
        SavepointOrderSource.resetProbe();

        var revisionA = SavepointUpgradeLab.createEnvironment(1, null);
        SavepointUpgradeLab.buildRevision(revisionA, true, "v1").print();
        var revisionAClient = revisionA.executeAsync("billing-revision-a");

        assertThat(SavepointOrderSource.awaitPause(10, TimeUnit.SECONDS)).isTrue();

        String savepointPath =
                revisionAClient
                        .stopWithSavepoint(
                                false,
                                savepointDirectory.toUri().toString(),
                                SavepointFormatType.CANONICAL)
                        .get(20, TimeUnit.SECONDS);

        assertThat(Path.of(URI.create(savepointPath)).resolve("_metadata")).exists();

        var revisionB = SavepointUpgradeLab.createEnvironment(2, savepointPath);
        var results =
                SavepointUpgradeLab.buildRevision(revisionB, false, "v2")
                        .executeAndCollect("billing-revision-b", 1);

        assertThat(results)
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.revision()).isEqualTo("v2");
                            assertThat(result.windowParallelism()).isEqualTo(2);
                            assertThat(result.windowSubtask()).isBetween(0, 1);
                            assertThat(result.report().windowStart())
                                    .isEqualTo(Instant.parse("2026-07-30T12:00:00Z"));
                            assertThat(result.report().windowEnd())
                                    .isEqualTo(Instant.parse("2026-07-30T12:05:00Z"));
                            assertThat(result.report().totalFee())
                                    .isEqualByComparingTo("26.50");
                            assertThat(result.report().eventCount()).isEqualTo(4);
                        });
    }
}

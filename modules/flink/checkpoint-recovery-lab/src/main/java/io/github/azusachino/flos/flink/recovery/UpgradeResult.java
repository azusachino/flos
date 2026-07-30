package io.github.azusachino.flos.flink.recovery;

import io.github.azusachino.flos.flink.eventtime.FeeReport;
import java.io.Serializable;

public record UpgradeResult(
        FeeReport report, String revision, int windowSubtask, int windowParallelism)
        implements Serializable {}

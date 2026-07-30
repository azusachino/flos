package io.github.azusachino.flos.flink.recovery;

import io.github.azusachino.flos.flink.eventtime.FeeReport;
import org.apache.flink.api.common.functions.RichMapFunction;

public final class RevisionMarker extends RichMapFunction<FeeReport, UpgradeResult> {

    private final String revision;

    public RevisionMarker(String revision) {
        this.revision = revision;
    }

    @Override
    public UpgradeResult map(FeeReport report) {
        var task = getRuntimeContext().getTaskInfo();
        return new UpgradeResult(
                report,
                revision,
                task.getIndexOfThisSubtask(),
                task.getNumberOfParallelSubtasks());
    }
}

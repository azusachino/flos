package io.github.azusachino.flos.flink.slotsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import org.apache.flink.runtime.jobmanager.scheduler.NoResourceAvailableException;
import org.junit.jupiter.api.Test;

class SlotSharingLabTest {

    @Test
    void defaultSharingFitsAThreeOperatorChainIntoParallelismManySlots() throws Exception {
        // source, map, and sink are all in Flink's default slot sharing group, so one
        // parallel instance of the whole chain occupies one slot. At parallelism 2 that
        // is 2 slots total, exactly what is available -- both source subtasks reach
        // "running" concurrently, proving they were both actually scheduled at once.
        SubtaskIndexSource.resetProbe(2);
        var env = SlotSharingLabJob.createEnvironment(2, 2);
        var jobClient = SlotSharingLabJob.submit(env, 2, false);

        try {
            assertThat(SubtaskIndexSource.awaitAllRunning(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            jobClient.cancel().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void isolatingTheSinkExhaustsASlotPoolThatFitsDefaultSharing() throws Exception {
        // Pulling the sink into its own slot sharing group means the default group
        // (source + map) and the isolated group (sink) each need their own slot per
        // parallel instance: 2 + 2 = 4 slots. Only 2 are available. Flink's default
        // scheduler deploys an entire pipelined region together, so the source never
        // even starts running -- it is held back by its downstream sink's inability to
        // get a slot, not the other way around. The whole job is declared failed once
        // the slot request times out -- a real scheduling failure, not a mock.
        SubtaskIndexSource.resetProbe(2);
        var env = SlotSharingLabJob.createEnvironment(2, 2);
        var jobClient = SlotSharingLabJob.submit(env, 2, true);

        assertThatThrownBy(() -> jobClient.getJobExecutionResult().get(10, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(NoResourceAvailableException.class);

        assertThat(SubtaskIndexSource.awaitAllRunning(0, TimeUnit.SECONDS))
                .as("source never gets to run when its own pipelined region can't be fully scheduled")
                .isFalse();
    }

    @Test
    void isolatingTheSinkSucceedsWithEnoughSlots() throws Exception {
        // Same isolated grouping, but with 4 slots available for the 2+2 requirement.
        // Proves slotSharingGroup() actually isolates scheduling rather than merely
        // failing -- the same topology that fails above runs here.
        SubtaskIndexSource.resetProbe(2);
        var env = SlotSharingLabJob.createEnvironment(2, 4);
        var jobClient = SlotSharingLabJob.submit(env, 2, true);

        try {
            assertThat(SubtaskIndexSource.awaitAllRunning(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            jobClient.cancel().get(10, TimeUnit.SECONDS);
        }
    }
}

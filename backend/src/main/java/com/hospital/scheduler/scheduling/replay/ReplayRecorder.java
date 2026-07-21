package com.hospital.scheduler.scheduling.replay;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.search.SearchState;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Hooks the search loop into a {@link MoveLog}. One instance per run, lives
 * for the duration of the search and is registered with the
 * {@link MoveLogRegistry} so the replay endpoint can fetch it.
 */
public class ReplayRecorder {

    private final String runId;
    private final MoveLog log;
    private final MoveLogRegistry registry;

    public ReplayRecorder(String runId, MoveLog log, MoveLogRegistry registry) {
        this.runId = runId;
        this.log = log;
        this.registry = registry;
    }

    /**
     * Record a single move attempt. Captures iteration number, elapsed
     * milliseconds, the move's type, affected slot, staff transition, and
     * delta. Always called even for rejected moves.
     */
    public void record(Move move,
                       WorkingSolution solution,
                       SearchState state,
                       ScoreDirector scoreDirector,
                       int hardDelta,
                       double coverageDelta,
                       boolean accepted) {
        int slotId = -1;
        int prevStaffId = -1;
        int newStaffId = -1;
        String moveType = move.getClass().getSimpleName();
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        if (slots != null && slots.length > 0) {
            slotId = slots[0];
            if (slotId >= 0 && slotId < solution.getAssignments().size()) {
                newStaffId = solution.getAssignment(slotId).staffId;
            }
        }
        if (staff != null && staff.length > slots.length) {
            prevStaffId = staff[slots.length];
        }
        ScoreSnapshot snapshot = scoreDirector.getCurrent().toImmutable();
        log.append(new MoveLog.MoveRecord(
                state.getIteration(),
                state.getElapsedMillis(),
                moveType,
                slotId,
                prevStaffId,
                newStaffId,
                hardDelta,
                coverageDelta,
                accepted,
                snapshot));
    }

    public String getRunId() { return runId; }
}

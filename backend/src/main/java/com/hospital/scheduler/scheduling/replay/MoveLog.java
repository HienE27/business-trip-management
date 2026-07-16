package com.hospital.scheduler.scheduling.replay;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Append-only log of {@link MoveRecord} entries produced during a search run.
 * The replay page streams this log to the FE, then walks through it
 * step-by-step to render the solution at each iteration.
 */
public class MoveLog {

    private final List<MoveRecord> records = new ArrayList<>();

    public synchronized void append(MoveRecord r) {
        records.add(r);
    }

    public synchronized List<MoveRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public synchronized int size() {
        return records.size();
    }

    public synchronized void clear() {
        records.clear();
    }

    /**
     * Single entry — captures enough state to reconstruct a solution at this
     * iteration without needing the full history.
     */
    public static final class MoveRecord {
        public final int iteration;
        public final long elapsedMillis;
        public final String moveType;
        public final int slotId;
        public final int previousStaffId;
        public final int newStaffId;
        public final int hardDelta;
        public final double coverageDelta;
        public final boolean accepted;
        public final ScoreSnapshot scoreSnapshot;

        public MoveRecord(int iteration,
                          long elapsedMillis,
                          String moveType,
                          int slotId,
                          int previousStaffId,
                          int newStaffId,
                          int hardDelta,
                          double coverageDelta,
                          boolean accepted,
                          ScoreSnapshot scoreSnapshot) {
            this.iteration = iteration;
            this.elapsedMillis = elapsedMillis;
            this.moveType = moveType;
            this.slotId = slotId;
            this.previousStaffId = previousStaffId;
            this.newStaffId = newStaffId;
            this.hardDelta = hardDelta;
            this.coverageDelta = coverageDelta;
            this.accepted = accepted;
            this.scoreSnapshot = scoreSnapshot;
        }

        public int getIteration() { return iteration; }
        public long getElapsedMillis() { return elapsedMillis; }
        public String getMoveType() { return moveType; }
        public int getSlotId() { return slotId; }
        public int getPreviousStaffId() { return previousStaffId; }
        public int getNewStaffId() { return newStaffId; }
        public int getHardDelta() { return hardDelta; }
        public double getCoverageDelta() { return coverageDelta; }
        public boolean isAccepted() { return accepted; }
        public ScoreSnapshot getScoreSnapshot() { return scoreSnapshot; }
    }
}

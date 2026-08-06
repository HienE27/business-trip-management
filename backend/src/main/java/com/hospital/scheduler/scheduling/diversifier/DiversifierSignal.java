package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Signals emitted by a {@link SchedulingDiversifier} to tell the search loop
 * what to do next.
 */
public enum DiversifierSignal {
    /** Continue normally — no special action needed. */
    CONTINUE,
    /** Apply shaking: perform {@code k} random moves to escape the basin. */
    SHAKE,
    /** Restart from the given solution. The returned solution may be a copy of
     *  an elite solution or the result of path relinking. */
    RESTART,
    /** Accept the current solution even if it is not strictly improving. */
    FORCE_ACCEPT
}
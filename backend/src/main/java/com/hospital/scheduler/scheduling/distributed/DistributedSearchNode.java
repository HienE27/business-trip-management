package com.hospital.scheduler.scheduling.distributed;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Distributed-search node contract.
 *
 * <p>v11 ships only the interface — the actual transport (gRPC, REST, Raft)
 * lands in v12. Implementations must be safe to call from a worker thread and
 * should never block longer than {@code timeoutMillis}.
 *
 * <p>The lifecycle is straightforward:
 * <pre>
 *   node.submit(problem)        // start a search on this node
 *   node.collectBest()          // poll for the best solution so far
 *   node.shutdown()             // release any resources
 * </pre>
 */
public interface DistributedSearchNode {

    /**
     * Submit a fresh problem to this node. Returns a {@link JobHandle} that
     * can be polled for status.
     */
    JobHandle submit(WorkingSolution initial);

    /**
     * Best solution found so far. Returns {@code null} if the job hasn't
     * produced anything yet.
     */
    ScoreSnapshot currentBestScore();

    WorkingSolution currentBestSolution();

    /**
     * Block until the job finishes or the timeout elapses. Returns true if the
     * job reached a terminal state.
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /** Stop the job and release resources. */
    void shutdown();

    enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    /** Opaque handle returned by {@link #submit(WorkingSolution)}. */
    interface JobHandle {
        String nodeId();
        Status status();
    }
}
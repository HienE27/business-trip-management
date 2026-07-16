package com.hospital.scheduler.scheduling.distributed;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * In-process implementation of {@link DistributedSearchNode}. Runs the
 * supplied search asynchronously on a worker thread and exposes its result
 * via the standard interface. Useful for testing the distributed contract
 * without spinning up a real cluster.
 *
 * <p>The "search" itself is delegated to a {@link SearchTask} the caller
 * supplies at construction time. This keeps the node decoupled from the
 * concrete algorithm implementation.
 */
public class InProcessDistributedSearchNode implements DistributedSearchNode {

    public interface SearchTask {
        WorkingSolution run(WorkingSolution initial, ResultSink sink);
    }

    public interface ResultSink {
        void publish(WorkingSolution solution, ScoreSnapshot score);
    }

    private final String nodeId = UUID.randomUUID().toString();
    private final SearchTask task;
    private CompletableFuture<Void> jobFuture;
    private volatile ScoreSnapshot bestScore;
    private volatile WorkingSolution bestSolution;
    private volatile Status status = Status.PENDING;

    public InProcessDistributedSearchNode(SearchTask task) {
        this.task = task;
    }

    @Override
    public JobHandle submit(WorkingSolution initial) {
        status = Status.RUNNING;
        ResultSink sink = (sol, score) -> {
            // Atomic single-writer update — only the worker thread touches it.
            this.bestSolution = sol;
            this.bestScore = score;
        };
        jobFuture = CompletableFuture.runAsync(() -> {
            try {
                task.run(initial, sink);
                status = Status.COMPLETED;
            } catch (RuntimeException ex) {
                status = Status.FAILED;
                throw ex;
            }
        });
        return new JobHandle() {
            @Override public String nodeId() { return nodeId; }
            @Override public Status status() { return status; }
        };
    }

    @Override
    public ScoreSnapshot currentBestScore() {
        return bestScore;
    }

    @Override
    public WorkingSolution currentBestSolution() {
        return bestSolution;
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        if (jobFuture == null) return status == Status.COMPLETED;
        try {
            jobFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void shutdown() {
        if (jobFuture != null && !jobFuture.isDone()) {
            jobFuture.cancel(true);
        }
        status = Status.FAILED;
    }
}
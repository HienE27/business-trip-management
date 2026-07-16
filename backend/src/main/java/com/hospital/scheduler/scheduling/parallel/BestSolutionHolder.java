package com.hospital.scheduler.scheduling.parallel;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for the best solution found across all workers in a
 * {@link ParallelSearchEngine}. Each worker compares its candidate to
 * {@link #cas(ScoreSnapshot, WorkingSolution)} which is atomic.
 */
public class BestSolutionHolder {

    private final AtomicReference<Entry> best = new AtomicReference<>();

    public void offer(ScoreSnapshot score, WorkingSolution solution) {
        best.updateAndGet(prev -> {
            if (prev == null) return new Entry(score, solution);
            // Lexicographic: prefer lower hard violations, then higher coverage
            if (score.getHardViolations() < prev.score().getHardViolations()) {
                return new Entry(score, solution);
            }
            if (score.getHardViolations() == prev.score().getHardViolations()
                    && score.getCoverage() > prev.score().getCoverage()) {
                return new Entry(score, solution);
            }
            return prev;
        });
    }

    public Entry snapshot() {
        return best.get();
    }

    public ScoreSnapshot bestScore() {
        Entry e = best.get();
        return e != null ? e.score() : null;
    }

    public WorkingSolution bestSolution() {
        Entry e = best.get();
        return e != null ? e.solution() : null;
    }

    public record Entry(ScoreSnapshot score, WorkingSolution solution) {}
}

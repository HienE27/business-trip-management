package com.hospital.scheduler.scheduling.parallel;

import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs N independent search instances in parallel. Each worker uses a fresh
 * RNG seed and (optionally) a different strategy mix. The {@link BestSolutionHolder}
 * is shared across workers, so the strongest solution emerges regardless of which
 * worker finds it.
 *
 * <p>Wire-through:
 * <ul>
 *   <li>{@code parallelism} — worker count (1 disables parallel mode)</li>
 *   <li>{@code seeds} — explicit per-worker seeds, or auto-generated</li>
 *   <li>{@code descriptors} — per-worker {@link IncrementalStatisticsHub} factory;
 *       one hub per worker keeps incremental updates contention-free</li>
 * </ul>
 */
public class ParallelSearchEngine {

    public interface WorkerFactory {
        Worker create(long seed);
    }

    public interface Worker {
        WorkingSolution run(WorkingSolution initial, BestSolutionHolder holder);
    }

    private final int parallelism;
    private final long baseSeed;
    private final long timeoutSeconds;

    public ParallelSearchEngine(int parallelism, long baseSeed, long timeoutSeconds) {
        this.parallelism = Math.max(1, parallelism);
        this.baseSeed = baseSeed;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public Result run(WorkingSolution initial, WorkerFactory factory) {
        BestSolutionHolder holder = new BestSolutionHolder();
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<WorkingSolution>> futures = new ArrayList<>();
        for (int i = 0; i < parallelism; i++) {
            long seed = baseSeed + i * 1009L;
            Worker worker = factory.create(seed);
            futures.add(CompletableFuture.supplyAsync(() -> worker.run(initial, holder), pool));
        }
        WorkingSolution best = initial;
        try {
            pool.shutdown();
            if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
            for (var f : futures) {
                try {
                    WorkingSolution sol = f.get();
                    if (sol != null) best = sol;
                } catch (Exception ignored) {
                    // worker crashed; rely on others
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return new Result(holder.bestScore(), holder.bestSolution() != null ? holder.bestSolution() : best, parallelism);
    }

    public record Result(
            com.hospital.scheduler.scheduling.score.ScoreSnapshot bestScore,
            WorkingSolution bestSolution,
            int workers
    ) {}
}

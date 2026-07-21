package com.hospital.scheduler.scheduling.ml;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ML warm-start research toggle. Persists {@code (problemFingerprint,
 * winningStrategy, finalScore, iterationCount)} per run and, given a new
 * problem fingerprint, picks the historically winning acceptor.
 *
 * <p>Off by default. Toggle via {@code scheduling.ml-warm-start=true}.
 */
public class MlWarmStart {

    public record RunRecord(
            String problemFingerprint,
            String winningStrategy,
            double finalScore,
            int iterationCount,
            LocalDateTime recordedAt
    ) {}

    public interface RunStore {
        void append(RunRecord record);
        List<RunRecord> all();
    }

    private final RunStore store;
    private final FingerprintHasher hasher;
    private final double similarityThreshold;

    public MlWarmStart(RunStore store, FingerprintHasher hasher, double similarityThreshold) {
        this.store = store;
        this.hasher = hasher;
        this.similarityThreshold = Math.max(0.0, Math.min(1.0, similarityThreshold));
    }

    /** Persist a freshly finished run. */
    public void record(String problemFingerprint,
                       String winningStrategy,
                       double finalScore,
                       int iterationCount) {
        store.append(new RunRecord(
                problemFingerprint, winningStrategy, finalScore, iterationCount,
                LocalDateTime.now()));
    }

    /**
     * Look up the historically winning strategy for a problem fingerprint.
     * Returns {@code null} if no similar record exists.
     */
    public String suggest(String problemFingerprint) {
        List<RunRecord> all = store.all();
        RunRecord best = null;
        double bestSim = -1;
        for (RunRecord r : all) {
            double sim = hasher.similarity(problemFingerprint, r.problemFingerprint());
            if (sim > bestSim && sim >= similarityThreshold) {
                best = r;
                bestSim = sim;
            }
        }
        return best != null ? best.winningStrategy() : null;
    }

    public interface FingerprintHasher {
        double similarity(String a, String b);
    }

    /** Simple Levenshtein-distance-based similarity. */
    public static FingerprintHasher levenshtein() {
        return (a, b) -> {
            if (a == null || b == null) return 0.0;
            if (a.equals(b)) return 1.0;
            int distance = levenshteinDistance(a, b);
            int max = Math.max(a.length(), b.length());
            return max == 0 ? 1.0 : 1.0 - ((double) distance / max);
        };
    }

    private static int levenshteinDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    /** In-memory store backed by a list. Replace with JPA-backed store in production. */
    public static class InMemoryRunStore implements RunStore {
        private final List<RunRecord> records = new java.util.ArrayList<>();
        @Override public synchronized void append(RunRecord record) { records.add(record); }
        @Override public synchronized List<RunRecord> all() { return List.copyOf(records); }
    }
}
package com.hospital.scheduler.algorithm;

import java.util.HashMap;
import java.util.Map;

/**
 * OPT-005 stub: Diversification entry point. The full implementation lives in
 * a follow-up OPT-005 change. The class exists here so {@link CspSearchEngine}
 * (which already references {@link TabuAssignmentStore}) compiles cleanly.
 *
 * <p>Current responsibilities (kept intentionally minimal):
 * <ul>
 *   <li>Expose {@link TabuAssignmentStore} — a per-(var, staff) tenure-aware
 *       tabu store. When the search encounters a tabu (var, staff) pair it
 *       skips it unless no non-tabu alternative remains (aspiration).</li>
 *   <li>Provide {@link TabuAssignmentStore#NO_TABU} — a shared singleton used
 *       by every code path that wants "no tabu filtering".</li>
 * </ul>
 *
 * <p>The actual multi-restart orchestration (seeded restarts, shaking, audit
 * metrics) will be wired in once OPT-005 picks up again.
 */
public final class CspDiversifier {

    private CspDiversifier() {}

    /**
     * Tracks tabu (var, staff) pairs and a per-pair remaining tenure. When the
     * tenure reaches zero the pair is no longer tabu. {@link #isTabu(int, int)}
     * is the only contract {@link CspSearchEngine} depends on.
     */
    public static final class TabuAssignmentStore {
        private final Map<Long, Integer> tenures = new HashMap<>();

        /** Sentinel that never reports tabu. Safe to share across threads. */
        public static final TabuAssignmentStore NO_TABU = new TabuAssignmentStore();

        public void tick() {
            tenures.entrySet().removeIf(e -> e.getValue() <= 0);
        }

        public void markTabu(int var, int staff, int tenure) {
            long key = pack(var, staff);
            tenures.merge(key, tenure, Math::max);
        }

        public boolean isTabu(int var, int staff) {
            Integer remaining = tenures.get(pack(var, staff));
            return remaining != null && remaining > 0;
        }

        public boolean isEmpty() {
            return tenures.isEmpty();
        }

        public int size() {
            return tenures.size();
        }

        private static long pack(int var, int staff) {
            return ((long) var << 32) | (staff & 0xffffffffL);
        }
    }
}
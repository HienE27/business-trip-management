package com.hospital.scheduler.algorithm;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OPT-002 repair-pass priority queue manager.
 *
 * <p>Drives the order in which unassigned or conflicted slots are fed to
 * {@link CspRepairHeuristics#attemptRepair}. Slots are ordered by:
 * <ol>
 *   <li><b>Scarcity</b> — fewer eligible staff first. A shift type with one
 *       eligible doctor (tier 0) is more urgent than one with five (tier 3).
 *       Tier is computed via {@link CspRepairHeuristics#computeScarcityTier}.</li>
 *   <li><b>Streak</b> — slots that have failed repair attempts before are
 *       prioritised (higher streak value comes first).  This surfaces stuck
 *       slots early so the search exhausts their rotation retries before
 *       burning budget on easy wins.</li>
 *   <li><b>Slack</b> — lower-workload staff are preferred (we want to keep
 *       the workload balanced). The "slack" field actually carries the
 *       current workload of the most-loaded eligible staff; lower wins.</li>
 *   <li><b>Sequence</b> — FIFO tiebreaker for entries with identical scores.</li>
 * </ol>
 *
 * <p>The queue also tracks:
 * <ul>
 *   <li>per-staff workload counters (incremented by {@link #recordAssignment})</li>
 *   <li>a relaxation flag exposed via {@link #shouldRelaxFairness()} so the
 *       caller can decide whether to permit BR05 (fairness) relaxation in the
 *       current repair pass.</li>
 * </ul>
 */
public final class CspRepairQueueManager {

    private final PriorityQueue<RepairEntry> queue;
    private final Map<Integer, Integer> staffWorkload = new HashMap<>();
    private final Map<String, Integer> eligibleCounts;
    private final boolean relaxFairnessEnabled;
    private final AtomicLong sequenceCounter = new AtomicLong(0L);

    private CspRepairQueueManager(PriorityQueue<RepairEntry> queue,
                                  Map<Integer, Integer> initialWorkload,
                                  Map<String, Integer> eligibleCounts,
                                  boolean relaxFairnessEnabled) {
        this.queue = queue;
        if (initialWorkload != null) this.staffWorkload.putAll(initialWorkload);
        this.eligibleCounts = eligibleCounts != null ? eligibleCounts : Map.of();
        this.relaxFairnessEnabled = relaxFairnessEnabled;
    }

    /**
     * Build a queue seeded with the supplied unmet var keys.
     *
     * <p>For every var key (format {@code date|shiftType}) the manager derives
     * a {@link PriorityScore} from:
     * <ul>
     *   <li>scarcity tier — looked up from {@code eligibleCounts} via shift type</li>
     *   <li>streak — starts at 0 for first attempt</li>
     *   <li>slack — workload of the currently-assigned staff (if any), else 0</li>
     *   <li>sequence — monotonic insertion counter</li>
     * </ul>
     *
     * @param unmetVars              var keys that need repair (date|shiftType)
     * @param varIndexMap            var key → problem index (only used to skip unknowns; may be null)
     * @param staffIndexMap          staffId → problem index (used for workload seeding; may be null)
     * @param staffList              full staff list (used for fallback workload seeding if map absent)
     * @param assignments            current assignments (keyed {@code staffId_date_shiftType})
     * @param eligibleCounts         shift type → eligible staff count
     * @param relaxFairnessEnabled   when true, the caller may relax BR05 (fairness) during repair
     */
    public static CspRepairQueueManager build(
            Set<String> unmetVars,
            Map<String, Integer> varIndexMap,
            Map<Integer, Integer> staffIndexMap,
            List<?> staffList,
            Map<String, String> assignments,
            Map<String, Integer> eligibleCounts,
            boolean relaxFairnessEnabled) {

        Map<Integer, Integer> initialWorkload = new HashMap<>();
        if (assignments != null) {
            for (String key : assignments.keySet()) {
                String[] parts = key.split("_");
                if (parts.length >= 1) {
                    try {
                        int sid = Integer.parseInt(parts[0]);
                        initialWorkload.merge(sid, 1, Integer::sum);
                    } catch (NumberFormatException ignored) {
                        // skip malformed keys
                    }
                }
            }
        }
        if (staffIndexMap != null) {
            for (Integer sid : staffIndexMap.keySet()) {
                initialWorkload.putIfAbsent(sid, 0);
            }
        }

        PriorityQueue<RepairEntry> q = new PriorityQueue<>();
        CspRepairQueueManager mgr = new CspRepairQueueManager(
                q, initialWorkload, eligibleCounts, relaxFairnessEnabled);

        if (unmetVars != null) {
            for (String varKey : unmetVars) {
                if (varIndexMap != null && !varIndexMap.containsKey(varKey)) continue;
                mgr.enqueue(varKey);
            }
        }
        return mgr;
    }

    private void enqueue(String varKey) {
        String[] parts = varKey.split("\\|", -1);
        if (parts.length < 2) return;
        LocalDate date;
        try {
            date = LocalDate.parse(parts[0]);
        } catch (Exception e) {
            return;
        }
        String shiftType = parts[1];
        int eligibleCount = eligibleCounts.getOrDefault(shiftType, 0);
        int scarcityTier = CspRepairHeuristics.computeScarcityTier(eligibleCount);
        long seq = sequenceCounter.getAndIncrement();
        // Streak starts at 0; the queue always promotes fresh entries to the
        // front on re-entry (see {@link #markRepaired}). Slack is the minimum
        // workload observed so far across the staff list; a conservative 0
        // when unknown is correct (lower slack = higher priority).
        int slack = 0;
        PriorityScore score = new PriorityScore(scarcityTier, 0, slack, seq);
        q().offer(new RepairEntry(varKey, date, shiftType, eligibleCount, score));
    }

    /**
     * Pop the next entry to repair. Returns {@code null} when the queue is empty.
     */
    public RepairEntry pollNextRepair() {
        return q().poll();
    }

    /**
     * Remove a successfully repaired entry so it isn't reprocessed.  If the
     * entry has already been polled (typical) this is a no-op.
     */
    public void markRepaired(String varKey) {
        if (varKey == null) return;
        q().removeIf(e -> e.varKey().equals(varKey));
    }

    /**
     * Bump the per-staff workload counter.  Called after a successful
     * assignment to keep the {@link PriorityScore#slack()} field fresh on
     * subsequent re-queues.
     */
    public void recordAssignment(int staffId) {
        staffWorkload.merge(staffId, 1, Integer::sum);
    }

    /**
     * Re-enqueue a var key that failed its current repair attempt with an
     * incremented streak.  Used by the caller to drive rotation retries.
     */
    public void reenqueueWithStreak(String varKey, int newStreak) {
        String[] parts = varKey.split("\\|", -1);
        if (parts.length < 2) return;
        String shiftType = parts[1];
        LocalDate date;
        try {
            date = LocalDate.parse(parts[0]);
        } catch (Exception e) {
            return;
        }
        int eligibleCount = eligibleCounts.getOrDefault(shiftType, 0);
        int scarcityTier = CspRepairHeuristics.computeScarcityTier(eligibleCount);
        int slack = currentMaxWorkload();
        long seq = sequenceCounter.getAndIncrement();
        q().offer(new RepairEntry(varKey, date, shiftType, eligibleCount,
                new PriorityScore(scarcityTier, newStreak, slack, seq)));
    }

    /**
     * Snapshot of the current per-staff workload counters.
     */
    public Map<Integer, Integer> getStaffWorkload() {
        return new HashMap<>(staffWorkload);
    }

    /**
     * Number of repair entries still in the queue (never decreases when an
     * entry is re-enqueued with a higher streak).
     */
    public int remainingRepairs() {
        return q().size();
    }

    /**
     * Whether the caller should permit BR05 (fairness) relaxation during this
     * repair pass.
     */
    public boolean shouldRelaxFairness() {
        return relaxFairnessEnabled;
    }

    private PriorityQueue<RepairEntry> q() {
        return queue;
    }

    private int currentMaxWorkload() {
        int max = 0;
        for (Integer v : staffWorkload.values()) {
            if (v != null && v > max) max = v;
        }
        return max;
    }

    /**
     * Composite priority score.  Ordering:
     * <pre>
     *   scarcity  ASCENDING (tier 0 = scarce first)
     *   streak    DESCENDING (higher streak = stuck earlier)
     *   slack     ASCENDING (lower workload first)
     *   sequence  ASCENDING (FIFO on equal score)
     * </pre>
     */
    public record PriorityScore(int scarcity, int streak, int slack, long seq)
            implements Comparable<PriorityScore> {

        @Override
        public int compareTo(PriorityScore other) {
            int cmp = Integer.compare(this.scarcity, other.scarcity);
            if (cmp != 0) return cmp;
            // streak DESCENDING: higher streak comes first
            cmp = Integer.compare(other.streak, this.streak);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(this.slack, other.slack);
            if (cmp != 0) return cmp;
            return Long.compare(this.seq, other.seq);
        }
    }

    /**
     * One repair entry — a single slot waiting for {@link CspRepairHeuristics#attemptRepair}.
     * Implements {@link Comparable} so {@link PriorityQueue} can order entries by
     * {@link #priority} without a separate comparator.
     */
    @Builder
    @Getter
    public static final class RepairEntry implements Comparable<RepairEntry> {
        private final String varKey;
        private final LocalDate date;
        private final String shiftType;
        private final int eligibleStaffCount;
        private final PriorityScore priority;

        public RepairEntry(String varKey, LocalDate date, String shiftType,
                           int eligibleStaffCount, PriorityScore priority) {
            this.varKey = varKey;
            this.date = date;
            this.shiftType = shiftType;
            this.eligibleStaffCount = eligibleStaffCount;
            this.priority = priority;
        }

        // ── Convenience accessors matching the test contract ─────────────────
        public String varKey() { return varKey; }
        public LocalDate date() { return date; }
        public String shiftType() { return shiftType; }
        public int eligibleStaffCount() { return eligibleStaffCount; }
        public PriorityScore priority() { return priority; }

        @Override
        public int compareTo(RepairEntry other) {
            if (other == null) return -1;
            if (priority == null && other.priority == null) return 0;
            if (priority == null) return 1;
            if (other.priority == null) return -1;
            return priority.compareTo(other.priority);
        }
    }
}
package com.hospital.scheduler.calculator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects diagnostics during algorithm analysis.
 * Thread-safe for concurrent analyzer runs (Mode 3 compares algorithms).
 */
public class AnalysisCollector {

    private final String algorithmType;
    private final long startTime = System.currentTimeMillis();

    private final AtomicInteger varsTotal = new AtomicInteger(0);
    private final AtomicInteger varsAssigned = new AtomicInteger(0);
    private final AtomicInteger varsFailed = new AtomicInteger(0);
    private final AtomicInteger varsExplored = new AtomicInteger(0);

    private final Map<String, AtomicInteger> shiftTypeCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> shiftTypeAssigned = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> shiftTypeDomainSum = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> shiftTypeDomainMin = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> shiftTypeEligibleStaff = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicInteger>> l04SpecialtyCount = new ConcurrentHashMap<>();

    private final List<String> bottleneckLog = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failureReasons = Collections.synchronizedList(new ArrayList<>());

    private volatile String terminatedBy = "UNKNOWN";

    public AnalysisCollector(String algorithmType) {
        this.algorithmType = algorithmType;
        for (String st : new String[]{"L01", "L02", "L03", "L04"}) {
            shiftTypeCount.put(st, new AtomicInteger(0));
            shiftTypeAssigned.put(st, new AtomicInteger(0));
            shiftTypeDomainSum.put(st, new AtomicInteger(0));
            shiftTypeDomainMin.put(st, new AtomicInteger(Integer.MAX_VALUE));
            shiftTypeEligibleStaff.put(st, new AtomicInteger(0));
            l04SpecialtyCount.put(st, new ConcurrentHashMap<>());
        }
    }

    // ── Recording methods ──

    public void recordVariable(String shiftType) {
        varsTotal.incrementAndGet();
        shiftTypeCount.get(shiftType).incrementAndGet();
    }

    public void recordAssignment(String shiftType) {
        varsAssigned.incrementAndGet();
        shiftTypeAssigned.get(shiftType).incrementAndGet();
    }

    public void recordFailure(String shiftType, String reason) {
        varsFailed.incrementAndGet();
        failureReasons.add(shiftType + ": " + reason);
    }

    public void recordExplored() {
        varsExplored.incrementAndGet();
    }

    public void recordDomainSize(String shiftType, int size) {
        shiftTypeDomainSum.get(shiftType).addAndGet(size);
        shiftTypeDomainMin.get(shiftType).updateAndGet(cur -> Math.min(cur, size));
        if (size <= 2) {
            bottleneckLog.add(shiftType + ": domain size=" + size);
        }
    }

    public void recordEligibleStaff(String shiftType, int count) {
        shiftTypeEligibleStaff.get(shiftType).set(count);
    }

    public void recordL04Specialty(String specialty, int count) {
        for (var entry : l04SpecialtyCount.entrySet()) {
            entry.getValue().computeIfAbsent(specialty, k -> new AtomicInteger(0)).addAndGet(count);
        }
    }

    public void recordBottleneck(String shiftType, String message) {
        bottleneckLog.add(shiftType + ": " + message);
    }

    public void setTerminatedBy(String reason) {
        this.terminatedBy = reason;
    }

    // ── Accessors ──

    public String getAlgorithmType() { return algorithmType; }
    public long getElapsedMs() { return System.currentTimeMillis() - startTime; }
    public int getVarsTotal() { return varsTotal.get(); }
    public int getVarsAssigned() { return varsAssigned.get(); }
    public int getVarsFailed() { return varsFailed.get(); }
    public int getVarsExplored() { return varsExplored.get(); }
    public String getTerminatedBy() { return terminatedBy; }
    public List<String> getBottleneckLog() { return List.copyOf(bottleneckLog); }
    public List<String> getFailureReasons() { return List.copyOf(failureReasons); }

    public int getShiftTypeCount(String st) { return shiftTypeCount.getOrDefault(st, new AtomicInteger(0)).get(); }
    public int getShiftTypeAssigned(String st) { return shiftTypeAssigned.getOrDefault(st, new AtomicInteger(0)).get(); }
    public double getAvgDomainSize(String st) {
        int count = getShiftTypeCount(st);
        int sum = shiftTypeDomainSum.getOrDefault(st, new AtomicInteger(0)).get();
        return count > 0 ? (double) sum / count : 0;
    }
    public int getMinDomainSize(String st) {
        int val = shiftTypeDomainMin.getOrDefault(st, new AtomicInteger(Integer.MAX_VALUE)).get();
        return val == Integer.MAX_VALUE ? 0 : val;
    }
    public int getEligibleStaff(String st) { return shiftTypeEligibleStaff.getOrDefault(st, new AtomicInteger(0)).get(); }
    public Map<String, Integer> getL04SpecialtyCounts(String st) {
        Map<String, AtomicInteger> inner = l04SpecialtyCount.getOrDefault(st, new ConcurrentHashMap<>());
        Map<String, Integer> result = new LinkedHashMap<>();
        inner.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
}

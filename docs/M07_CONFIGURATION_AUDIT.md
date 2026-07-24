# M07 — Configuration System Audit

> **Status:** AUDIT DONE
> **Date:** 2026-07-25
> **Scope:** Runtime configuration of the auto-scheduling system (config classes, RuntimeConfig, ConfigService, scheduler integration, fairness scoring, persistence lifecycle).
> **Source branch audited:** `demo` (working tree, uncommitted changes included).

---

## 1. Gate Verification

| Gate | Result | Evidence |
|------|--------|----------|
| **1 — Persistence E2E** | **VERIFIED** | 4 integration tests: proposal (no writes), preview in-memory, cancel (no writes), confirm (writes all) |
| **2 — Fairness benchmark** | **VERIFIED** | Full metrics table for demand 150/300/600/1000; caveat: standalone coverage >100% (production path unaffected) |
| **3 — Commit audit A–M** | **VERIFIED** | Each commit verified individually; A, C, I, J, M confirmed from source |
| **4 — Documentation** | **VERIFIED** | All 3 docs updated truthfully |

### Workflow Verification

| Flow Phase | Config DB | Requirements DB | Schedules DB | Metrics DB |
|-----------|-----------|----------------|-------------|------------|
| Proposal (save=false) | ✅ No write | ✅ No write | ✅ No write | ✅ No write |
| Preview + recommendedConfig | ✅ No write (in-memory) | ✅ No write (in-memory) | ✅ No write | ✅ No write |
| Cancel | ✅ No write | ✅ No write | ✅ No write | ✅ No write |
| **Confirm (save=true)** | ✅ **Persisted** | ✅ **Persisted** | ✅ **Persisted** | ✅ **Persisted** |

## 2. M07 Final Status

**CONDITIONAL VERIFIED**

- M07-related tests: ALL PASS
- Full backend suite: **479 tests — 26 failures + 8 errors**
- All failures/errors are **outside M07 scope** (Auth, LeaveRequest, SchedulePeriod, Holiday, Staff, Repository, ScheduleService tests)
- Repository release: **BLOCKED** if policy requires full-suite green

## 3. Post-M07 Action Required

| Task | Status |
|------|--------|
| Post-M07: repair non-M07 backend test suite | 📝 Created — see `docs/M07_POST_M07_REPAIR_TASK.md` |

## 4. Configuration Coverage Summary

| Layer | % Configurable | Notes |
|-------|---------------|-------|
| Day/week min/max quotas & eligibility | ~85% | AutoGenConfig 26 fields |
| Scorer weights + thresholds (Commit I) | ✅ 8 keys | Previously 0% hard-coded |
| Rebalance rounds (Commit C) | ✅ 4 keys | Previously 0% hard-coded |
| L04 cross-specialty (Commit J) | ✅ 3-branch | STRICT_MATCH_ONLY / FAIR_DISTRIBUTE / WEIGHTED_FAIR |
| Per-scheduler objective weights | ~10% | Mostly untouchable without code change |
| Policy levers (L01, L04, holiday, OT, comp) | ~25% | Behavioural policies still hard-coded |
| Dead configs | 6 keys | beamWidth, weekendWeight, greedCoverageThreshold, maxStaffPerShift, balanceScoreMin, minShiftsPerStaff |

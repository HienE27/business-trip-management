# Release Notes — v1.0.0

**Version**: v1.0.0
**Release Date**: 2026-07-17
**Status**: RC1

---

## 1. Overview

This is the initial stable release of the **Hospital Scheduler** (Quản lý Lịch Công Tác) system. The system supports automatic scheduling of medical staff across four shift types with full conflict detection, fairness scoring, and manual override capabilities.

---

## 2. Core Features

### 2.1 Scheduling Algorithms

| Algorithm | Description | Default |
|-----------|-------------|---------|
| **Greedy** | Round-robin greedy assignment with fairness comparator | ✅ (default) |
| **FairGreedy** | Fairness-optimized greedy with rotation index | Fallback |
| **CSP MRV-FC** | Constraint satisfaction with backtracking | Optional |
| **V10 LocalSearch** | Tabu search optimizer | Optional |

### 2.2 Shift Types

| Type | Code | Description | Overnight |
|------|------|-------------|-----------|
| Trực 24/24 | L01 | 7:30 AM → 7:30 AM next day | Yes |
| Lịch thông tầm | L02 | Day shift, no lunch break | No |
| Phòng khám dịch vụ | L03 | Service clinic shift | No |
| Phòng khám chuyên gia | L04 | Expert clinic shift | No |

### 2.3 Business Rules (BR)

All 7 rules from SPEC are enforced:

| Rule | Description | Enforcement |
|------|-------------|-------------|
| BR-01 | L01 ↔ L02 conflict (same staff, same day) | HARD |
| BR-02 | L03 ↔ L04 conflict (same staff, same day) | HARD |
| BR-03 | Max 6 consecutive work days | SOFT (V10) |
| BR-04 | Adjacent L01 penalty | SOFT (V10) / HARD filter (Greedy) |
| BR-05 | Leave conflict | HARD |
| BR-06 | Max shifts per month | SOFT |
| BR-07 | No duplicate shifts | HARD (DB + V10) |

---

## 3. Config System

### 3.1 Active Configs (v1.0)

| Config | Category | Default | Description |
|--------|----------|---------|-------------|
| `weekendWeight` | Fairness | 2.0 | Weekend penalty multiplier |
| `maxShiftsPerStaff` | Limits | 0 (unlimited) | Max total shifts per staff |
| `balanceScoreMin` | Fairness | 0.70 | Trigger FairGreedy fallback |
| `greedyCoverageThreshold` | Monitoring | 0.85 | Coverage target (display only) |
| `l01MaxPerWeek` | Limits | 0 | Max L01 per week per staff |
| `l02MaxPerWeek` | Limits | 0 | Max L02 per week per staff |
| `l03MaxPerWeek` | Limits | 0 | Max L03 per week per staff |
| `l04MaxPerWeek` | Limits | 0 | Max L04 per week per staff |
| `l04CrossSpecialtyEnabled` | L04 | false | Allow cross-specialty L04 |
| `l04CrossSpecialtyRatio` | L04 | 0.3 | Cross-specialty shortage threshold |
| `l04AllowedSpecialties` | L04 | [] | List of allowed specialty IDs |

### 3.2 Reserved for v1.1

The following configs are read but not used in v1.0:

| Config | Planned Use |
|--------|-------------|
| `overnightRecoveryHours` | L01 spacing enforcement |
| `autoCompensationEnabled` | Control auto-compensation |
| `l04BalanceStrategy` | L04 distribution strategy |
| `l0XMinPerWeek` | Minimum shifts per week |

### 3.3 Deprecated

| Config | Replacement |
|--------|-------------|
| `minStaffPerShift` | Use `maxStaffPerShift` |
| `minShiftsPerStaff` | Not used |

---

## 4. Audit Results

This release was preceded by a comprehensive audit:

| Module | Status | Issues |
|--------|--------|--------|
| Scheduler Engine | ✅ Stable | 2 misleading descriptions (fixed) |
| Constraint Engine | ✅ Stable | BR-04 inconsistency (documented) |
| Score Engine | ✅ Stable | 1 stub component (documented) |
| Config System | ✅ Stable | 4 reserved, 2 deprecated |

**No blocking issues found.**

---

## 5. Known Limitations

### 5.1 BR-04 Behavior Inconsistency

Greedy and V10 handle adjacent L01 differently:
- **Greedy**: HARD block (staff cannot work adjacent L01)
- **V10**: SOFT penalty (adjacent L01 allowed but penalized)

Both are valid interpretations. No fix planned for v1.0.

### 5.2 ScheduleScorer Stub

`BenchmarkService` uses `ScheduleScorer` which returns hardcoded values.
Benchmark scores are not meaningful in v1.0.

### 5.3 V10 Incremental Re-solve

V10 LocalSearch does not support incremental re-solve.
Re-solve falls back to full solve.

---

## 6. Roadmap (v1.1)

The following are planned for future release:

- [ ] Align BR-04 behavior across all engines
- [ ] Implement `ScheduleScorer` properly for benchmark
- [ ] Add `overnightRecoveryHours` enforcement
- [ ] Add `autoCompensationEnabled` toggle
- [ ] Implement `l04BalanceStrategy`
- [ ] Add `l0XMinPerWeek` enforcement
- [ ] Implement V10 incremental re-solve

---

## 7. Migration Notes

### 7.1 Database

No schema changes required for upgrade from previous versions.

### 7.2 API

No breaking changes to existing endpoints.

New endpoints:
- `POST /api/v1/auto-scheduling/preview-with-quality`
- `GET /api/v1/auto-scheduling/quality/{periodId}`
- `GET /api/v1/scheduling/config/effective`

---

## 8. Dependencies

| Component | Version |
|-----------|---------|
| Java | 17+ |
| Spring Boot | 3.x |
| MySQL | 8.0 |
| Node.js | 20+ |
| Next.js | 14+ |

---

## 9. Support

For issues and questions, contact the development team.

---

## 10. Credits

**Development Team**: Nhóm 4
**Supervisor**: ThS. Văn Minh Hoàng Quân

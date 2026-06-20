# Frontend polish round 2 — lazy-load + finalize guard matrix

This PR continues the frontend polish work from PR #6. It bundles four
related improvements together because they share build/test infrastructure
and would be artificial to split:

## Commits

1. `483775a` perf(fe): lazy-load bottom info panels on /monthly-schedule
2. `fe9e719` feat(fe): guard 4 schedule-by-type routes + ROLE_MATRIX audit
3. `a4ac2f5` chore(fe): verify optimizePackageImports tree-shakes @/components/ui
4. `24c7c52` test(fe): add unit tests for GuardedScheduleByTypePage

## What changed

### 1. Lazy-load bottom info panels (R1)

`/monthly-schedule` had a 94 KB initial JS chunk. The conflict/coverage/
review panels below the calendar are now loaded via `next/dynamic` with
`ssr: false`, deferring ~7 KB (ConflictSection 1.7 KB, CoverageSection
0.4 KB, ReviewSnapshotPanel inline). Trade-off: the calendar grid stays
eager because it is above the fold and lazy-loading would visibly delay
first paint.

### 2. Guard 4 schedule-by-type routes (R2)

The 4 routes `/duty-24`, `/all-day`, `/service-clinic`, `/expert-clinic`
were bare re-exports of `ScheduleByTypePage` with no role guard. STAFF
users could URL-access them. A new `GuardedScheduleByTypePage` wrapper
composes `RoleGuard + ScheduleByTypePage` with default `["ADMIN", "MANAGER"]`
allow list. Also adds `docs/ROLE_MATRIX_2026-06-20.md` cataloguing every
route (29 total) + its guard + 3 known gaps for follow-up.

### 3. Verify optimizePackageImports (R3)

Removed the bogus `@/components/schedule` entry (no barrel exists at that
path). Verified that the remaining `@/components/ui` optimization actually
tree-shakes: `/periods` imports `Button` only and its page payload is 986 B
with no FormInput/ToastProvider/ConfirmDialog leakage.

### 4. Unit tests for GuardedScheduleByTypePage (R4)

8 tests covering:
- ADMIN/MANAGER render the schedule page
- STAFF and unauthenticated users see denied state
- Custom allow lists (`["ADMIN"]`, `["ADMIN", "MANAGER", "STAFF"]`)
- Config (activeSection, title) passthrough

## Test results

- vitest: 22 files / 243 tests pass (was 21 / 235 — +8 tests)
- tsc --noEmit: clean (user code)
- pnpm analyze: 3 reports generated successfully

## Known gaps documented for follow-up

- `/leave-requests` — no route-level guard (uses WorkflowShell, not DashboardShell)
- `/auto-scheduling/{algorithm-config,history}` — inherit-only inline guard
- `/monthly-schedule` — only inline guard; STAFF allowed (by design)

See `docs/ROLE_MATRIX_2026-06-20.md` for the full matrix.

## Review checklist

- [x] Bundle analyzer wired up and used
- [x] All 4 schedule-by-type routes now route through RoleGuard
- [x] E2E spec extended to cover the 4 new guards
- [x] No user code regressions (TS clean)
- [x] Lazy-load keeps above-the-fold content eager

## Related

- docs/PERFORMANCE_AUDIT_2026-06-20.md — bundle snapshot
- docs/ROLE_MATRIX_2026-06-20.md — guard audit
- Trellis task: `.trellis/tasks/06-20-06-20-fe-polish-r2/`
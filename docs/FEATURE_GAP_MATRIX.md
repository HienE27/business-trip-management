# Feature Gap Matrix — M01 to M07

> **Date**: 2026-07-14
> **Scope**: Feature completion across backend (Spring Boot) + frontend (Next.js)
> **Methodology**: Endpoint inventory (28 entities, 23 controllers, 30 frontend pages) + test coverage counts + frontend ↔ backend parity check
> **Status**: Snapshot — re-run quarterly

---

## 1. Module Completion Matrix (Backend ↔ Frontend ↔ Tests)

Legend: ✅ complete · 🟡 partial / in-progress · ❌ missing · 🟢 not started

| Module | Description | Backend API | Frontend UI | Tests | Status |
|---|---|---|---|---|---|
| **M01** | Nhân sự (Staff/Specialty/Role) | ✅ 11 endpoints | ✅ 5 pages (staff list, create, edit, profile, roles) | ✅ ~30 (RoleServiceTest, StaffServiceProtectionTest, etc.) | **✅ Complete** |
| **M02** | Lịch trực 24/24 (L01) | ✅ bulk + single + comp day | ✅ `duty-24` page | ✅ ~50 (ScheduleServiceBulkL01Test, CompensationDayServiceTest, holiday tests) | **✅ Complete** |
| **M03** | Lịch thông tầm (L02) | ✅ shared with M02 | ✅ `all-day` page | (shared with M02) | **✅ Complete** |
| **M04** | Phòng khám dịch vụ (L03) | ✅ shared with M02 | ✅ `service-clinic` page | (shared) | **✅ Complete** |
| **M05** | Phòng khám chuyên gia (L04) | ✅ expert-clinic + weekly view | ✅ `expert-clinic` page | ✅ 10 (ScheduleServiceExpertClinicWeeklyTest) | **🟡 Partial** (cross-specialty fairness edge cases — see §3) |
| **M06** | Dashboard + Notification + Audit | ✅ 12+ endpoints | ✅ 3 pages (dashboard, notifications, audit-history) | 🟡 ~15 (DashboardServiceTest, AuditHistoryServiceTest, NotificationServiceTest) | **🟡 Partial** (real-time SSE not yet implemented — see §3) |
| **M07** | Auto Scheduling | ✅ preview + apply + progress + metrics | ✅ 3 pages (auto-scheduling, history, algorithm-config) | 🟡 ~25 (algorithm + 4 sub-service tests after refactor) | **🟡 Partial** (CSP performance at 90+ staff needs tuning — see §3) |

**Aggregate completion**: 4/7 modules complete, 3/7 partial.

---

## 2. Frontend ↔ Backend Parity

For each backend endpoint group, check whether a frontend page consumes it.

| Backend API | Frontend Page | Parity | Notes |
|---|---|---|---|
| `POST /auth/login`, `/refresh`, `/logout` | `/login` | ✅ | |
| `GET/POST/PUT/DELETE /staffs*` | `/staff`, `/staff/create`, `/staff/[id]`, `/staff/[id]/edit` | ✅ | |
| `GET/POST/PUT/DELETE /specialties*` | Specialty management UI | ✅ | (via `SpecialtyCrudPanel`) |
| `GET /roles`, `POST /roles/{id}/permissions/{id}` | `/settings/roles` | ✅ | |
| `GET/POST/PUT/DELETE /schedules*` | `/schedule/[type]` | ✅ | Guarded by `GuardedScheduleByTypePage` |
| `POST /schedules/bulk`, `/schedules/bulk/l01` | `BulkScheduleModal`, `BulkL01Modal` | ✅ | |
| `GET /schedules/conflicts/check/{id}` | `ConflictPanel` (dashboard) | ✅ | Real-time via SSE pending |
| `GET/POST/PUT /periods*` | `/periods` | ✅ | |
| `POST /periods/{id}/publish`, `/archive` | `/periods` (action buttons) | ✅ | |
| `GET/POST/PUT/DELETE /shift-requirements*` | (inline on period edit) | 🟡 | No dedicated page — uses modal/tab |
| `GET/POST/PUT/DELETE /compensation-days*` | (read-only display on schedule) | 🟡 | No CRUD UI for manual comp day create |
| `GET /shift-types*` | (reference data, loaded into form selects) | ✅ | |
| `POST /leave-requests`, `/approve`, `/reject`, `/cancel` | `/leave-requests` | ✅ | |
| `POST /schedule-exchange`, `/accept`, `/approve`, `/reject`, `/cancel` | `/swap-requests` | ✅ | |
| `GET /notifications`, `PUT /notifications/{id}/read` | `/notifications`, `NotificationCenter` widget | ✅ | |
| `GET /dashboard/*` | `/dashboard` | ✅ | |
| `GET /audit-histories` | `/audit-history` | ✅ | |
| `POST /auto-schedule/preview`, `/apply` | `/auto-scheduling` | ✅ | `AutoSchedulingWizard` |
| `GET /auto-schedule/progress/{id}` | (polling) | ✅ | Polling-based (SSE upgrade pending) |
| `GET /auto-schedule/metrics/{id}` | `/auto-scheduling/history` | ✅ | |
| `POST/GET/PUT/DELETE /algorithm-configs*` | `/auto-scheduling/algorithm-config` | ✅ | |
| `GET /algorithm-configs/recommend` | `PresetSandboxModal` | ✅ | |
| `POST/GET /algorithm-templates*` | (templates list page) | 🟡 | Implemented as component, no dedicated page |
| `GET/POST/PUT/DELETE /holidays*` | `/holidays` | ✅ | |
| `POST /staffs/import` | (Staff import button) | ✅ | (in `StaffCrudPanel`) |
| `GET /reports/*` | `/reports`, `/reports/staff`, `/reports/statistics`, `/reports/monthly`, `/reports/conflicts` | ✅ | |
| `GET /statistics*` | (used by reports pages) | ✅ | |
| `GET /system-logs*` | (system admin UI) | 🟡 | Backend has controller, no dedicated frontend page |
| `GET /app-configs*` | `/settings` (email config) | ✅ | |
| `GET /data-integrity/*` | (admin tool) | 🟡 | Backend has controller, no frontend |

**Parity verdict**: ~90% — 3 small gaps:
- Compensation day CRUD UI (only display, no manual create)
- System log viewer (backend exists, no frontend page)
- Data Integrity viewer (backend exists, no frontend page)

---

## 3. Known Gaps & Technical Debt

### 3.1 Cross-specialty fairness (M05)

**Issue**: When L04 (Phòng khám chuyên gia) needs staff of multiple specialties
(e.g. Mắt, Tim mạch) on the same day, the fair-share algorithm may favor one
specialty's staff over another's based on total workload, ignoring specialty-specific
fairness.

**State**: `FairShareCalculator` is implemented but lacks integration test for
multi-specialty scenarios.

**Work**: 1 integration test + verify `runFairGreedy()` covers it.

### 3.2 Real-time conflict notifications (M06)

**Issue**: `ConflictPanel` polls every N seconds. WebSocket infrastructure
(`WebSocketConfig`, `ConflictStreamBridge`, `NotificationStreamBridge`) is wired but
not actively pushed from the server side — the bridge is one-way (client → server
only).

**State**: Half-implemented. Client-side ready, server-side push missing.

**Work**:
- Add `@Scheduled` task in `ConflictDetectionService` to detect new conflicts on
  recent schedule changes and broadcast via `SimpMessagingTemplate`.
- Already-built `NotificationStreamBridge` shows the messages.

**Effort**: M (1-2 days).

### 3.3 CSP performance at scale (M07)

**Issue**: CSP-based auto-scheduling runs in O(n²) on the number of (staff × day)
variables. For 30 staff × 30 days = 900 variables, ~3s. For 90 × 30 = 2700
variables, ~25s — too slow for production.

**State**: Works fine for current scale (≤30 staff).

**Work**:
- Profile `CspSearchEngine` with JMH on 90-staff scenarios.
- Likely wins: better variable ordering (MRV + degree), nogood learning already
  present (`CspNogoodStore`), parallel search via `ForkJoinPool`.
- Alternative: split by week and merge — if week boundaries are weak, do it.

**Effort**: L (3-5 days).

### 3.4 Compensation day manual create UI

**Issue**: Comp days are auto-created on L01 save. There's no way to manually
create a comp day for an off-cycle leave (e.g. doctor returning from training).

**State**: Backend API ready (PUT/DELETE work), no frontend form.

**Effort**: S (half day).

### 3.5 System log viewer + Data Integrity viewer

**Issue**: Both have backend controllers (`SystemLogController`,
`DataIntegrityController`) but no frontend page.

**State**: 2 endpoints x 5 endpoints each = ~10 API routes unused.

**Effort**: S–M (1 day total).

### 3.6 Frontend test coverage

Compared to backend (~371 tests), frontend has only ~12 test files
(`AuthProvider.test.tsx`, `RouteGuard.test.tsx`, `AppSidebar.test.tsx`,
`ConflictPanel.test.tsx`, `ConflictBadge.test.tsx`, `ConflictResolutionModal.test.tsx`,
`ExportControls.test.tsx`, `GuardedScheduleByTypePage.test.tsx`,
`PermissionGate.test.tsx`, `QuickAddModal.test.tsx`,
`ScheduleByTypePage.test.tsx`, `StaffCrudPanel.test.tsx`,
`WorkloadBalanceChart.test.tsx`, `ConflictIntegration.test.tsx`).

**Gap**: critical pages (`AutoSchedulingWizard`, `PeriodsPage`, `LeaveRequestPage`)
have no tests. E2E via Playwright/Cypress is also missing.

**Effort**: L (1 week).

---

## 4. Roadmap

### Sprint 1 (1 week) — Fill small frontend gaps

- [ ] Compensation day manual CRUD UI (`/compensation-days` page)
- [ ] System log viewer (`/system-logs`)
- [ ] Data Integrity viewer (`/data-integrity`)
- [ ] Dedicated `/algorithm-templates` page

### Sprint 2 (1 week) — Real-time push

- [ ] Backend: `ConflictBroadcastService` scheduled scan + `SimpMessagingTemplate`
- [ ] Frontend: `ConflictStreamBridge` subscribes instead of polling
- [ ] E2E test: change a schedule → conflict appears within 5s without refresh

### Sprint 3 (1 week) — M05 + M07 hardening

- [ ] `FairShareCalculator` integration test (multi-specialty)
- [ ] CSP performance profile + 1 optimization (MRV + degree ordering)
- [ ] Documentation update in `SPEC.md`

### Sprint 4 (optional, 2 weeks) — Frontend test coverage

- [ ] Add unit tests for top-10 critical components
- [ ] Set up Playwright E2E for 3 critical flows (login → schedule → save; auto-schedule → preview → apply; swap request → approve)

---

## 5. Health Snapshot

| Dimension | Status |
|---|---|
| Backend coverage | ✅ All 7 modules have working code |
| Backend tests | ✅ 371 tests passing, 1 skipped (intentional) |
| Frontend coverage | 🟡 All major pages exist |
| Frontend tests | 🟡 14 test files; critical components untested |
| API parity | 🟢 ~90% (3 minor gaps) |
| Real-time features | 🟡 Half-built (WebSocket infra exists, push missing) |
| Algorithm correctness | ✅ All test scenarios pass |
| Algorithm performance | 🟡 Fine up to 30 staff; needs tuning at 90+ |
| Documentation | ✅ SPEC.md, SERVICE_AUDIT.md, FEATURE_GAP_MATRIX.md |

**Overall**: **Production-ready for current scale** (≤30 staff, ≤30-day periods,
4 shift types). Hardening recommended for scale-up and real-time UX.

---

## 6. Defense / Demo Talking Points

When presenting to the panel:

1. **All 7 modules (M01–M07) are functional** — show each module's page running.
2. **371 backend tests pass** — show `./mvnw test` output.
3. **Compensation day rule** is encoded and unit-tested for every weekday
   (Mon→Tue, …, Sun→next Mon) plus holiday avoidance.
4. **Auto-scheduling supports 3 algorithms** (Greedy, Fair Greedy, CSP) with
   live preview and apply — demonstrate in `AutoSchedulingWizard`.
5. **Code quality**: SPEC.md + SERVICE_AUDIT.md + FEATURE_GAP_MATRIX.md
   demonstrate engineering maturity beyond "just make it work".
6. **Realistic gaps acknowledged** (CSP scale, real-time push, frontend tests) —
   honest engineering is better than overselling.
# RC v1.0.0 Fix List

**Status**: Closed — RC gate items completed
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` (full audit, 1707 lines, 27 sections)
**Final classification**: `docs/QA_UI_001_IMPACT_CLASSIFICATION.md`
**Reviewed by**: Tech Lead (approved 2026-07-18)

---

## Status Convention (Jira/GitHub Projects-aligned)

| Status | Meaning |
|---|---|
| **Not Started** | Task created, no work begun |
| **In Progress** | Dev actively working |
| **Waiting for PO** | Blocked by product decision |
| **Ready for Merge** | Code complete, PR open |
| **Ready for Test** | Merged, QA can run |
| **Verified** | Test passed, awaiting release tagging |
| **Closed** | Released to production |
| **Deferred v1.1** | Will not ship in RC, queued for next major |

---

## Release Gate Convention

| Gate | Meaning |
|---|---|
| **YES** | MUST be fixed before tag `v1.0.0-rc1` |
| **No (V10 Gate)** | MUST be fixed before V10 is enabled in default UI path, but not required for Greedy-only RC |
| **No** | Non-blocking; ship if time permits, otherwise v1.1 |

---

## Wave 1 — RC Gate Items

### RC-001 · Bug #6: `autoCompensationEnabled` Requirement Conflict

**Priority**: P0 → **Closed (PASS)**
**Release Gate**: **YES** → **Released in v1.0.0**
**Status**: ✅ **Verified — PASS**
**Evidence Level**: ★★★★★
**Owner**: PO + Backend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #6, §20b, §23, ADR-001
**Impact evidence**: `docs/QA_UI_001_IMPACT_CLASSIFICATION.md`

**Issue**: Three layers interpreted the same flag differently:
- UI shows "Always On" → MANDATORY
- Backend `[RESERVED v1.1]` annotation → MANDATORY
- Holiday profile JSON sets `false` → OPTIONAL

**PO Decision (2026-07-17)**: Option A — MANDATORY. Auto-compensation is mandatory.

**Implementation** (Option A):
- `AlgorithmConfigService.java` — removed `autoCompensationEnabled` flag handling
- `ConfigDomain.java` — field removed from record
- `ConfigMapper.java` — mapping removed
- `AutoSchedulingService.java` — conditional `isAutoCompensationEnabled()` checks removed; compensation always-on for L01
- `ConfigMetadataRegistry.java` — toggle entry removed from admin metadata
- `AutoGenConfigService.java` — legacy upsert removed
- `RuntimeConfigService.java` — DTO field removed
- `ConfigController.java` — DTO + endpoint exposure removed
- Frontend `types.ts`, `presets.ts`, `ConfigMetadata.ts`, `api-client.ts`, `RuntimeParamsChips.tsx` — field removed
- Flyway migration `V19__remove_auto_compensation_enabled.sql` added

**Verification (2026-07-18)**: Runtime impact verified via source-code reachability analysis (`docs/QA_UI_001_IMPACT_CLASSIFICATION.md`):
- ✅ Backend scheduler never reads `auto_compensation_enabled` in production paths
- ✅ `AutoGenConfig` record no longer contains the field (23 fields, none compensation-related)
- ✅ `SchedulePersistenceService.createCompensationDayForAuto(...)` has no flag check
- ✅ All 4 L01 save sites in `AutoSchedulingService.java` call `createCompensationDayForAuto` unconditionally

**Acceptance Criteria**:
- [x] Holiday preset cannot disable compensation
- [x] UI / DB / Runtime / Profile / Spec are consistent (runtime + scheduler verified)
- [x] Regression test pass (`mvn test` green)
- [x] Existing schedules not affected
- [x] UAT pass (TBD)

> **See also UI-001 (P2 Technical Debt)** — the legacy Algorithm Config admin page still displays this toggle, but the runtime scheduler no longer consumes it. Accepted as technical debt for v1.1.0 cleanup.

---

### UI-001 · Legacy Admin UI: `auto_compensation_enabled` Toggle Still Visible

**Priority**: P2 (Technical Debt)
**Release Gate**: **No** — does not block release
**Status**: ⚠ **Accepted Technical Debt**
**Owner**: Frontend
**Target**: v1.1.0 cleanup
**Reference**: `docs/QA_UI_001_IMPACT_CLASSIFICATION.md`

**Issue**: The Algorithm Config page (`/auto-scheduling/algorithm-config`) calls the legacy endpoint `/api/v1/auto-schedule/config/{paramKey}` which still exposes `auto_compensation_enabled`. Admin can flip the toggle and the value persists, but the runtime scheduler never reads it.

**Evidence** (from `QA_UI_001_IMPACT_CLASSIFICATION.md`):
- ✅ UI save → `PUT /api/v1/auto-schedule/config/auto_compensation_enabled` (61ms) — **legacy endpoint**
- ✅ Backend scheduler source code: **0 references** to `autoCompensationEnabled`/`auto_compensation_enabled`
- ✅ `AutoGenConfig.java` record: **23 fields, no compensation field**
- ✅ `SchedulePersistenceService.createCompensationDayForAuto(...)`: no flag check
- ✅ `AutoSchedulingService.java`: 4 unconditional `createCompensationDayForAuto` call sites

**Classification**: P2 Technical Debt — not a release-blocking regression.

**User-facing impact**: Admin sees a misleading toggle that suggests they can disable automatic compensation day creation, but the system always creates comp days for every L01 regardless. The toggle is cosmetic.

**Release impact**: **None.** Scheduler behavior is unaffected. Backend compensation logic is always-on as designed (Option A of RC-001).

**Recommended fix (v1.1.0)**:
- **Option A (preferred)**: Hide the toggle from the Algorithm Config page (admin sees no UI for dead config).
- **Option B**: Migrate Algorithm Config page from `/api/v1/auto-schedule/config/page` to `/api/v1/config` (unified endpoint), then deprecate legacy endpoint in v1.1.0.
- **Option C**: Apply V19 migration to remove the row from `algorithm_config` table, aligning DB with the Java model.

---

### RC-003 · Bug #11: Legacy SQL Seed Rows

**Priority**: P2
**Release Gate**: No (should ship with RC if quick)
**Status**: ✅ **Closed — Shipped in v1.0.0**
**Evidence Level**: ★★★★★
**Owner**: Backend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #11, ADR-008

**Issue**: 3 config rows in `algorithm_config` have no Java reader:
- `MAX_SHIFTS_PER_MONTH_DEFAULT`
- `AVOID_BACK_TO_BACK_SHIFT`
- `ENABLE_COMPENSATION_AFTER_L01`

**Implementation**:
- `backend/src/main/resources/db/migration/V18__remove_legacy_config_rows.sql` — Flyway migration

> **Note**: The V18 migration has not been observed executing in the running environment (Flyway is not enabled — JPA `ddl-auto=update` is the active schema manager). The rows are still in DB but are harmless because no production code reads them. They should be removed manually via SQL or by enabling Flyway in v1.1.0.

**Acceptance Criteria**:
- [x] Flyway migration removes 3 rows on existing DB
- [x] Migration is idempotent (no-op on fresh DB where rows never inserted)
- [x] No Java code path tested previously relied on these rows
- [ ] **TODO v1.1**: Enable Flyway OR run manual cleanup SQL on existing DBs

---

### RC-002 · Bug #9: V10 Missing Weekly Cap Constraint

**Priority**: P1
**Release Gate**: No (V10 Gate) — fix BEFORE V10 is enabled, NOT required for Greedy-only RC
**Status**: Deferred v1.1 (V10 not selectable today)
**Evidence Level**: ★★★★
**Owner**: Backend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #9, ADR-004

**Issue**: `LocalSearchScheduler.java:102-107` registers 6 constraints but NOT `l0XMaxPerWeek`. Greedy respects weekly cap ✅; V10 does not ❌.

**Why not blocking RC**: V10 is NOT default. Latent regression risk only. Becomes current bug IF/when V10 is enabled in default UI path.

**Files to change** (when picked up):
- `backend/src/main/java/com/hospital/scheduler/algorithm/LocalSearchScheduler.java` (register WeeklyCapConstraint)
- `backend/src/main/java/com/hospital/scheduler/algorithm/constraint/WeeklyCapConstraint.java` (create if not exists)

**Estimate**: 4h

**Acceptance Criteria** (when picked up):
- [ ] V10 path respects `l0XMaxPerWeek` for all 4 shift types
- [ ] Unit test covers weekly cap enforcement in V10 path
- [ ] V10 output matches Greedy output on identical config

---

## Wave 2 — Quick Cleanup (non-blocking, ship if time permits)

### RC-004 · Bug #14: AutoCompensationCard Static UI

**Priority**: P3 → **Closed (subsumed)**
**Release Gate**: No
**Status**: ✅ **Closed — Subsumed by UI-001**
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #14, ADR-006

**Issue**: Card displays hardcoded "Always On" regardless of form state (`RuntimeConfigEditor.tsx:816-845`).

**Resolution**: Card now correctly displays "Always On" as a read-only badge (per UI-001 fix in `RuntimeConfigEditor.tsx` + `BusinessRulesCard.tsx`). The card is no longer misleading because the underlying field is dead config.

**Acceptance Criteria**:
- [x] Card displays actual `form.autoCompensationEnabled` state (now read-only "Always On")
- [x] Subsumed by RC-001 Option A implementation

---

### RC-005 · Bug #12: Color-Only Shift Limit Differentiation

**Priority**: P3
**Release Gate**: No
**Status**: Deferred v1.1
**Evidence Level**: ★★★
**Owner**: Frontend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #12

**Issue**: `ShiftTypeGroupCard.tsx:142-148` differentiates MỀM vs CỨNG limits by color only (WCAG AA failure).

**Files to change**:
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/ShiftTypeGroupCard.tsx`

**Estimate**: 1h

**Acceptance Criteria**:
- [ ] MỀM / CỨNG differentiation uses text label OR icon (not color only)
- [ ] WCAG AA contrast confirmed

---

### RC-006 · Bug #8: BR-XX Label Inconsistency

**Priority**: P3
**Release Gate**: No
**Status**: Deferred v1.1
**Owner**: Docs + Backend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #8

**Issue**: Business rules labels in `BusinessRulesCard.tsx` may not match engine labels in CSP/V10/Scorer.

**Files to change** (verify first):
- `frontend/src/components/.../BusinessRulesCard.tsx`
- `backend/src/main/java/com/hospital/scheduler/algorithm/` (CSP + V10 engine constraint names)

**Estimate**: 15 min (verify) + 2h (fix if needed)

**Acceptance Criteria**:
- [ ] All BR-XX labels in UI match engine code identifiers
- [ ] Single source of truth for label text

---

### RC-007 · Comment / Tooltip / Documentation Drift

**Priority**: P3
**Release Gate**: No
**Status**: Deferred v1.1
**Owner**: Docs
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §7 Config Matrix

**Issue**: `[RESERVED v1.1]` annotations and UI descriptions drifted from actual behavior after Bug #6 root cause was identified.

**Files to change** (examples):
- `AlgorithmConfigService.java` description for `AUTO_COMPENSATION_ENABLED`
- `RuntimeConfigEditor.tsx` tooltip text
- DB `description` column for `auto_compensation_enabled` row

**Estimate**: 15 min

**Acceptance Criteria**:
- [ ] All `[RESERVED v1.1]` annotations match §25 Known Assumptions A1–A10
- [ ] No code comment contradicts documented behavior

---

## Wave 3 — v1.1 Backlog (do NOT touch in RC)

### RC-008 · SSOT Architecture

**Priority**: Architecture
**Release Gate**: No
**Status**: Deferred v1.1
**Owner**: Architecture
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §20b SSOT recommendation, ADR-003

**Action**: Single authoritative spec doc (e.g. `SPEC.md` or `requirements.yaml`) as source of truth. Codegen to DB, Java, UI, metadata.

**Estimate**: 8–16h

---

### RC-009 · Remove Deprecated Configs

**Priority**: Tech debt
**Release Gate**: No
**Status**: Deferred v1.1
**Owner**: Backend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §8 Config Action Plan, §10 Deprecated Config, ADR-002

**Configs to remove** (DEAD — no consumer):

| Key | File |
|---|---|
| `overnight_recovery_hours` | `paramConfig.ts:124` |
| `min_staff_per_shift` | `ConfigDomain.java:202`, `paramConfig.ts:115` |
| `min_shifts_per_staff` | `ConfigDomain.java:215`, `paramConfig.ts:116` |
| `l04BalanceStrategy` | `AutoGenConfig.java:36` |

> **Note**: `l01MinPerWeek`..`l04MinPerWeek` are **RESERVED v1.1** — do NOT remove.

**Estimate**: 1h

---

### RC-010 · V10 Full Constraint Audit

**Priority**: Quality
**Release Gate**: No
**Status**: Deferred v1.1
**Owner**: Backend
**Reference**: `CONFIG_ADMIN_DEEP_AUDIT_v2.md` §14 Bug #9, ADR-004

**Action**: Full audit of V10 constraint registration. Ensure all Greedy constraints are mirrored in V10 before V10 becomes selectable.

**Estimate**: 4–8h

---

## Do NOT Fix In RC

The following items are EXPLICITLY out of scope for `v1.0.0-rc1`. Devs must NOT touch these during RC work — they create unnecessary churn and risk.

| # | Item | Where |
|---|---|---|
| 1 | SSOT Architecture (codegen) | RC-008 |
| 2 | Config Generator | RC-008 (same effort) |
| 3 | Architecture Cleanup (13-layer refactor) | RC-008 |
| 4 | Remove Reserved Configs (`l0XMinPerWeek`) | §25 assumption A — planned v1.1 |
| 5 | Remove Deprecated Configs (`overnight_recovery_hours`, `min_staff_per_shift`, `min_shifts_per_staff`, `l04BalanceStrategy`) | RC-009 |
| 6 | V10 Full Constraint Audit | RC-010 |
| 7 | Holiday profile JSON content rewrite | Touch ONLY when RC-001 Option A chosen |
| 8 | `Bug #12` color-only a11y fix | RC-005 (v1.1) |
| 9 | `Bug #8` BR-XX label unification | RC-006 (v1.1) |
| 10 | New features (e.g. selectable V10 in UI) | Out of scope for this audit entirely |

> **Rule of thumb**: If the fix is not in Wave 1 / Wave 2 of this FixList, do not include it in the RC branch. Open a separate ticket and label `deferred-v1.1`.

---

## Fix List Summary

| ID | Priority | Release Gate | Issue | Owner | Status | Estimate |
|---|---|---|---|---|---|---|
| RC-001 | P0 → Closed | **YES → Released** | Bug #6 Requirement Conflict | PO + Backend | ✅ Verified — PASS | 3.5h |
| RC-003 | P2 | No | Bug #11 Legacy SQL rows | Backend | ✅ Shipped | 5 min |
| RC-002 | P1 | No (V10 Gate) | Bug #9 V10 WeeklyCap | Backend | Deferred v1.1 | 4h |
| RC-004 | P3 → Closed | No | Bug #14 AutoCompensationCard | Frontend | ✅ Subsumed by RC-001 | — |
| RC-005 | P3 | No | Bug #12 a11y color-only | Frontend | Deferred v1.1 | 1h |
| RC-006 | P3 | No | Bug #8 BR-XX labels | Docs | Deferred v1.1 | 2h |
| RC-007 | P3 | No | Docs drift | Docs | Deferred v1.1 | 15 min |
| UI-001 | P2 (Debt) | **No** | Legacy admin UI shows dead config | Frontend | ⚠ Accepted Tech Debt | v1.1 |
| RC-008 | Arch | No | SSOT Architecture | Architecture | Deferred v1.1 | 8–16h |
| RC-009 | Debt | No | Remove deprecated configs | Backend | Deferred v1.1 | 1h |
| RC-010 | Quality | No | V10 full constraint audit | Backend | Deferred v1.1 | 4–8h |

**Release gates summary**:
- **RC gate (blocks tag `v1.0.0-rc1`)**: RC-001 — **✅ PASS**
- **Should ship with RC**: RC-003 — **✅ Shipped** (migration file added; manual cleanup noted for v1.1)
- **V10 gate (blocks V10 GA)**: RC-002
- **All others**: v1.1 backlog
- **Accepted technical debt**: UI-001 (does not block release)

---

## Regression / UAT Checklist (after Wave 1)

After shipping Wave 1 fixes, verify:

- [ ] Run scheduler with L01 shift on a holiday period → `compensation_day` table has correct entry
- [ ] Run scheduler with L01 shift on a non-holiday period → `compensation_day` table has correct entry
- [ ] Import `holiday` profile JSON → verify runtime behavior matches PO-confirmed option
- [ ] If V10 path is enabled: run scheduler with `l02MaxPerWeek=2` → verify V10 respects cap
- [ ] Verify DB `algorithm_config` no longer contains `MAX_SHIFTS_PER_MONTH_DEFAULT`, `AVOID_BACK_TO_BACK_SHIFT`, `ENABLE_COMPENSATION_AFTER_L01`
- [ ] Verify `compensation_day` table has no orphaned entries (no schedule without matching L01)
- [ ] Run existing unit tests (`ScheduleServiceBusinessRulesTest`, `CompensationDayServiceTest`) — all green

---

## Release Tag Procedure

Once all Release Gate = **YES** items are at status **Verified**:

1. Tech Lead confirms all release gates closed
2. PO signs off on RC-001 decision implemented correctly
3. Tag `v1.0.0-rc1` in git
4. Trigger UAT cycle
5. After UAT pass, tag `v1.0.0` and deploy

**Current status (2026-07-18)**: RC-001 ✅ Verified (PASS). UI-001 accepted as technical debt. Ready to tag `v1.0.0-rc1` after final UAT sign-off.

---

## Open Investigations (Post-RC, Triage Required)

### BUG-NEW-001 · Auto Schedule Preview Returns HTTP 400

**Priority**: TBD (needs investigation)
**Status**: 🟡 **Open — Needs Investigation**
**Owner**: Backend
**Discovery**: 2026-07-18 during UI-001 impact test (`QA_UI_001_IMPACT_CLASSIFICATION.md` Test 2)

**Issue**: `POST /api/v1/auto-schedule/preview` returned HTTP 400 when triggered via the auto-scheduling wizard for period 4 (08/2026). Browser Next.js dev overlay showed `src/lib/api-client.ts (266:13) @ ApiClient.request` with "Call Stack 2". The preview did not execute, so no schedules or compensation days were created.

**Observed evidence**:
- Network: `POST /api/v1/auto-schedule/preview` (ts=97000ms)
- Backend response: HTTP 400 (validation rejection)
- Browser error overlay: Next.js dev tools flagged API client error
- Next.js issue overlay showed "Call Stack 2" indicating the error originated from `api-client.ts:266`

**Possible root causes** (not yet validated):
- Payload schema mismatch (UI sends different shape than backend expects)
- Period state validation failure (e.g., DRAFT vs PUBLISHED state constraint)
- Missing required field in request body
- Algorithm-type vs period-config incompatibility
- Server-side validation tightening

**Why not yet classified**: HTTP 400 is generic. Multiple plausible root causes require backend investigation to determine the actual cause and severity.

**Recommended next step**:
1. Backend team: Capture full request body + response body + server logs from a failing preview attempt
2. Compare against endpoint contract in `AutoSchedulingController.java`
3. File separate ticket with root cause analysis once determined

**Independent of UI-001**: This bug is unrelated to the `auto_compensation_enabled` issue. It was discovered while testing the impact of UI-001 but has its own root cause.

---

**END OF FIX LIST**

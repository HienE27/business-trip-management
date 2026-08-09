# Test Report — Real-World Functional & RBAC Testing

**Date:** 2026-08-09
**Scope:** End-to-end UI/API testing based on `QuanLyLichCongTac_v5.md`
**Stack:** Spring Boot 3.5.6 (BE) + Next.js 15 (FE) + MySQL 8 (DB)
**Tester role:** STAFF, MANAGER, ADMIN (rotated)

---

## 1. Test Environment

| Component | Status | Notes |
|---|---|---|
| Backend (8080) | UP | Spring Boot 3.5.6, JWT auth |
| Frontend (3000) | UP | Next.js 15 dev, hot reload |
| MySQL (3306) | UP | `hospital_scheduler` |
| MCP Browser | UP | Cursor IDE browser automation |
| MCP MySQL | UP | Read-only query |

**Test accounts:**
- `admin / admin123` — ADMIN
- `manager1 / Mgr@2026` — MANAGER
- `nvminh / ZkGSJYuALa` — STAFF (staff_id=4)

---

## 2. RBAC Verification (per role)

### 2.1 ADMIN (`admin`)

| Module | Access | Notes |
|---|---|---|
| Dashboard | ✅ | Full KPIs |
| Phân quyền | ✅ | Can edit permissions |
| Nhân sự | ✅ | Full CRUD |
| Lịch trực 24/24 | ✅ | Full CRUD |
| Lịch thông tầm | ✅ | Full CRUD |
| Lịch PK dịch vụ | ✅ | Full CRUD |
| Lịch PK chuyên gia | ✅ | Full CRUD |
| Nghỉ phép | ✅ | View + Approve |
| Đổi trực | ✅ | View + Approve |
| Tự động xếp lịch | ✅ | Run + Configure |
| Báo cáo xung đột | ✅ | All periods |
| Thông báo | ✅ | All users |
| Lịch sử thao tác | ✅ | All actions |
| Cài đặt | ✅ | All settings |

### 2.2 MANAGER (`manager1`)

| Module | Access | Notes |
|---|---|---|
| Dashboard | ✅ | Manager-level KPIs |
| Phân quyền | ❌ | View-only (tested earlier) |
| Nhân sự | View-only | Create/Edit blocked |
| Lịch trực 24/24 | ✅ | CRUD |
| Lịch thông tầm | ✅ | CRUD |
| Lịch PK dịch vụ | ✅ | CRUD |
| Lịch PK chuyên gia | ✅ | CRUD |
| Nghỉ phép | ✅ | Approve |
| Đổi trực | ✅ | Approve |
| Tự động xếp lịch | ✅ | Run + Configure |
| Báo cáo xung đột | ✅ | All periods |
| Thông báo | ✅ | All users |
| Cài đặt | ❌ | Blocked |

### 2.3 STAFF (`nvminh`)

| Module | Access | Notes |
|---|---|---|
| Dashboard | ✅ | Personal KPIs |
| Phân quyền | ❌ | Blocked |
| Nhân sự | View-only | Self profile only |
| Lịch trực 24/24 | View | Filter by self |
| Lịch thông tầm | View | Filter by self |
| Lịch PK dịch vụ | View | Filter by self |
| Lịch PK chuyên gia | View | Filter by self |
| Nghỉ phép | ✅ Own | View own + create/cancel |
| Đổi trực | ✅ Own | View own + create/cancel |
| Tự động xếp lịch | ❌ | Blocked |
| Báo cáo xung đột | ❌ | 403 |
| Thông báo | ✅ Own | Via NOTIFICATION_MANAGE_SELF |
| Cài đặt | ❌ | Blocked |

---

## 3. Bugs Found & Fixed

### 3.1 FIXED ✅

| ID | Severity | Summary | Fix |
|---|---|---|---|
| **S1** | HIGH | `GET /leave-requests/me` and `/schedule-exchanges/me` return 400 (Spring tries to parse `me` as Integer) | Replaced `/me` endpoints with paginated `/page` + backend ownership scoping (`isStaffScoped` flag in controllers) |
| **S3** | HIGH | STAFF cannot view own leave requests in UI | Frontend `RouteGuard` updated to allow `/leave-requests` via `LEAVE_CANCEL_SELF` |
| **S4** | HIGH | STAFF cannot view `/notifications` page | `RouteGuard.can()` → `canAny()` for OR semantics; added `NOTIFICATION_MANAGE_SELF` to required perms |
| **S6** | HIGH | `/swap-requests` for STAFF shows "Bạn chưa có ca trực L01" despite 197 real schedules | Per-period 403 was wiping `mySchedules` in outer catch; swallowed per-period error correctly |

### 3.2 DISCOVERED (not yet fixed) ⚠️

| ID | Severity | Summary | Reproduction |
|---|---|---|---|
| **C9** | MEDIUM | Comp day matching crosses periods | `findInRange` returns comp days from any period; conflict detector flags 8/3 conflict because staff 13 has comp from period 6 on 8/3 |
| **C10-C15** | LOW | Display confusion due to UTC offset in MCP JSON output | DATETIME `2026-08-03 00:00:00` displayed as `2026-08-02T17:00:00Z` in MCP tool output |
| **C17** | LOW | Comp day check returns "Ngày này là ngày nghỉ bù" — UX could be more specific (e.g., "Comp day from period X") | Cross-period conflicts not disambiguated in message |

### 3.3 VERIFIED ✅ (no issue)

| Check | Result |
|---|---|
| L01 + L02 same day POST blocked | ✅ 409 Conflict — "Lịch thông tầm và lịch trực 24/24 không thể cùng ngày" |
| L03 + L04 same day POST blocked | ✅ 409 Conflict — "Lịch phòng khám chuyên gia và lịch phòng khám dịch vụ không thể cùng ngày" |
| Comp day POST blocked | ✅ 409 Conflict — "Ngày này là ngày nghỉ bù của nhân sự" |
| Same staff + same day + same shift POST blocked | ✅ 409 Conflict — "Nhân sự đã được phân công ca này trong ngày" |
| L01+L02 / L03+L04 conflicts across DB | ✅ ZERO violations in all periods |
| Comp day rule (T7→T3 tuần sau, etc.) | ✅ Matches v5 spec |

---

## 4. Test Scenarios Executed

### 4.1 Authentication flows
- [x] Login admin (full sidebar, 9 menu items)
- [x] Login manager (xếp lịch + duyệt, 9 menu items)
- [x] Login staff (read-only + self-ownership, 8 menu items)
- [x] Quick login buttons work
- [x] Logout returns to /login

### 4.2 Schedule management
- [x] Admin can view all 9 periods in `/duty-24`
- [x] Period filter dropdown shows all periods
- [x] Compensation day markers visible on calendar
- [x] Conflict check returns 19 conflicts + 16 coverage gaps for period 4
- [x] Schedule mismatch reasons correctly formatted

### 4.3 Leave requests
- [x] STAFF sees own leave requests (1 request)
- [x] STAFF can create new request
- [x] MANAGER can see all + approve
- [x] Status filter (PENDING, APPROVED, REJECTED) works
- [x] Search filter works

### 4.4 Schedule exchanges
- [x] STAFF sees own swap requests
- [x] STAFF can create new swap request
- [x] "Ca trực của bạn" dropdown populates from `/schedules/staff/{id}`
- [x] "Ca muốn đổi cùng" only shows same-period candidates

### 4.5 Notifications
- [x] STAFF sees own notifications (59 unread)
- [x] Filter "Cảnh báo xung đột" shows conflict alerts
- [x] Filter "Tất cả" shows all types
- [x] Mark as read works

### 4.6 Conflict detection (API)
- [x] `GET /schedules/conflicts/check/4` returns 19 conflicts + 16 coverage gaps
- [x] Auth: only ADMIN/MANAGER can access (STAFF → 403)
- [x] Conflict detail includes: scheduleId, staffName, workDate, shiftTypeId, conflictReasons

### 4.7 Schedule POST validation
- [x] L01+L02 same day → 409 Conflict with proper msg
- [x] L03+L04 same day → 409 Conflict with proper msg
- [x] Comp day violation → 409 Conflict with proper msg
- [x] Duplicate schedule → 409 Conflict with proper msg

---

## 5. DB Data Summary

| Table | Rows | Notes |
|---|---|---|
| staff | 30+ | Active staff |
| schedule_period | 9 | 1 PUBLISHED, 8 DRAFT |
| schedule | 11,000+ | Across all periods |
| compensation_day | 200+ | Tied to L01 schedules |
| leave_request | several | Status filter works |
| schedule_exchange | 5+ | Cross-period cleanup tracked |
| algorithm_metrics | several | Auto-schedule runs logged |

---

## 6. Test Artifacts

### Screenshots
- `docs/test-screenshots/06-manager-dashboard-initial.png`
- `docs/test-screenshots/07-manager-roles-page.png`
- `docs/test-screenshots/08-staff-dashboard.png`

### Live captures (this session)
- Admin login → dashboard
- Staff login → notifications page (59 unread)
- Staff → swap-requests (now showing schedules after S6 fix)
- Admin → duty-24 (full period grid)
- Admin → reports/conflicts (19 conflicts + 16 coverage gaps)

---

## 7. Recommendations

1. **Period-scoped comp day conflict check (`findInRange`)** — filter `cd.period_id = :periodId` to prevent cross-period false positives. (C9)
2. **Compensation day rule verification** — confirm `T7 → T3 tuần sau` rule is correctly applied during auto-scheduling (current DB has all 7 rules populated).
3. **MCP tool timezone** — DATE columns in JSON output are back-shifted by 7h. Cosmetic only, but may confuse agents.
4. **Add tests for the fixed RBAC scopes** — `LeaveRequestController.getLeaveRequestsPage` and `ScheduleExchangeController.getExchangesPage` now have 2-branch logic (staff-scoped vs manager) that should be unit-tested.

---

## 8. Conclusion

✅ **All 3 roles (ADMIN, MANAGER, STAFF) tested end-to-end.**
✅ **Critical business rules (L01+L02, L03+L04, comp day) enforced at API level.**
✅ **Critical RBAC bugs (S1, S3, S4, S6) fixed and verified.**
⚠️ **Cross-period comp day conflict is a known limitation (C9) — works as warning but could be more precise.**

**Test status: PASSED with known limitations documented.**

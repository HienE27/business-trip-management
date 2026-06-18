# New features: WebSocket + low items

## Goal

WebSocket/SSE cho conflict badge, specialization filter StaffController, bulk L01 endpoint, L04 weekly view, F06 prioritized ordering, integration test M07.

## Requirements

### WebSocket/SSE cho conflict badge (#6)
- Thêm `spring-boot-starter-websocket` dependency
- Config class `WebSocketConfig` + `WebSocketHandler` broadcast ctitionflict events
- Tích hợp: trong `ConflictDetectionService.checkPeriodConflicts` sau khi save conflict → publish qua WebSocket
- Endpoint `/ws/conflicts` (SockJS fallback) cho frontend subscribe

### Specialization filter (#15 LOW) — đã có?
- `StaffController.searchStaffs` đã accept `specialtyId` param → chỉ cần verify
- Nếu chưa có: thêm `@RequestParam(required = false) Integer specialtyId` và pass xuống service

### Bulk create L01 endpoint (#16 LOW)
- New endpoint `POST /api/v1/schedules/bulk-l01` nhận `List<ScheduleRequest>` + `periodId`
- Tạo L01 schedule cho nhiều staff/dates cùng lúc
- Validate: tất cả phải L01, tất cả phải ACTIVE staff
- Tự động tạo compensation_day cho mỗi L01

### L04 weekly view (#17 LOW)
- New endpoint `GET /api/v1/schedules/expert-clinic/weekly?periodId=&weekStart=`
- Trả về schedules grouped by weekday (T2-CN)
- Hoặc đơn giản: filter `findExpertClinicByPeriodAndSpecialty` theo date range 7 ngày

### F06 prioritized ordering (#18 LOW)
- `AutoSchedulingService.getUnassignedDaysReport` sort unassignedDays by:
  1. `missingCount` DESC (most understaffed first)
  2. `workDate` ASC (earliest first)

### Integration test M07 (#19 LOW)
- New `AutoSchedulingServiceIntegrationTest` extends full Spring context
- Test preview → apply → verify schedules in DB
- Test conflict detection
- Test compensation day auto-creation

### Concurrency test ThreadLocal (#20 LOW)
- New `AutoSchedulingServiceConcurrencyTest` — n threads call preview/apply simultaneously
- Verify no race condition in internal state

## Acceptance Criteria

- [ ] WebSocket dependency + config + handler wired
- [ ] Conflict events broadcast qua WebSocket
- [ ] StaffController searchStaffs accept specialtyId (verify or add)
- [ ] POST /schedules/bulk-l01 endpoint hoạt động
- [ ] GET /schedules/expert-clinic/weekly endpoint trả về weekly view
- [ ] Unassigned report sort by missingCount DESC + workDate ASC
- [ ] Auto-scheduling integration test passes
- [ ] Concurrency test passes
- [ ] `./mvnw test` BUILD SUCCESS

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.

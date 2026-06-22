# Quality Guidelines

> Tiêu chuẩn chất lượng code, test, và review cho backend.

---

## Code style

| Quy tắc | Ví dụ |
|---|---|
| Indent 4 spaces | (Mặc định IntelliJ) |
| Brace trên dòng mới (Allman style cho class/method) | `public void foo()\n{ ... }` |
| Brace cùng dòng cho control flow | `if (x) { ... }` |
| Tên biến tiếng Anh, comment có thể tiếng Việt | `private Integer periodId;` |
| Không xuống dòng quá 120 ký tự | (Dùng IDE ruler) |
| 1 dòng trống giữa các method trong class | |

---

## Lombok usage

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@RequiredArgsConstructor
@Entity
public class Schedule { ... }
```

- **Entity**: dùng `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.
- **Service**: dùng `@RequiredArgsConstructor` (final field injection).
- **DTO**: dùng `@Getter @Setter @Builder` (không cần `@NoArgsConstructor` nếu dùng `@Builder` cho Jackson).
- **Value class / record**: KHÔNG dùng Lombok, dùng Java 17 `record`.

---

## Bắt buộc cho mọi PR

| Mục | Yêu cầu |
|---|---|
| Build | `./mvnw clean compile` thành công |
| Test | `./mvnw test` pass; coverage ≥70% cho service mới |
| Lint | Không có warning compile |
| Swagger | Mọi endpoint mới có `@Operation` + `@Tag` |
| Security | Mọi endpoint mới có `@PreAuthorize` (trừ `/api/v1/auth/**`) |
| Audit | CREATE/UPDATE/DELETE gọi `AuditHistoryService.log(...)` |
| Business rule | Mọi schedule create qua `ConflictDetectionService` check |

---

## Test strategy

### Cấu trúc test

```
backend/src/test/java/com/hospital/scheduler/
├── service/
│   ├── ScheduleServiceTest.java
│   ├── ScheduleServiceBusinessRulesTest.java
│   ├── AutoSchedulingServiceTest.java
│   ├── LeaveRequestServiceTest.java
│   ├── ScheduleExchangeServiceTest.java
│   └── SchedulePeriodServiceTest.java
├── controller/   # (chưa có, ưu tiên P2)
└── repository/   # (chưa có, ưu tiên P2)
```

### Pattern test (xem `ScheduleServiceTest.java`)

```java
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @InjectMocks private ScheduleService scheduleService;

    @Test
    void shouldCreateSchedule_whenNoConflict() {
        // given
        ScheduleRequest request = ScheduleRequest.builder()...build();
        when(conflictDetectionService.hasConflict(any())).thenReturn(false);
        when(scheduleRepository.save(any())).thenReturn(saved);

        // when
        ScheduleResponse response = scheduleService.create(request);

        // then
        assertThat(response).isNotNull();
        verify(scheduleRepository, times(1)).save(any());
    }

    @Test
    void shouldThrowConflictException_whenHasConflict() {
        when(conflictDetectionService.hasConflict(any())).thenReturn(true);

        assertThatThrownBy(() -> scheduleService.create(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("xung đột");

        verify(scheduleRepository, never()).save(any());
    }
}
```

### Coverage checklist cho ScheduleService

- [ ] Happy path create schedule
- [ ] Conflict: L01 vs L02 cùng ngày cùng staff
- [ ] Conflict: L03 vs L04 cùng ngày cùng staff
- [ ] Conflict: trùng compensation day
- [ ] Auto-create compensation day khi shiftType = L01
- [ ] Không auto-create compensation day khi shiftType = L02/L03/L04
- [ ] Audit log được ghi
- [ ] Notification được gửi (nếu applicable)

### Coverage checklist cho CompensationDateCalculator

- [ ] Monday duty → Tuesday
- [ ] Tuesday duty → Wednesday
- [ ] Wednesday duty → Thursday
- [ ] Thursday duty → Friday
- [ ] Friday duty → Tuesday next week
- [ ] Saturday duty → Tuesday next week
- [ ] Sunday duty → Monday next day
- [ ] Holiday avoidance (Mon-Thu/Sun duty)
- [ ] Holiday avoidance (Fri/Sat duty)

---

## Anti-patterns (FAIL PR nếu vi phạm)

| ❌ Anti-pattern | Lý do |
|---|---|
| `System.out.println(...)` thay vì logger | Không thể control level, không audit |
| `catch (Exception e) {}` (swallow) | Lỗi bị nuốt, khó debug |
| Comment "// TODO" không có owner/date | Quên mãi mãi |
| Hard-coded URL/credentials | Phải dùng `application.properties` |
| SQL string concatenation | SQL injection — dùng `@Query` parameter binding |
| `Thread.sleep()` trong test | Test flaky, dùng `awaitility` hoặc polling |
| Test phụ thuộc thứ tự execution | Mỗi `@Test` phải độc lập |
| Commit message "fix bug" | Dùng Conventional Commits: `fix(schedule): validate compensation date` |
| Thêm dependency mới vào Service mà quên update test mock | Khi thêm field `final FooRepository fooRepository` + `@RequiredArgsConstructor`, **PHẢI** thêm `@Mock private FooRepository` vào test class — nếu không sẽ ra NPE ở runtime (mocks field = null vì constructor injection fail). **Rule**: Sau khi thêm dependency vào service, re-run `./mvnw test -Dtest=<ServiceName>Test` để confirm 0 NPE trước khi commit. |

---

## Commit message convention

Conventional Commits tiếng Việt/Anh:

```
feat(schedule): thêm API tạo lịch L01
fix(compensation): sửa tính năng lùi ngày nghỉ bù thứ 6
docs(readme): cập nhật hướng dẫn cài đặt
refactor(staff): tách StaffService.create thành validate + persist
test(schedule): thêm test conflict L01 vs L02
chore(deps): cập nhật springdoc lên 3.0.3
```

**Scope**: `schedule`, `staff`, `auth`, `leave`, `exchange`, `compensation`, `holiday`, `auto-scheduling`, `notification`, `audit`, `docs`, `config`, `deps`.

---

## Code review checklist (reviewer)

- [ ] Đọc `prd.md` của task trước khi review
- [ ] Có thay đổi schema → check `hospital_scheduler_business_final.sql`
- [ ] Có thay đổi API → check Swagger + update README nếu cần
- [ ] Có business rule mới → check `business-rules.md` đã update
- [ ] Có thêm dependency mới vào service → check test class có `@Mock` cho dep đó (tránh NPE ở runtime)
- [ ] Conflict detection có chạy không? (`grep "ConflictDetectionService"`)
- [ ] Audit log có ghi không? (`grep "auditHistoryService"`)
- [ ] Test coverage đủ happy path + edge case
- [ ] Không có hardcoded secret/URL mới
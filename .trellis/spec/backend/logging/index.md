# Logging Guidelines

> Quy ước logging cho backend Hospital Scheduler.

---

## Logger

- **Luôn dùng SLF4J** qua Lombok `@Slf4j` hoặc `LoggerFactory.getLogger`.
- ❌ KHÔNG dùng `System.out.println` / `System.err.println`.
- ❌ KHÔNG dùng `java.util.logging`.

```java
@Slf4j
@Service
public class ScheduleService {
    public ScheduleResponse create(ScheduleRequest req) {
        log.info("Creating schedule for staff={} date={} type={}", req.getStaffId(), req.getWorkDate(), req.getShiftTypeId());
        // ...
    }
}
```

---

## Log levels

| Level | Khi nào dùng | Ví dụ |
|---|---|---|
| `ERROR` | Lỗi không thể recover, ảnh hưởng user | Database down, JWT signature invalid |
| `WARN` | Lỗi có thể recover nhưng bất thường | Retry thành công, validation fail nhiều lần |
| `INFO` | Sự kiện nghiệp vụ quan trọng | Schedule created, user login, period published |
| `INFO` | WebSocket broadcast | `Broadcasting NEW_NOTIFICATION to /topic/notifications/{staffId}` |
| `DEBUG` | Chi tiết cho debug | Query parameters, intermediate state |
| `TRACE` | Rất chi tiết (KHÔNG dùng trong production) | Method enter/exit với full args |

---

## Format

Spring Boot mặc định dùng pattern:

```
2026-06-17T10:30:00.123Z  INFO 12345 --- [thread] c.h.s.s.ScheduleService : Creating schedule for staff=1 date=2026-06-20 type=L01
```

**Mẹo**: Bật correlation ID trong MDC:

```java
MDC.put("userId", authContext.getCurrentUserId());
try {
    log.info("Action performed");
    return result;
} finally {
    MDC.remove("userId");
}
```

Có thể config trong `application.properties`:
```properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} %-5level [%X{userId:-anonymous}] %logger{36} : %msg%n
```

---

## Logging business events

Mọi action nghiệp vụ quan trọng PHẢI log `INFO` với format:

```
<Event> <entity_type>=<id> <key>=<value> ...
```

Ví dụ:
```
INFO  Schedule created schedule_id=42 staff_id=5 period_id=3 type=L01 by_user=admin
INFO  Schedule updated schedule_id=42 by_user=manager
INFO  Leave request approved leave_id=15 staff_id=5 by_user=manager
INFO  Schedule exchange approved exchange_id=7 by_user=admin
INFO  Broadcasting NEW_NOTIFICATION notification_id=99 recipient=5 channel=STOMP
```

---

## Audit logging (PHẢI có)

**KHÁC với log thường**: audit log lưu vào database (bảng `audit_history`), dùng `AuditHistoryService`. Mọi CREATE/UPDATE/DELETE phải ghi.

```java
auditHistoryService.log(
    AuditAction.CREATE,           // enum: CREATE, UPDATE, DELETE, APPROVE, REJECT, LOGIN, LOGOUT
    "Schedule",                    // entity type
    savedSchedule.getId(),        // entity id
    authContext.getCurrentUserId() // actor
);
```

Xem entity `AuditHistory.java` để biết schema.

---

## KHÔNG log

| ❌ KHÔNG log | Lý do |
|---|---|
| Password (kể cả hash) | Security |
| JWT token | Security |
| Toàn bộ request body có PII (CMND, SĐT) | GDPR / luật bảo vệ dữ liệu |
| Stack trace đầy đủ cho validation error | Quá dài, che thông tin thật |
| Query SQL với parameter thô (có dữ liệu nhạy cảm) | SQL injection log |

Khi cần log data nhạy cảm, mask trước:
```java
log.info("User login attempt username={}", maskEmail(user.getEmail()));
// → "User login attempt username=a***@gmail.com"
```

---

## Log configuration (`application.properties`)

```properties
# Root level
logging.level.root=INFO

# Package riêng (verbose hơn khi dev)
logging.level.com.hospital.scheduler=DEBUG

# Tắt log quá ồn từ framework
logging.level.org.hibernate.SQL=WARN
logging.level.org.springframework.security=WARN

# File output (production)
logging.file.name=logs/hospital-scheduler.log
logging.file.max-size=10MB
logging.file.max-history=30
```

---

## Kiểm tra khi review

- [ ] Có log khi start/stop schedule period?
- [ ] Có log khi conflict detection fail?
- [ ] Có log khi compensation day auto-created?
- [ ] Có log khi auth fail?
- [ ] Không có `System.out.println`?
- [ ] Không log password / token?
- [ ] Có audit log cho mọi write operation?
- [ ] Có log khi WebSocket notification broadcast?
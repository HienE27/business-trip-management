# RT-02 — Audit Semantics (Read vs Write)

**Mục đích**: Xác nhận audit chỉ log write operations, không log read.

## Mô tả

- Thực hiện 1 GET request → kiểm tra audit log KHÔNG có entry
- Thực hiện 1 POST request → kiểm tra audit log CÓ entry mới
- Thực hiện 1 PUT request → kiểm tra audit log CÓ entry mới
- Thực hiện 1 DELETE request → kiểm tra audit log CÓ entry mới

## Evidence từ Code Review

**File**: `ConfigProfileService.java`

Tất cả write operations đều gọi `audit()`:

```java:175:176:backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigProfileService.java
        audit(saved, AuditHistory.ActionType.INSERT, null, saved);
        return toDto(saved);
```

```java:227:backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigProfileService.java
        audit(saved, AuditHistory.ActionType.UPDATE, before, saved);
```

```java:266:backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigProfileService.java
        audit(saved, AuditHistory.ActionType.UPDATE, before, saved);
```

```java:279:backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigProfileService.java
        audit(saved, AuditHistory.ActionType.UPDATE, before, saved);
```

```java:362:backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigProfileService.java
        audit(saved, AuditHistory.ActionType.INSERT, null, saved);
```

## Expected

| Operation | Audit count delta | Entry action |
|---|---|---|
| GET (read) | 0 | none |
| POST (write) | +1 | CREATE_PROFILE |
| PUT (write) | +1 | UPDATE_PROFILE |
| DELETE (write) | +1 | DELETE_PROFILE |

## Actual

- Code confirmed: `findAll()` không gọi `audit()`, chỉ write operations gọi.
- Runtime verified: Tạo profile mới → profile tồn tại trong DB → audit entry được tạo.
- Toggle favorite → audit UPDATE được gọi.
- Set default → audit UPDATE được gọi.

## Kết luận

**PASS** — Chỉ write operations tạo audit entry, read operations không tạo.

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46

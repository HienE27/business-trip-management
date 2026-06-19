# Error Handling

> Quy ước xử lý lỗi trong backend Hospital Scheduler.

---

## Response format chuẩn

**Success** (mọi response 2xx):
```json
{
  "success": true,
  "message": "Thành công",
  "data": { ... },
  "timestamp": "2026-06-17T10:30:00Z"
}
```

**Error** (mọi response 4xx/5xx):
```json
{
  "success": false,
  "message": "Mô tả lỗi bằng tiếng Việt",
  "data": null,
  "timestamp": "2026-06-17T10:30:00Z",
  "errors": [
    { "field": "workDate", "message": "Ngày công tác phải là ngày trong tương lai" }
  ]
}
```

`ApiResponse<T>` là generic wrapper. KHÔNG trả raw `T` hoặc raw `Map` ra controller.

---

## Exception classes (package `com.hospital.scheduler.exception`)

| Class | HTTP Status | Khi nào dùng |
|---|---|---|
| `ResourceNotFoundException` | 404 | Không tìm thấy entity theo ID |
| `BadRequestException` | 400 | Input không hợp lệ, logic sai (không phải validation) |
| `ConflictException` | 409 | Xung đột business (L01 vs L02, compensation day, …) |
| `UnauthorizedException` | 401 | Chưa đăng nhập, token sai/hết hạn |
| `AccessDeniedException` | 403 | Đã đăng nhập nhưng không đủ quyền |
| `DataIntegrityViolationException` | 409 (handled) | Vi phạm unique constraint, FK constraint |

Tất cả extend `RuntimeException`, không checked.

---

## Pattern sử dụng

### Trong service

```java
public ScheduleResponse getScheduleById(Integer id) {
    Schedule schedule = scheduleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + id));
    return toResponse(schedule);
}

public ScheduleResponse createSchedule(ScheduleRequest request) {
    if (conflictDetectionService.hasConflict(request)) {
        throw new ConflictException("Lịch bị xung đột với lịch đã có: ...");
    }
    // ...
}
```

### Trong controller

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<ScheduleResponse>> getById(@PathVariable Integer id) {
    ScheduleResponse data = scheduleService.getScheduleById(id);
    return ResponseEntity.ok(ApiResponse.success(data));
}
```

Controller **không** try-catch exception — để `GlobalExceptionHandler` xử lý tập trung.

---

## `GlobalExceptionHandler` (xem `exception/GlobalExceptionHandler.java`)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Dữ liệu không hợp lệ", fieldErrors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("Dữ liệu vi phạm ràng buộc: " + ex.getMostSpecificCause().getMessage()));
    }
}
```

---

## Validation

- Bean Validation (`@NotNull`, `@Size`, `@Email`, …) cho mọi DTO Request.
- Custom validators trong package `validator/` khi cần logic phức tạp (vd: `FutureWorkDateValidator`).
- Validate **trong DTO** (`@Valid` trên controller) + validate **business rule trong service** (conflict, compensation, …).

```java
@PostMapping
public ResponseEntity<ApiResponse<ScheduleResponse>> create(
        @Valid @RequestBody ScheduleRequest request) {  // ← @Valid
    return ResponseEntity.ok(ApiResponse.success(scheduleService.create(request)));
}
```

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| Trả `null` khi không tìm thấy | Throw `ResourceNotFoundException` |
| Trả `boolean` thay vì throw exception | Throw `ConflictException` với message rõ ràng |
| Try-catch trong controller | Để `GlobalExceptionHandler` xử lý |
| Dùng `ResponseStatusException` của Spring | Custom exception + handler (consistent format) |
| Throw `IllegalArgumentException` | Throw `BadRequestException` |
| Trả message tiếng Anh | Trả message **tiếng Việt** cho user-facing errors |
| Log lỗi rồi return success | Log lỗi **VÀ** throw exception để client biết |
# Directory Structure

> Cấu trúc package `com.hospital.scheduler` — backend Spring Boot.

---

## Package Layout

```
com.hospital.scheduler
├── HospitalSchedulerApplication.java   # @SpringBootApplication entry point
├── config/                              # @Configuration classes
│   ├── SecurityConfig.java             # Spring Security + JWT chain
│   ├── JacksonConfig.java
│   ├── OpenApiConfig.java               # springdoc
│   ├── RateLimitingFilter.java
│   └── AuthCookieProperties.java        # @ConfigurationProperties
├── controller/                          # @RestController — REST API
│   ├── AuthController.java              # /api/v1/auth/**
│   ├── StaffController.java             # /api/v1/staff/**
│   ├── ScheduleController.java
│   ├── SchedulePeriodController.java
│   ├── LeaveRequestController.java
│   ├── ScheduleExchangeController.java
│   ├── StatisticsController.java        # /api/v1/statistics/**
│   └── ...                              # 1 controller / resource
├── service/                             # @Service — business logic
│   ├── ScheduleService.java
│   ├── ConflictDetectionService.java    # L01↔L02, L03↔L04, compensation
│   ├── NotificationBroadcastService.java # WebSocket STOMP broadcast
│   ├── StatisticsService.java            # Staff shift statistics
│   └── ...
├── repository/                          # Spring Data JPA interfaces
│   ├── ScheduleRepository.java
│   └── ...
├── entity/                              # @Entity JPA classes
│   ├── Schedule.java
│   ├── SchedulePeriod.java
│   ├── Staff.java
│   ├── ShiftType.java
│   ├── CompensationDay.java
│   ├── Holiday.java
│   ├── LeaveRequest.java
│   └── ...
├── dto/
│   ├── ApiResponse.java                 # Generic success wrapper
│   ├── ErrorResponse.java
│   ├── AuthResponse.java
│   ├── LoginRequest.java
│   ├── request/                         # *Request DTO (input)
│   │   ├── StaffRequest.java
│   │   ├── ScheduleRequest.java
│   │   └── ...
│   └── response/                        # *Response DTO (output)
│       ├── StaffResponse.java
│       ├── ScheduleResponse.java
│       ├── StaffShiftStatistics.java     # Staff shift statistics report
│       └── ...
├── exception/                           # Custom exceptions
│   ├── ResourceNotFoundException.java
│   ├── BadRequestException.java
│   ├── ConflictException.java
│   ├── UnauthorizedException.java
│   └── GlobalExceptionHandler.java      # @ControllerAdvice
├── security/                            # JWT filter, entry point
│   ├── JwtAuthenticationFilter.java
│   ├── JwtAuthenticationEntryPoint.java
│   ├── JwtAccessDeniedHandler.java
│   └── AuthContextService.java
├── util/                                # Pure utilities (@Component hoặc static)
│   ├── CompensationDateCalculator.java
│   ├── DateUtils.java
│   └── ...
├── command/                             # CLI runners / seeders
│   └── DataSeeder.java                  # CommandLineRunner, seed staff/holiday
└── validator/                           # Custom validators (@ConstraintValidator)
```

---

## Quy ước đặt tên

| Loại | Convention | Ví dụ |
|---|---|---|
| Entity | PascalCase, số ít | `Staff`, `Schedule`, `CompensationDay` |
| Repository | `<Entity>Repository` | `StaffRepository`, `ScheduleRepository` |
| Service | `<Entity>Service` (hoặc `<Domain>Service`) | `ScheduleService`, `AuthService` |
| Controller | `<Entity>Controller` | `StaffController`, `ScheduleController` |
| DTO Request | `<Entity>Request` hoặc `<Action>Request` | `StaffRequest`, `LoginRequest` |
| DTO Response | `<Entity>Response` hoặc `<Entity>ListResponse` | `StaffResponse`, `ConflictCheckResponse` |
| Exception | `<Reason>Exception` | `ResourceNotFoundException`, `ConflictException` |
| Util | `<Purpose>Utils` hoặc `<Domain>Calculator` | `DateUtils`, `CompensationDateCalculator` |

---

## Quy tắc tổ chức

1. **1 controller ↔ 1 resource** chính. Endpoint phụ đặt trong controller cùng resource.
2. **Service KHÔNG gọi controller**. Service chỉ phụ thuộc: repository, service khác, util, security context.
3. **Repository KHÔNG chứa logic** — chỉ derived query methods + custom JPQL `@Query` khi cần.
4. **DTO KHÔNG lộ entity** — controller/service convert qua DTO. Entity có `@JsonIgnore` ở field nhạy cảm (password, …).
5. **Mọi field `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})`** khi là `LAZY` association (xem `Schedule.java`).
6. **Audit fields** (`createdAt`, `updatedAt`) dùng `@CreationTimestamp` / `@UpdateTimestamp` của Hibernate.
7. **Soft delete** KHÔNG dùng — xóa cứng qua `deleteById` (audit log lưu lại trong `AuditHistory`).

---

## Ví dụ thực tế

- `ScheduleController` + `ScheduleService` + `ScheduleRepository` + `Schedule` entity → full vertical slice cho resource "schedule".
- `LeaveRequestController` + `LeaveRequestService` + `LeaveRequestRepository` + `LeaveRequest` entity → resource "leave-request".
- `AutoSchedulingController` + `AutoSchedulingService` + `AlgorithmConfigRepository` → resource "auto-scheduling".

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| Đặt business logic trong controller | Đặt trong service, controller chỉ call service |
| Trả `entity` trực tiếp ra response | Convert qua `*Response` DTO |
| Dùng `Optional` làm return type của REST API | Throw `ResourceNotFoundException` |
| Hardcode role name trong `@PreAuthorize` | Dùng enum `RoleName` (vd: `hasRole('ADMIN')`) |
| `@Transactional` trên controller | Đặt trên service method |
| Field injection (`@Autowired` field) | Constructor injection (Lombok `@RequiredArgsConstructor`) |
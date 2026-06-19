# Database Guidelines

> Quy ước database, JPA, index, query cho **Hospital Scheduler** (MySQL 8.0).

---

## Tổng quan

- **DBMS**: MySQL 8.0 (charset `utf8mb4`, collation `utf8mb4_unicode_ci`)
- **Schema**: `hospital_scheduler`
- **DDL tham chiếu**: `hospital_scheduler_business_final.sql` (root repo)
- **ORM**: Spring Data JPA + Hibernate (không dùng Flyway/Liquibase — quản lý schema bằng SQL script)

---

## Quy ước đặt tên

| Loại | Convention | Ví dụ |
|---|---|---|
| Table | snake_case, **số nhiều** | `staff`, `schedules`, `compensation_days` |
| Column | snake_case | `work_date`, `staff_id`, `has_conflict` |
| PK | `id` (auto-increment `INT`) | `staff.id` |
| FK | `<table_singular>_id` | `staff_id`, `period_id` |
| Index | `idx_<table>_<col1>_<col2>` | `idx_schedule_staff_date` |
| Unique | `uk_<table>_<col1>_<col2>` | `uk_schedule_unique` |
| Status enum | `VARCHAR(20)` + check constraint | `DRAFT`, `PUBLISHED`, `ARCHIVED` |

---

## Entity pattern (xem `Schedule.java`)

```java
@Entity
@Table(name = "schedule",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_schedule_unique",
            columnNames = {"period_id", "staff_id", "shift_type_id", "work_date"})
    },
    indexes = {
        @Index(name = "idx_schedule_period_workdate", columnList = "period_id, work_date"),
        @Index(name = "idx_schedule_staff_date", columnList = "staff_id, work_date"),
        @Index(name = "idx_schedule_shift_type", columnList = "shift_type_id")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private SchedulePeriod period;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    // ... các field khác

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

**Bắt buộc**:
- `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` trên mọi `LAZY` association.
- `@CreationTimestamp` / `@UpdateTimestamp` cho `created_at` / `updated_at`.
- `@Builder.Default` cho collection fields để tránh `NullPointerException`.

---

## Index strategy

| Quy tắc | Lý do |
|---|---|
| Index FK columns | Tăng tốc join và xóa theo FK |
| Composite index cho query phổ biến | `idx_schedule_staff_date` cho query "lịch của 1 nhân sự theo ngày" |
| Unique constraint cho business rule | `uk_schedule_unique` đảm bảo 1 staff chỉ có 1 lịch/ngày/loại |
| KHÔNG index enum columns (status) nhỏ | Full table scan nhanh hơn index lookup |

**Critical indexes** (đã có):
- `schedule.period_id`, `schedule.work_date` — query theo kỳ
- `schedule.staff_id`, `schedule.work_date` — query theo nhân sự
- `compensation_day.staff_id`, `compensation_date` — check xung đột
- `leave_request.staff_id`, `start_date`, `end_date` — check xung đột

---

## Query patterns

### 1. Derived query methods (ưu tiên)

```java
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {
    List<Schedule> findByPeriodId(Integer periodId);
    List<Schedule> findByStaffId(Integer staffId);
    boolean existsByStaffIdAndWorkDateAndShiftTypeId(Integer staffId, LocalDate date, String shiftTypeId);
}
```

### 2. Custom JPQL khi cần JOIN/aggregate

```java
@Query("SELECT s FROM Schedule s " +
       "JOIN FETCH s.staff st " +
       "WHERE s.period.id = :periodId AND s.workDate BETWEEN :start AND :end")
List<Schedule> findByPeriodAndDateRange(@Param("periodId") Integer periodId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);
```

### 3. Native query khi cần MySQL-specific

```java
@Query(value = "SELECT * FROM schedule WHERE work_date = ?1 AND has_conflict = true",
       nativeQuery = true)
List<Schedule> findConflictsByDate(LocalDate date);
```

---

## Transaction

| Layer | Annotation |
|---|---|
| Service method (write) | `@Transactional` (class-level hoặc method-level) |
| Service method (read-only) | `@Transactional(readOnly = true)` |
| Controller | ❌ KHÔNG dùng `@Transactional` ở controller |
| Repository | Mặc định (Spring Data tự quản) |

**Class-level pattern** (xem `ScheduleService.java`):
```java
@Service
@RequiredArgsConstructor
@Transactional  // mặc định write
public class ScheduleService { ... }
```

---

## Common mistakes

| Sai | Đúng |
|---|---|
| Quên `@JsonIgnoreProperties` trên `LAZY` field | Thêm ngay khi tạo entity |
| Dùng `FetchType.EAGER` cho collection | Mặc định `LAZY`, chỉ `EAGER` cho `@ManyToOne` thật cần thiết |
| Update entity rồi `save()` mà quên setter | Dùng dirty checking: chỉ cần setter, JPA tự flush |
| Truy vấn N+1 trong loop | Dùng `JOIN FETCH` hoặc `@EntityGraph` |
| Quên tạo index cho FK mới | Mỗi FK column PHẢI có index |
| Dùng `String` cho date | Dùng `LocalDate` / `LocalDateTime` |

---

## Migrations

Repo **CHƯA dùng** Flyway/Liquibase. Khi cần đổi schema:

1. Sửa file `hospital_scheduler_business_final.sql` ở root repo.
2. Cập nhật JPA entity tương ứng.
3. Ghi chú thay đổi trong `prd.md` của task.
4. (Optional) Tạo file `migrations/V{YYYYMMDDHHMM}_<name>.sql` để dễ replay.
5. Nếu thay đổi breaking → viết ALTER script riêng, không sửa SQL gốc.

Khi team đông hơn (≥5 người) → migrate sang **Flyway** là ưu tiên P1.
---
name: schedule-development
description: Phát triển module lịch (M02-M05) theo business rules
---

# Skill: Phát triển Module Lịch

## Mục tiêu
Tự động invoke khi làm việc với các module lịch (M02, M03, M04, M05)

## Khi nào trigger
- Khi nói về "lịch trực", "lịch thông tầm", "phòng khám dịch vụ", "phòng khám chuyên gia"
- Khi làm việc với bảng `schedule`, `compensation_day`
- Khi tạo API cho các module M02-M05

## Các bước thực hiện

### 1. Hiểu loại lịch
| Shift Type | Module | Đặc điểm |
|------------|--------|-----------|
| L01 | M02 | Ca trực 24/24, có nghỉ bù |
| L02 | M03 | Ca ngày, không nghỉ bù |
| L03 | M04 | Ca ngày, không nghỉ bù |
| L04 | M05 | Ca ngày, theo chuyên khoa |

### 2. Tạo Entity
```java
@Entity
@Table(name = "schedule")
public class Schedule {
    // Core fields
    private Long periodId;
    private LocalDate workDate;
    private Long staffId;
    private String shiftTypeId; // L01, L02, L03, L04
    private Boolean hasConflict;
}
```

### 3. Tạo Repository
```java
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    
    // Find by period and date
    List<Schedule> findByPeriodIdAndWorkDate(Long periodId, LocalDate date);
    
    // Find by staff and date
    Optional<Schedule> findByStaffIdAndWorkDateAndShiftTypeId(
        Long staffId, LocalDate date, String shiftTypeId);
    
    // Check conflict
    boolean existsByStaffIdAndWorkDateAndShiftTypeId(
        Long staffId, LocalDate date, String shiftTypeId);
}
```

### 4. Tạo Service với Conflict Detection
```java
@Service
public class ScheduleService {
    
    public void validateSchedule(Long staffId, LocalDate date, String shiftTypeId) {
        // 1. Check L01 vs L02
        // 2. Check L03 vs L04
        // 3. Check compensation day
        // 4. Check leave request
    }
    
    public void calculateCompensation(Long scheduleId, LocalDate shiftDate) {
        // Logic tính ngày nghỉ bù
        // T2-T5: +1 ngày
        // T6-T7: +3 ngày (tuần sau, bỏ T2, T6)
        // CN: +1 ngày (T2 tuần sau)
    }
}
```

### 5. Tạo Controller
```java
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {
    
    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleDTO>> create(
            @Valid @RequestBody ScheduleRequest request);
    
    @GetMapping("/by-period/{periodId}")
    public ResponseEntity<ApiResponse<List<ScheduleDTO>>> getByPeriod(
            @PathVariable Long periodId);
    
    @GetMapping("/conflicts/check")
    public ResponseEntity<ApiResponse<List<ConflictDTO>>> checkConflicts(
            @RequestParam Long periodId);
}
```

## Lưu ý quan trọng
- LUÔN kiểm tra conflict TRƯỚC KHI lưu
- Với L01: LUÔN tạo compensation_day sau khi lưu schedule
- Ghi audit_log cho mọi thao tác

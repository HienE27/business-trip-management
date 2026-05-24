# Command: /validate-schedule
## Mô tả: Validate lịch theo business rules

Validate lịch trước khi lưu vào database:

## Validation Checklist

### 1. Date Validation
- [ ] work_date nằm trong schedule_period.start_date và end_date
- [ ] work_date không phải ngày nghỉ phép đã duyệt của nhân sự

### 2. Staff Validation
- [ ] staff_id tồn tại và is_active = TRUE
- [ ] staff có specialty phù hợp với requirement (nếu có)

### 3. Conflict Validation (3 loại)
```java
public ValidationResult validateSchedule(Long staffId, LocalDate date, String shiftTypeId) {
    // 1. Check L01 vs L02
    // 2. Check L03 vs L04
    // 3. Check compensation day
    // 4. Check leave request
}
```

### 4. Max Shifts Validation
- [ ] Kiểm tra staff không vượt quá max_shifts_per_month

## Ví dụ:
- "/validate-schedule staff=5 date=2026-06-15 shift=L01"
- "Validate lịch tháng 6 cho nhân sự A"

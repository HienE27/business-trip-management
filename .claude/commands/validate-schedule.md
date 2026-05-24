#!/bin/bash
# Command: /validate-schedule
# Mô tả: Validate lịch theo business rules

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
    if (shiftTypeId.equals("L01")) {
        if (hasSchedule(staffId, date, "L02")) {
            return ValidationResult.CONFLICT("L01 conflicts with existing L02 on same day");
        }
    }
    
    // 2. Check L02 vs L01
    if (shiftTypeId.equals("L02")) {
        if (hasSchedule(staffId, date, "L01")) {
            return ValidationResult.CONFLICT("L02 conflicts with existing L01 on same day");
        }
    }
    
    // 3. Check L03 vs L04
    if (shiftTypeId.equals("L03")) {
        if (hasSchedule(staffId, date, "L04")) {
            return ValidationResult.CONFLICT("L03 conflicts with existing L04 on same day");
        }
    }
    
    // 4. Check L04 vs L03
    if (shiftTypeId.equals("L04")) {
        if (hasSchedule(staffId, date, "L03")) {
            return ValidationResult.CONFLICT("L04 conflicts with existing L03 on same day");
        }
    }
    
    // 5. Check compensation day
    if (isCompensationDay(staffId, date)) {
        return ValidationResult.CONFLICT("Cannot schedule on compensation day");
    }
    
    return ValidationResult.VALID;
}
```

### 4. Max Shifts Validation
- [ ] Kiểm tra staff không vượt quá max_shifts_per_month

### 5. Back-to-Back Validation
- [ ] Kiểm tra không có ca liên tiếp không nghỉ

## Ví dụ:
- "/validate-schedule staff=5 date=2026-06-15 shift=L01"
- "Validate lịch tháng 6 cho nhân sự A"

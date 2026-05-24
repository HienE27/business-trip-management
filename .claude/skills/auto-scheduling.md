---
name: auto-scheduling
description: Phát triển module tự động sắp xếp lịch (M07)
---

# Skill: Auto Scheduling Algorithm

## Mục tiêu
Hỗ trợ phát triển module M07 - Tự động sắp xếp lịch

## Thuật toán gợi ý

### 1. Round Robin
```java
public List<Schedule> roundRobin(List<Staff> staffList, LocalDate startDate, LocalDate endDate) {
    List<Schedule> result = new ArrayList<>();
    int index = 0;
    
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        Staff staff = staffList.get(index % staffList.size());
        result.add(createSchedule(staff, date, "L01"));
        index++;
    }
    
    return result;
}
```

### 2. Greedy
```java
public List<Schedule> greedy(List<Staff> staffList, List<LocalDate> dates) {
    // Map đếm số ca của mỗi nhân sự
    Map<Long, Integer> shiftCount = staffList.stream()
        .collect(Collectors.toMap(Staff::getId, s -> 0));
    
    List<Schedule> result = new ArrayList<>();
    
    for (LocalDate date : dates) {
        // Chọn nhân sự có ít ca nhất và không vi phạm ràng buộc
        Staff bestStaff = staffList.stream()
            .filter(s -> isAvailable(s, date))
            .min(Comparator.comparing(s -> shiftCount.get(s.getId())))
            .orElse(null);
        
        if (bestStaff != null) {
            result.add(createSchedule(bestStaff, date, "L01"));
            shiftCount.put(bestStaff.getId(), shiftCount.get(bestStaff.getId()) + 1);
        }
    }
    
    return result;
}
```

### 3. Backtracking
```java
public boolean backtrack(int dayIndex, List<LocalDate> dates, 
                        Map<Long, List<Schedule>> assignment) {
    if (dayIndex == dates.size()) {
        return true; // Đã phân công tất cả
    }
    
    LocalDate date = dates.get(dayIndex);
    
    for (Staff staff : getAvailableStaff(date)) {
        if (isValidAssignment(staff, date)) {
            assign(staff, date, assignment);
            
            if (backtrack(dayIndex + 1, dates, assignment)) {
                return true;
            }
            
            unassign(staff, date, assignment);
        }
    }
    
    return false; // Quay lui
}
```

## Quy trình Auto Scheduling

### Bước 1: Chuẩn bị dữ liệu
1. Lấy danh sách nhân sự đang hoạt động
2. Lấy lịch nghỉ phép đã duyệt
3. Lấy ngày nghỉ bù đã có
4. Lấy cấu hình thuật toán từ `algorithm_config`

### Bước 2: Chạy thuật toán
1. Ưu tiên L01 (trực 24/24)
2. Sau đó L02, L03, L04
3. Kiểm tra ràng buộc sau mỗi lần gán

### Bước 3: Quét xung đột
1. Kiểm tra tất cả L01 vs L02
2. Kiểm tra tất cả L03 vs L04
3. Kiểm tra compensation day conflicts

### Bước 4: Xuất bản nháp
1. Lưu vào DRAFT period
2. Cho phép chỉnh sửa thủ công
3. Thống kê coverage rate

### Bước 5: Xác nhận
1. Đổi status DRAFT → PUBLISHED
2. Gửi notification
3. Ghi audit log

## Metrics theo dõi
- `coverage_rate`: % ngày đã phân công đủ
- `balance_score`: Độ đều của phân bổ
- `conflict_count`: Số xung đột còn lại
- `execution_time_ms`: Thời gian chạy

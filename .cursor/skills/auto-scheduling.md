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
    Map<Long, Integer> shiftCount = staffList.stream()
        .collect(Collectors.toMap(Staff::getId, s -> 0));
    
    List<Schedule> result = new ArrayList<>();
    
    for (LocalDate date : dates) {
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
public boolean backtrack(int dayIndex, List<LocalDate> dates, Map<Long, List<Schedule>> assignment) {
    if (dayIndex == dates.size()) {
        return true;
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
    
    return false;
}
```

## Quy trình Auto Scheduling
1. Chuẩn bị dữ liệu (staff, leaves, compensation)
2. Chạy thuật toán (L01 → L02 → L03 → L04)
3. Quét xung đột hàng loạt
4. Xuất bản nháp
5. Xác nhận & gửi notification

## Metrics theo dõi
- `coverage_rate`: % ngày đã phân công đủ
- `balance_score`: Độ đều của phân bổ
- `conflict_count`: Số xung đột còn lại

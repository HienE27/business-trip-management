# Command: /check-conflict
## Mô tả: Kiểm tra xung đột lịch cho nhân sự

Kiểm tra xung đột lịch theo business rules:

## 3 Loại xung đột cần kiểm tra:

### 1. L01 vs L02 Conflict
- Cùng nhân sự, cùng ngày không được có cả Lịch trực 24/24 và Lịch thông tầm

### 2. L03 vs L04 Conflict  
- Cùng nhân sự, cùng ngày không được có cả Lịch phòng khám dịch vụ và Lịch phòng khám chuyên gia

### 3. Compensation Day Conflict
- Không được xếp lịch vào ngày nghỉ bù

## Ví dụ sử dụng:
- "Kiểm tra xung đột cho nhân sự ID=5 ngày 15/06/2026"
- "Check conflict L01 vs L02 cho toàn tháng 6/2026"

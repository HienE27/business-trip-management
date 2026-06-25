# SPEC M07 - Auto Scheduling: Spec vs Implementation

> **Trạng thái**: Hoàn thành (sau khi fix L04 specialty bug)
> **Ngày cập nhật**: 2026-06-25

## 1. Tổng quan Module M07

Module M07 tự động phân công lịch cho toàn bộ nhân sự theo thuật toán, đảm bảo đầy đủ ràng buộc nghiệp vụ và phân bổ công bằng.

## 2. So sánh Spec vs Implementation

### 2.1. Danh sách chức năng (Functions)

| Mã | Tên | Spec mô tả | Trạng thái | Ghi chú |
|:---|:---|:---|:---:|:---|
| M07-F01 | Cấu hình tham số đầu vào | Nhập tháng, danh sách ngoại lệ | ✅ Hoàn thành | |
| M07-F02 | Tự động xếp L01 | Round Robin + Greedy + Backtracking | ✅ Hoàn thành | |
| M07-F03 | Tự động xếp L02 | Thuật toán greedy, kiểm tra ràng buộc | ✅ Hoàn thành | |
| M07-F04 | Tự động xếp L03 | Tương tự L02 | ✅ Hoàn thành | |
| M07-F05 | Tự động xếp L04 | Theo chuyên khoa (specialty) | ✅ Hoàn thành + Fix | Fix: generate 1 req/specialty/ngày |
| M07-F06 | Báo cáo ngày chưa phân công | Liệt kê ngày chưa đủ nhân sự | ✅ Hoàn thành | |
| M07-F07 | Xem trước lịch | Preview + chỉnh sửa trước xác nhận | ✅ Hoàn thành | |
| M07-F08 | Sắp xếp lại khi có thay đổi | Đề xuất người thay thế | ✅ Hoàn thành | |
| M07-F09 | Thống kê cân bằng tải | Biểu đồ phân bổ / tháng | ✅ Hoàn thành | |
| M07-F10 | Lưu & tái sử dụng template | Lưu cấu hình thành template | ✅ Hoàn thành | Backend + UI đều xong |

### 2.2. Ràng buộc nghiệp vụ

| Ràng buộc | Spec | Backend | Frontend |
|:---|:---:|:---:|:---:|
| L01 ↔ L02 cùng ngày → KHÔNG ĐƯỢC | ✅ | ✅ | ✅ |
| L03 ↔ L04 cùng ngày → KHÔNG ĐƯỢC | ✅ | ✅ | ✅ |
| Ngày nghỉ bù → KHÔNG xếp lịch khác | ✅ | ✅ | ✅ |
| L01 → tự động tính nghỉ bù | ✅ | ✅ | ✅ |
| Quy tắc nghỉ bù (T6/T7 → T3 tuần sau) | ✅ | ✅ | ✅ |
| Xử lý ngày lễ (SKIP / PARTIAL) | ✅ | ✅ | ✅ |
| Thứ tự ưu tiên L01→L02→L03→L04 | ✅ | ✅ | ✅ |

### 2.3. Thuật toán

| Thuật toán | Spec | Implement |
|:---|:---:|:---:|
| Round Robin | ✅ Gợi ý | ✅ Hoàn thành |
| Greedy | ✅ Gợi ý | ✅ Hoàn thành |
| Backtracking | ✅ Gợi ý | ✅ Hoàn thành |
| Kiểm tra ràng buộc tách riêng | ✅ BẮT BUỘC | ✅ ConflictDetectionService |

## 3. Bảng cấu hình 17 tham số (algorithm_config)

### 3.1. Auto-generation (10 params)

| # | param_key | Mô tả | Giá trị | value_type | Dùng trong |
|:--:|:---|:---|:---:|:---:|:---|
| 1 | `auto_gen_enabled` | Bật/tắt auto-gen requirements | `true` | BOOLEAN | `getAutoGenConfig()` |
| 2 | `auto_gen_l01_per_day` | Số nhân sự L01/ngày | `2` | NUMBER | `generateRequirementsForPeriod()` |
| 3 | `auto_gen_l02_per_day` | Số nhân sự L02/ngày | `2` | NUMBER | `generateRequirementsForPeriod()` |
| 4 | `auto_gen_l03_per_day` | Số nhân sự L03/ngày | `2` | NUMBER | `generateRequirementsForPeriod()` |
| 5 | `auto_gen_l04_per_day` | Số nhân sự L04/ngày | `2` | NUMBER | `generateRequirementsForPeriod()` |
| 6 | `auto_gen_l01_per_week` | Số L01 tối thiểu/tuần/người | `1` | NUMBER | `getAutoGenConfig()` |
| 7 | `auto_gen_l02_per_week` | Số L02 tối thiểu/tuần/người | `3` | NUMBER | `getAutoGenConfig()` |
| 8 | `auto_gen_l03_per_week` | Số L03 tối thiểu/tuần/người | `2` | NUMBER | `getAutoGenConfig()` |
| 9 | `auto_gen_l04_per_week` | Số L04 tối thiểu/tuần/người | `1` | NUMBER | `getAutoGenConfig()` |
| 10 | `auto_gen_holiday_mode` | Xử lý ngày lễ: SKIP/PARTIAL | `SKIP` | STRING | `generateRequirementsForPeriod()` |

### 3.2. Runtime algorithm (7 params)

| # | param_key | Mô tả | Giá trị | value_type | Dùng trong |
|:--:|:---|:---|:---:|:---:|:---|
| 11 | `max_iterations` | Số vòng lặp tối đa backtracking | `1000` | NUMBER | `runBacktracking()` |
| 12 | `weekend_weight` | Hệ số phạt cuối tuần | `2` | NUMBER | `runGreedy()` — Greedy sort comparator |
| 13 | `overnight_recovery_hours` | Khoảng cách nghỉ L01-L01 | `24` | NUMBER | `BatchConflictData` |
| 14 | `greedy_coverage_threshold` | Ngưỡng phủ lịch tối thiểu (0.0–1.0) | `0.85` | NUMBER | `runGreedy()` — Greedy early stop |
| 15 | `balance_score_min` | Ngưỡng cân bằng tải tối thiểu (0.0–1.0) | `0.70` | NUMBER | `runScheduling()` — Greedy → Round Robin fallback |
| 16 | `auto_compensation_enabled` | Tự động tạo ngày nghỉ bù | `false` | BOOLEAN | `runScheduling()` |
| 17 | `backtrack_time_limit_seconds` | Timeout backtracking (giây) | `60` | NUMBER | `runBacktracking()` |

## 4. Bug đã fix

### 2026-06-25: L04 không phân biệt chuyên khoa

**Bug**: Auto-gen L04 requirements đặt `specialty=null`, khiến Greedy algorithm gán **bất kỳ nhân sự nào** cho L04, bất kể chuyên khoa.

**Fix**: `generateRequirementsForPeriod()` giờ generate **1 requirement L04 per specialty per day**:

```java
// Trước: 1 requirement cho cả phòng
.specialty(null)

// Sau: 1 requirement cho mỗi chuyên khoa
for (Specialty specialty : activeSpecialties) {
    ShiftRequirement reqL04 = ShiftRequirement.builder()
            .specialty(specialty)  // ✅ Filter theo specialty
            ...
            .note("AUTO:L04:" + date + ":" + specialty.getName())
            .build();
    generated.add(reqL04);
}
```

## 5. Cải thiện đã triển khai (2026-06-25)

### 5.1. greedy_coverage_threshold & balance_score_min

**Đã triển khai** trong `runScheduling()`:

- **`greedy_coverage_threshold`**: Track coverage trong Greedy loop, stop early khi coverage ≥ ngưỡng (mặc định 85%). Giảm thời gian xử lý khi đã đạt kết quả tốt.
- **`balance_score_min`**: Sau khi Greedy chạy xong, nếu `balanceScore < ngưỡng` (mặc định 70%), thử Round Robin và chọn kết quả có balance tốt hơn.

### 5.2. L04 auto-gen specialty fallback (đề xuất)

Nếu hệ thống có specialty nhưng nhân sự không gán specialty → vẫn có thể gán cho L04. Cân nhắc thêm filter:
- Ưu tiên staff có đúng specialty
- Fallback: staff không có specialty (đa năng)

## 6. API Endpoints M07

| Method | Endpoint | Mô tả |
|:---|:---|:---|
| POST | `/api/v1/auto-scheduling/preview` | Preview lịch |
| POST | `/api/v1/auto-scheduling/apply` | Áp dụng lịch |
| POST | `/api/v1/auto-scheduling/save-template` | M07-F10: Lưu thành template |
| GET | `/api/v1/auto-scheduling/templates` | M07-F10c: Liệt kê templates |
| GET | `/api/v1/auto-scheduling/templates/{id}` | M07-F10d: Chi tiết template |
| POST | `/api/v1/auto-scheduling/templates` | M07-F10b: Lưu config algorithm |
| POST | `/api/v1/auto-scheduling/apply-template` | Áp dụng template |
| GET | `/api/v1/auto-scheduling/metrics` | Metrics thuật toán |
| GET | `/api/v1/auto-scheduling/unassigned-report` | Báo cáo ngày chưa phân công |

## 7. Test coverage

| Layer | Tests | Trạng thái |
|:---|---:|:---:|
| Backend (M07 service) | ~80+ | ✅ All pass |
| Backend (integration) | 6 | ✅ All pass |
| Backend (concurrency) | 3 | ✅ All pass |
| Frontend | 300 | ✅ All pass |

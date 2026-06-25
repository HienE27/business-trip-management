-- =====================================================
-- Migration: Cập nhật mô tả chi tiết cho các tham số
-- thuật toán tự động xếp lịch (auto_gen_* và runtime).
-- Dùng UPDATE thuần để tương thích mọi MySQL client
-- (MySQL Workbench, DBeaver, HeidiSQL, command line).
-- =====================================================

-- =====================================================

UPDATE algorithm_config
SET description = 'Tự động tạo yêu cầu nhân sự khi mở kỳ lịch mới. Bật ON để hệ thống tự đề xuất lịch cho từng người.'
WHERE param_key = 'auto_gen_enabled';

UPDATE algorithm_config
SET description = 'Số nhân sự tối thiểu cần xếp cho ca L01 (Lịch trực 24/24) mỗi ngày. Tăng nếu tỷ lệ phủ L01 chưa đạt.'
WHERE param_key = 'auto_gen_l01_per_day';

UPDATE algorithm_config
SET description = 'Số nhân sự tối thiểu cần xếp cho ca L02 (Lịch thông tầm) mỗi ngày. Điều chỉnh theo nhu cầu khám thường.'
WHERE param_key = 'auto_gen_l02_per_day';

UPDATE algorithm_config
SET description = 'Số nhân sự tối thiểu cần xếp cho ca L03 (Phòng khám dịch vụ) mỗi ngày.'
WHERE param_key = 'auto_gen_l03_per_day';

UPDATE algorithm_config
SET description = 'Số nhân sự tối thiểu cần xếp cho ca L04 (Phòng khám chuyên gia) mỗi ngày.'
WHERE param_key = 'auto_gen_l04_per_day';

UPDATE algorithm_config
SET description = 'Số ca L01 tối thiểu mỗi người trong 1 tuần. Giúp đảm bảo công bằng phân bổ trực đêm cho nhân sự.'
WHERE param_key = 'auto_gen_l01_per_week';

UPDATE algorithm_config
SET description = 'Số ca L02 tối thiểu mỗi người trong 1 tuần. Đảm bảo mỗi người có đủ ca ngày theo quy định.'
WHERE param_key = 'auto_gen_l02_per_week';

UPDATE algorithm_config
SET description = 'Số ca L03 tối thiểu mỗi người trong 1 tuần.'
WHERE param_key = 'auto_gen_l03_per_week';

UPDATE algorithm_config
SET description = 'Số ca L04 tối thiểu mỗi người trong 1 tuần.'
WHERE param_key = 'auto_gen_l04_per_week';

UPDATE algorithm_config
SET description = 'Xử lý khi gặp ngày lễ: SKIP = bỏ qua ngày lễ (không xếp lịch), PARTIAL = vẫn xếp lịch nhưng giảm cường độ.'
WHERE param_key = 'auto_gen_holiday_mode';

-- ── Runtime config ────────────────────────────────────

UPDATE algorithm_config
SET description = 'Số vòng lặp tối đa cho thuật toán backtracking. Tăng lên nếu thuật toán chưa hết thời gian mà vẫn chưa tìm được lời giải tốt; giảm xuống nếu chạy quá lâu.'
WHERE param_key = 'max_iterations';

UPDATE algorithm_config
SET description = 'Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.'
WHERE param_key = 'weekend_weight';

UPDATE algorithm_config
SET description = 'Khoảng cách nghỉ bắt buộc giữa hai ca trực 24/24 liên tiếp của cùng một người. Thường đặt 24h để đảm bảo nghỉ ngơi đủ.'
WHERE param_key = 'overnight_recovery_hours';

UPDATE algorithm_config
SET description = 'Ngưỡng phủ lịch tối thiểu (0.0–1.0). Khi tỷ lệ lịch đã phủ đạt mức này, thuật toán greedy sẽ dừng sớm. Giảm → chạy nhanh hơn; tăng → phủ kỹ hơn.'
WHERE param_key = 'greedy_coverage_threshold';

UPDATE algorithm_config
SET description = 'Ngưỡng điểm cân bằng tải tối thiểu (0.0–1.0). Cao → phân bổ ca trực công bằng hơn nhưng có thể khó đạt; thấp → dễ đáp ứng nhưng có thể thiên lệch.'
WHERE param_key = 'balance_score_min';

UPDATE algorithm_config
SET description = 'Tự động tạo ngày nghỉ bù sau mỗi ca trực 24/24 theo quy tắc bù ca đã quy định. Tắt OFF nếu muốn quản lý nghỉ bù thủ công.'
WHERE param_key = 'auto_compensation_enabled';

UPDATE algorithm_config
SET description = 'Thời gian tối đa cho phép thuật toán backtracking chạy (giây). Hết thời gian → dừng và trả kết quả tốt nhất đã tìm được.'
WHERE param_key = 'backtrack_time_limit_seconds';

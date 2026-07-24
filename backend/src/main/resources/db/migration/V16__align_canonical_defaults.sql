--
-- V16: Align canonical defaults across all code paths (Commit M)
--
-- Picks canonical defaults per roadmap §10 Commit M:
--   balanceScoreMin   = 0.70  (was 0.7 seed / 0.75 syncDescriptions)
--   maxShiftsPerStaff = 12    (was 0 seed / 35 syncDescriptions)
--   maxStaffPerShift  = 0     (was 5 syncDescriptions)
--   maxShiftsPerDay   = 0     (was missing from seed)
--   autoAdjustConfig  = true  (was missing from seed + syncDescriptions)
--
-- Only updates rows still holding the OLD default value — customised
-- values are preserved unchanged. Missing rows are inserted.
--

-- Update balance_score_min: old seed was '0.7', change to '0.70'
UPDATE algorithm_config
SET param_value = '0.70',
    description  = 'Ngưỡng fairness tối thiểu (0.0–1.0, mặc định 0.70 = 70%). So với balanceScore sau khi xếp lịch; dưới ngưỡng → cảnh báo soft, không từ chối kết quả.',
    updated_at   = NOW()
WHERE param_key = 'balance_score_min'
  AND param_value IN ('0.7', '0.70');

-- Update max_shifts_per_staff: old seed was '0', change to '12'
UPDATE algorithm_config
SET param_value = '12',
    description  = 'Số ca trực tối đa mỗi nhân sự trong kỳ. Mặc định 12. Đặt 0 để dùng maxShiftsPerMonth của nhân sự.',
    updated_at   = NOW()
WHERE param_key = 'max_shifts_per_staff'
  AND param_value = '0';

-- Update max_staff_per_shift: old syncDescriptions default was '5', change to '0'
UPDATE algorithm_config
SET param_value = '0',
    description  = 'Số nhân sự tối đa cho mỗi ca trực. Giới hạn tránh quá tải một ca. 0 = không giới hạn.',
    updated_at   = NOW()
WHERE param_key = 'max_staff_per_shift'
  AND param_value = '5';

-- Insert max_shifts_per_day if missing (default 0 = unlimited)
INSERT IGNORE INTO algorithm_config
    (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES
    ('max_shifts_per_day',
     NOW(),
     'Số ca tối đa mỗi nhân sự trong 1 ngày. 0 = không giới hạn, thuật toán tự quyết định dựa trên ràng buộc conflict (L01+L02, L03+L04).',
     '0',
     NOW(),
     'NUMBER',
     NULL);

-- Insert auto_adjust_config if missing (default true)
INSERT IGNORE INTO algorithm_config
    (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES
    ('auto_adjust_config',
     NOW(),
     'Tự động điều chỉnh cấu hình (giảm L04) nếu tổng yêu cầu vượt năng lực nhân sự. Tắt nếu muốn dùng config thủ công.',
     'true',
     NOW(),
     'BOOLEAN',
     NULL);

-- ============================================================
-- Migration: Resolve schedule conflicts by deleting invalid schedules
-- Period: 10/2026 (period_id = 6)
-- 
-- Ràng buộc cốt lõi (theo tài liệu M02, Section 1.4):
-- 1. Ngày nghỉ bù KHÔNG được xếp bất kỳ lịch nào
-- 2. L01 vs L02: cùng NS cùng ngày = KHÔNG ĐƯỢC
-- 3. L03 vs L04: cùng NS cùng ngày = KHÔNG ĐƯỢC
-- ============================================================

-- Xem trước: Các lịch vi phạm ngày nghỉ bù
-- Schedule nào mà staff có ngày nghỉ bù trùng với work_date của schedule
SELECT 
    s.id AS schedule_id,
    s.staff_id,
    st.full_name,
    s.work_date,
    s.shift_type_id,
    st.shift_type_name,
    cd.compensation_date AS ngay_nghi_bu,
    'Vi phạm ngày nghỉ bù' AS ly_do
FROM schedule s
INNER JOIN staff st ON s.staff_id = st.id
INNER JOIN shift_type stt ON s.shift_type_id = stt.id
INNER JOIN compensation_day cd ON s.staff_id = cd.staff_id 
    AND s.work_date = cd.compensation_date
WHERE s.period_id = 6
ORDER BY s.work_date, st.full_name;

-- Xem trước: Tổng số lịch vi phạm
SELECT COUNT(*) AS total_conflict_schedules
FROM schedule s
INNER JOIN compensation_day cd ON s.staff_id = cd.staff_id 
    AND s.work_date = cd.compensation_date
WHERE s.period_id = 6;

-- ============================================================
-- THỰC HIỆN XÓA
-- ============================================================

-- Xóa lịch vi phạm ngày nghỉ bù
DELETE s FROM schedule s
INNER JOIN compensation_day cd ON s.staff_id = cd.staff_id 
    AND s.work_date = cd.compensation_date
WHERE s.period_id = 6;

-- Xem lại: Số lịch còn lại sau khi xóa
SELECT 
    stt.id AS shift_type_id,
    stt.name AS shift_type_name,
    COUNT(*) AS so_lich
FROM schedule s
INNER JOIN shift_type stt ON s.shift_type_id = stt.id
WHERE s.period_id = 6
GROUP BY stt.id, stt.name
ORDER BY stt.id;

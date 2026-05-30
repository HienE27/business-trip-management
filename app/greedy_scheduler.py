"""
GREEDY SCHEDULER - Thuật toán Greedy cho xếp lịch tự động

Ưu tiên chọn nhân sự có ít ngày công nhất và không vi phạm ràng buộc.
Phù hợp cho nhóm trung bình - đơn giản, nhanh.

Theo tài liệu M07-F02 đến M07-F05:
- Tự động xếp L01 (Truc 24/24)
- Tự động xếp L02 (Thong Tam)
- Tự động xếp L03 (Phong Kham Dich Vu)
- Tự động xếp L04 (Phong Kham Chuyen Gia)

Quy tắc:
- L01 không trùng L02 cùng ngày
- L03 không trùng L04 cùng ngày
- Không xếp vào ngày nghỉ bù
- Nghỉ sau trực L01
"""

from datetime import date, timedelta
from typing import List, Dict, Set, Tuple, Optional
from .models import Staff, LeaveRequest, calculate_compensation_date, ScheduleEntry, CompensationDay
from .constraints import can_assign_shift, validate_schedule, generate_compensation_days_from_schedule


# Các loại ca làm việc (bổ sung L04)
SHIFTS = ["L01", "L02", "L03", "L04", "OFF"]

# Thứ tự ưu tiên khi xếp lịch (theo M07)
SHIFT_PRIORITY = ["L01", "L02", "L03", "L04"]


def generate_greedy_schedule(
    staff_list: List[Staff],
    start_date: date,
    num_days: int,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None,
    min_staff_per_day: Optional[Dict[str, int]] = None
) -> Tuple[Dict[Tuple[int, date], str], Set[Tuple[int, date]], List[str]]:
    """
    Thuật toán Greedy xếp lịch tự động

    Args:
        staff_list: Danh sách nhân sự
        start_date: Ngày bắt đầu
        num_days: Số ngày cần xếp
        leave_requests: Danh sách đơn nghỉ phép
        staff_max_shifts: Dict {staff_id: max_shifts_per_month}
        min_staff_per_day: Số nhân sự tối thiểu mỗi ca/ngày

    Returns:
        (schedule_dict, compensation_days, errors)
    """
    if leave_requests is None:
        leave_requests = []
    if staff_max_shifts is None:
        staff_max_shifts = {s.id: s.max_shifts for s in staff_list}
    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    schedule: Dict[Tuple[int, date], str] = {}
    compensation_days: Set[Tuple[int, date]] = set()
    errors: List[str] = []

    # Tạo danh sách ngày
    dates = [start_date + timedelta(days=i) for i in range(num_days)]

    # Bước 1: Xếp L01 (Truc 24/24) - ưu tiên cao nhất
    for work_date in dates:
        for _ in range(min_staff_per_day.get("L01", 0)):
            # Tìm nhân sự ít công nhất, có thể gán L01
            candidates = get_least_loaded_staff(
                schedule, staff_list, staff_max_shifts, compensation_days,
                work_date, "L01", leave_requests
            )
            if candidates:
                staff_id = candidates[0]  # Chọn người ít công nhất
                schedule[(staff_id, work_date)] = "L01"
                # Tính ngày nghỉ bù và cập nhật
                comp_date = calculate_compensation_date(work_date)
                compensation_days.add((staff_id, comp_date))

    # Bước 2: Xếp L02 (Thong Tam)
    for work_date in dates:
        for _ in range(min_staff_per_day.get("L02", 0)):
            candidates = get_least_loaded_staff(
                schedule, staff_list, staff_max_shifts, compensation_days,
                work_date, "L02", leave_requests
            )
            if candidates:
                staff_id = candidates[0]
                schedule[(staff_id, work_date)] = "L02"

    # Bước 3: Xếp L03 (Phong Kham Dich Vu)
    for work_date in dates:
        for _ in range(min_staff_per_day.get("L03", 0)):
            candidates = get_least_loaded_staff(
                schedule, staff_list, staff_max_shifts, compensation_days,
                work_date, "L03", leave_requests
            )
            if candidates:
                staff_id = candidates[0]
                schedule[(staff_id, work_date)] = "L03"

    # Bước 4: Xếp L04 (Phong Kham Chuyen Gia)
    for work_date in dates:
        for _ in range(min_staff_per_day.get("L04", 0)):
            candidates = get_least_loaded_staff(
                schedule, staff_list, staff_max_shifts, compensation_days,
                work_date, "L04", leave_requests
            )
            if candidates:
                staff_id = candidates[0]
                schedule[(staff_id, work_date)] = "L04"

    # Kiểm tra ràng buộc sau khi xếp
    is_valid, conflicts = validate_schedule(
        schedule, compensation_days, leave_requests, staff_max_shifts, min_staff_per_day
    )

    if not is_valid:
        for conflict in conflicts:
            errors.append(conflict.message)

    return schedule, compensation_days, errors


def get_least_loaded_staff(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff],
    staff_max_shifts: Dict[int, int],
    compensation_days: Set[Tuple[int, date]],
    work_date: date,
    shift_type: str,
    leave_requests: List[LeaveRequest]
) -> List[int]:
    """
    Lấy danh sách nhân sự có thể gán ca, sắp xếp theo số ngày công tăng dần

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sách nhân sự
        staff_max_shifts: Dict {staff_id: max_shifts}
        compensation_days: Set các ngày nghỉ bù
        work_date: Ngày cần xếp
        shift_type: Loại ca
        leave_requests: Danh sách đơn nghỉ phép

    Returns:
        Danh sách staff_id có thể gán, đã sắp xếp theo tải
    """
    # Đếm số ngày công hiện tại của mỗi nhân sự
    workload: Dict[int, int] = {}
    for (staff_id, _), shift in schedule.items():
        if shift != "OFF":
            workload[staff_id] = workload.get(staff_id, 0) + 1

    candidates = []

    for staff in staff_list:
        if not staff.is_active:
            continue

        # Kiểm tra có thể gán không
        can_assign, _ = can_assign_shift(
            schedule, compensation_days, staff.id, work_date,
            shift_type, leave_requests, staff_max_shifts
        )

        if can_assign:
            candidates.append(staff.id)

    # Sắp xếp theo số ngày công (ít nhất trước)
    candidates.sort(key=lambda sid: workload.get(sid, 0))

    return candidates


def generate_schedule_with_unassigned_days(
    staff_list: List[Staff],
    start_date: date,
    num_days: int,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None,
    min_staff_per_day: Optional[Dict[str, int]] = None
) -> Tuple[Dict[Tuple[int, date], str], Set[Tuple[int, date]], List[Tuple[date, str]]]:
    """
    Xếp lịch và trả về danh sách ngày chưa phân công đủ nhân sự

    Theo M07-F06: Báo cáo ngày chưa phân công được

    Returns:
        (schedule, compensation_days, unassigned_days)
    """
    schedule, compensation_days, errors = generate_greedy_schedule(
        staff_list, start_date, num_days, leave_requests, staff_max_shifts, min_staff_per_day
    )

    if leave_requests is None:
        leave_requests = []
    if staff_max_shifts is None:
        staff_max_shifts = {s.id: s.max_shifts for s in staff_list}
    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    unassigned_days: List[Tuple[date, str]] = []
    dates = [start_date + timedelta(days=i) for i in range(num_days)]

    for work_date in dates:
        for shift_type, min_required in min_staff_per_day.items():
            count = sum(
                1 for (staff_id, d), s in schedule.items()
                if d == work_date and s == shift_type
            )
            if count < min_required:
                # Thử tìm thêm nhân sự
                candidates = get_least_loaded_staff(
                    schedule, staff_list, staff_max_shifts, compensation_days,
                    work_date, shift_type, leave_requests
                )
                if not candidates:
                    unassigned_days.append((work_date, shift_type))

    return schedule, compensation_days, unassigned_days


def suggest_replacement(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff],
    compensation_days: Set[Tuple[int, date]],
    original_staff_id: int,
    work_date: date,
    shift_type: str,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None
) -> Optional[int]:
    """
    Đề xuất người thay thế khi nhân sự xin nghỉ đột xuất

    Theo M07-F08: Sắp xếp lại khi có thay đổi đột xuất

    Args:
        schedule: Lịch hiện tại
        staff_list: Danh sách nhân sự
        compensation_days: Set các ngày nghỉ bù
        original_staff_id: Nhân sự muốn nghỉ
        work_date: Ngày cần thay thế
        shift_type: Loại ca
        leave_requests: Danh sách đơn nghỉ phép

    Returns:
        staff_id của người thay thế, hoặc None
    """
    if staff_max_shifts is None:
        staff_max_shifts = {s.id: s.max_shifts for s in staff_list}

    candidates = get_least_loaded_staff(
        schedule, staff_list, staff_max_shifts, compensation_days,
        work_date, shift_type, leave_requests or []
    )

    # Loại bỏ nhân sự gốc
    if original_staff_id in candidates:
        candidates.remove(original_staff_id)

    return candidates[0] if candidates else None


def calculate_workload_balance(staff_list: List[Staff], schedule: Dict[Tuple[int, date], str]) -> Dict[int, Dict[str, int]]:
    """
    Tính toán phân bổ tải của từng nhân sự

    Theo M07-F09: Thống kê cân bằng tải

    Returns:
        Dict {staff_id: {"L01": count, "L02": count, "L03": count, "L04": count, "total": count}}
    """
    workload: Dict[int, Dict[str, int]] = {}

    for staff in staff_list:
        workload[staff.id] = {"L01": 0, "L02": 0, "L03": 0, "L04": 0, "total": 0}

    for (_, _), shift in schedule.items():
        if shift in ["L01", "L02", "L03", "L04"]:
            for (staff_id, _), s in schedule.items():
                if s == shift:
                    if staff_id in workload:
                        workload[staff_id][shift] += 1
                        workload[staff_id]["total"] += 1

    # Tính lại đúng
    workload = {s.id: {"L01": 0, "L02": 0, "L03": 0, "L04": 0, "total": 0} for s in staff_list}
    for (staff_id, _), shift in schedule.items():
        if shift in workload.get(staff_id, {}):
            workload[staff_id][shift] += 1
            workload[staff_id]["total"] += 1

    return workload


def get_uncovered_days(
    schedule: Dict[Tuple[int, date], str],
    dates: List[date],
    min_staff_per_day: Optional[Dict[str, int]] = None
) -> List[Tuple[date, str]]:
    """
    Lấy danh sách ngày chưa đủ nhân sự

    Theo M07-F06
    """
    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    uncovered = []

    for work_date in dates:
        for shift_type, min_required in min_staff_per_day.items():
            count = sum(
                1 for (staff_id, d), s in schedule.items()
                if d == work_date and s == shift_type
            )
            if count < min_required:
                uncovered.append((work_date, shift_type))

    return uncovered

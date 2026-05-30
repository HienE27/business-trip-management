"""
CONSTRAINTS MODULE - Ràng buộc nghiệp vụ cốt lõi

Theo tài liệu mô tả chức năng:
- L01 (Truc 24/24) không trùng L02 (Thong Tam) cùng ngày
- L03 (Phong Kham Dich Vu) không trùng L04 (Phong Kham Chuyen Gia) cùng ngày
- Không xếp lịch vào ngày nghỉ bù của nhân sự
- Nghỉ bù sau trực L01 theo quy tắc: T6/T7 -> tuần sau (bỏ T2, T6)
"""

from datetime import date, timedelta
from typing import List, Dict, Set, Optional, Tuple
from .models import (
    Staff, LeaveRequest, ScheduleEntry, CompensationDay,
    ScheduleConflict, calculate_compensation_date, ShiftType
)

# Các loại ca làm việc
SHIFTS = ["L01", "L02", "L03", "L04", "OFF"]

# Ngày không được phép làm ngày nghỉ bù
FORBIDDEN_COMPENSATION_DAYS = [0, 4]  # Monday (0), Friday (4)


# =====================================================
# CORE CONSTRAINT FUNCTIONS
# =====================================================

def check_l01_l02_conflict(
    schedule: Dict[Tuple[int, date], str],
    staff_id: int,
    work_date: date
) -> bool:
    """
    Kiểm tra xung đột L01 + L02 cùng ngày (cùng một nhân sự)

    Theo tài liệu: lịch trực 24/24 đã chiếm toàn bộ thời gian trong ngày,
    nên không thể đồng thời có lịch thông tầm.

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_id: Mã nhân sự
        work_date: Ngày làm việc

    Returns:
        True nếu có xung đột (không hợp lệ)
    """
    shifts = _normalize_shifts(schedule.get((staff_id, work_date)))
    return "L01" in shifts and "L02" in shifts


def check_l03_l04_conflict(
    schedule: Dict[Tuple[int, date], str],
    staff_id: int,
    work_date: date
) -> bool:
    """
    Kiểm tra xung đột L03 + L04 cùng ngày (cùng một nhân sự)

    Theo tài liệu: không được đồng thời có lịch phòng khám dịch vụ
    và lịch phòng khám chuyên gia cùng ngày.

    Returns:
        True nếu có xung đột (không hợp lệ)
    """
    shifts = _normalize_shifts(schedule.get((staff_id, work_date)))
    return "L03" in shifts and "L04" in shifts


def check_on_compensation_day(
    compensation_days: Set[Tuple[int, date]],
    staff_id: int,
    work_date: date,
    proposed_shift: str
) -> bool:
    """
    Kiểm tra xem ngày được chọn có phải là ngày nghỉ bù của nhân sự không

    Ngày nghỉ bù: KHÔNG được xếp bất kỳ loại lịch nào (L02, L03, L04)
    Chỉ L01 (trực) mới được phép ghi đè? Không - theo tài liệu thì
    ngày nghỉ bù bị khoá hoàn toàn.

    Args:
        compensation_days: Set các tuple (staff_id, compensation_date)
        staff_id: Mã nhân sự
        work_date: Ngày muốn xếp lịch
        proposed_shift: Loại ca muốn xếp (L02, L03, L04...)

    Returns:
        True nếu vi phạm (ngày nghỉ bù)
    """
    return (staff_id, work_date) in compensation_days


# =====================================================
# REST AFTER 24H SHIFT
# =====================================================

def check_rest_after_24h(
    schedule: Dict[Tuple[int, date], str]
) -> bool:
    """
    Kiểm tra nhân sự được nghỉ sau ca trực L01

    Sau khi trực L01 (7h30 ngày N đến 7h30 ngày N+1),
    nhân sự phải được nghỉ vào ngày N+1 (OFF).

    Returns:
        True nếu hợp lệ
    """
    for (staff_id, day), shift in schedule.items():
        if shift == "L01":
            next_day = day + timedelta(days=1)
            next_key = (staff_id, next_day)
            next_shift = schedule.get(next_key)

            if next_shift and next_shift != "OFF":
                return False

    return True


# =====================================================
# MAX SHIFT LIMIT
# =====================================================

def check_max_shift_limit(
    schedule: Dict[Tuple[int, date], str],
    staff_max_shifts: Dict[int, int]
) -> bool:
    """
    Kiểm tra giới hạn số ca làm việc tối đa của mỗi nhân sự

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_max_shifts: Dict {staff_id: max_shifts_per_month}

    Returns:
        True nếu hợp lệ
    """
    counter = {}

    for (staff_id, _), shift in schedule.items():
        if shift != "OFF":
            counter[staff_id] = counter.get(staff_id, 0) + 1

            max_allowed = staff_max_shifts.get(staff_id, 5)
            if counter[staff_id] > max_allowed:
                return False

    return True


# =====================================================
# LEAVE REQUESTS
# =====================================================

def check_leave_requests(
    schedule: Dict[Tuple[int, date], str],
    leave_requests: List[LeaveRequest]
) -> bool:
    """
    Kiểm tra tôn trọng đơn xin nghỉ phép đã được duyệt

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        leave_requests: Danh sách đơn nghỉ phép đã duyệt

    Returns:
        True nếu hợp lệ
    """
    for request in leave_requests:
        if request.status != "APPROVED":
            continue

        key = (request.staff_id, request.date)
        shift = schedule.get(key)

        if shift and shift != "OFF":
            return False

    return True


# =====================================================
# MINIMUM STAFF PER DAY
# =====================================================

def check_minimum_staff(
    schedule: Dict[Tuple[int, date], str],
    required_per_day: Dict[str, int] = None
) -> bool:
    """
    Kiểm tra đủ nhân sự cho mỗi ngày

    Default requirements:
    - L01: >= 1 người
    - L02: >= 1 người
    - L03: >= 1 người
    - L04: >= 1 người

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        required_per_day: Số người tối thiểu cho mỗi loại ca

    Returns:
        True nếu đủ nhân sự
    """
    if required_per_day is None:
        required_per_day = {
            "L01": 1,
            "L02": 1,
            "L03": 1,
            "L04": 1
        }

    # Thu thập tất cả các ngày
    all_dates: Set[date] = set()
    for (_, day), _ in schedule.items():
        all_dates.add(day)

    # Kiểm tra từng ngày
    for work_date in all_dates:
        for shift_type, min_required in required_per_day.items():
            count = sum(
                1 for (staff_id, day), shift in schedule.items()
                if day == work_date and shift == shift_type
            )
            if count < min_required:
                return False

    return True


# =====================================================
# COMPLETE VALIDATION
# =====================================================

def validate_schedule(
    schedule: Dict[Tuple[int, date], str],
    compensation_days: Set[Tuple[int, date]],
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None,
    required_per_day: Optional[Dict[str, int]] = None
) -> Tuple[bool, List[ScheduleConflict]]:
    """
    Kiểm tra toàn bộ ràng buộc và trả về danh sách xung đột

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        compensation_days: Set các ngày nghỉ bù (staff_id, date)
        leave_requests: Danh sách đơn nghỉ phép
        staff_max_shifts: Dict {staff_id: max_shifts}
        required_per_day: Số người tối thiểu mỗi loại ca/ngày

    Returns:
        (is_valid, list_of_conflicts)
    """
    conflicts: List[ScheduleConflict] = []

    # 1. Kiểm tra L01 + L02 conflict
    for (staff_id, work_date), shift in schedule.items():
        if shift == "L01":
            # Tìm xem có L02 cùng ngày không
            for (sid2, d2), s2 in schedule.items():
                if staff_id == sid2 and work_date == d2 and s2 == "L02":
                    conflicts.append(ScheduleConflict(
                        staff_id=staff_id,
                        date=work_date,
                        shift1="L01",
                        shift2="L02",
                        conflict_type="L01_L02",
                        message=f"Nhan su {staff_id} khong the truc 24/24 va Thong Tam cung ngay {work_date}"
                    ))

        elif shift == "L02":
            # Tìm xem có L01 cùng ngày không
            for (sid2, d2), s2 in schedule.items():
                if staff_id == sid2 and work_date == d2 and s2 == "L01":
                    conflicts.append(ScheduleConflict(
                        staff_id=staff_id,
                        date=work_date,
                        shift1="L02",
                        shift2="L01",
                        conflict_type="L01_L02",
                        message=f"Nhan su {staff_id} khong the Thong Tam va truc 24/24 cung ngay {work_date}"
                    ))

    # 2. Kiểm tra L03 + L04 conflict
    for (staff_id, work_date), shift in schedule.items():
        if shift == "L03":
            for (sid2, d2), s2 in schedule.items():
                if staff_id == sid2 and work_date == d2 and s2 == "L04":
                    conflicts.append(ScheduleConflict(
                        staff_id=staff_id,
                        date=work_date,
                        shift1="L03",
                        shift2="L04",
                        conflict_type="L03_L04",
                        message=f"Nhan su {staff_id} khong the Phong Kham Dich Vu va Chuyen Gia cung ngay {work_date}"
                    ))

        elif shift == "L04":
            for (sid2, d2), s2 in schedule.items():
                if staff_id == sid2 and work_date == d2 and s2 == "L03":
                    conflicts.append(ScheduleConflict(
                        staff_id=staff_id,
                        date=work_date,
                        shift1="L04",
                        shift2="L03",
                        conflict_type="L03_L04",
                        message=f"Nhan su {staff_id} khong the Chuyen Gia va Phong Kham Dich Vu cung ngay {work_date}"
                    ))

    # 3. Kiểm tra vi phạm ngày nghỉ bù
    for (staff_id, work_date), shift in schedule.items():
        if shift in ["L02", "L03", "L04"]:
            if (staff_id, work_date) in compensation_days:
                conflicts.append(ScheduleConflict(
                    staff_id=staff_id,
                    date=work_date,
                    shift1=shift,
                    shift2="COMPENSATION",
                    conflict_type="COMPENSATION_VIOLATION",
                    message=f"Nhan su {staff_id} khong the xep lich {shift} vao ngay nghi bu {work_date}"
                ))

    # 4. Kiểm tra nghỉ sau trực L01
    if not check_rest_after_24h(schedule):
        for (staff_id, day), shift in schedule.items():
            if shift == "L01":
                next_day = day + timedelta(days=1)
                next_shift = schedule.get((staff_id, next_day))
                if next_shift and next_shift != "OFF":
                    conflicts.append(ScheduleConflict(
                        staff_id=staff_id,
                        date=next_day,
                        shift1="L01",
                        shift2=next_shift,
                        conflict_type="REST_VIOLATION",
                        message=f"Nhan su {staff_id} phai nghi sau khi truc 24/24 ngay {day}"
                    ))

    # 5. Kiểm tra giới hạn số ca
    if staff_max_shifts:
        counter = {}
        for (staff_id, _), shift in schedule.items():
            if shift != "OFF":
                counter[staff_id] = counter.get(staff_id, 0) + 1
                max_allowed = staff_max_shifts.get(staff_id, 5)
                if counter[staff_id] > max_allowed:
                    conflicts.append(ScheduleConflict(
                        staff_id=staff_id,
                        date=None,
                        shift1=shift,
                        shift2="MAX_LIMIT",
                        conflict_type="MAX_SHIFT_VIOLATION",
                        message=f"Nhan su {staff_id} da vuot gioi han {max_allowed} ca/thang"
                    ))

    # 6. Kiểm tra đơn nghỉ phép
    if leave_requests:
        for request in leave_requests:
            if request.status != "APPROVED":
                continue
            key = (request.staff_id, request.date)
            shift = schedule.get(key)
            if shift and shift != "OFF":
                conflicts.append(ScheduleConflict(
                    staff_id=request.staff_id,
                    date=request.date,
                    shift1=shift,
                    shift2="LEAVE",
                    conflict_type="LEAVE_VIOLATION",
                    message=f"Nhan su {request.staff_id} co don nghi phep duoc duyet vao ngay {request.date}"
                ))

    is_valid = len(conflicts) == 0
    return is_valid, conflicts


# =====================================================
# QUICK CONSTRAINT CHECK (for incremental assignment)
# =====================================================

def can_assign_shift(
    schedule: Dict[Tuple[int, date], str],
    compensation_days: Set[Tuple[int, date]],
    staff_id: int,
    work_date: date,
    shift_type: str,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None
) -> Tuple[bool, Optional[str]]:
    """
    Kiểm tra nhanh xem có thể gán ca này không

    Returns:
        (can_assign, error_message)
    """
    # 1. Kiểm tra ngày nghỉ bù
    if shift_type in ["L02", "L03", "L04"]:
        if (staff_id, work_date) in compensation_days:
            return False, f"Ngay {work_date} la ngay nghi bu cua nhan su {staff_id}"

    # 2. Kiểm tra L01 + L02 conflict
    if shift_type == "L01":
        if (staff_id, work_date) in schedule:
            existing = schedule[(staff_id, work_date)]
            if existing == "L02":
                return False, f"Nhan su {staff_id} da co lich Thong Tam ngay {work_date}"
    elif shift_type == "L02":
        if (staff_id, work_date) in schedule:
            existing = schedule[(staff_id, work_date)]
            if existing == "L01":
                return False, f"Nhan su {staff_id} da co lich Truc 24/24 ngay {work_date}"

    # 3. Kiểm tra L03 + L04 conflict
    if shift_type == "L03":
        if (staff_id, work_date) in schedule:
            existing = schedule[(staff_id, work_date)]
            if existing == "L04":
                return False, f"Nhan su {staff_id} da co lich Chuyen Gia ngay {work_date}"
    elif shift_type == "L04":
        if (staff_id, work_date) in schedule:
            existing = schedule[(staff_id, work_date)]
            if existing == "L03":
                return False, f"Nhan su {staff_id} da co lich Dich Vu ngay {work_date}"

    # 4. Kiểm tra đơn nghỉ phép
    if leave_requests:
        for request in leave_requests:
            if request.staff_id == staff_id and request.date == work_date:
                if request.status == "APPROVED":
                    return False, f"Nhan su {staff_id} co don nghi phep duoc duyet ngay {work_date}"

    # 5. Kiểm tra giới hạn số ca
    if staff_max_shifts:
        current_count = sum(
            1 for (sid, d), s in schedule.items()
            if sid == staff_id and s != "OFF"
        )
        max_allowed = staff_max_shifts.get(staff_id, 5)
        if current_count >= max_allowed:
            return False, f"Nhan su {staff_id} da dat gioi han {max_allowed} ca"

    return True, None


# =====================================================
# HELPER FUNCTIONS
# =====================================================

def get_available_staff_for_day(
    schedule: Dict[Tuple[int, date], str],
    compensation_days: Set[Tuple[int, date]],
    staff_list: List[Staff],
    work_date: date,
    shift_type: str,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None
) -> List[int]:
    """
    Lấy danh sách nhân sự có thể xếp ca cho một ngày cụ thể

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        compensation_days: Set các ngày nghỉ bù
        staff_list: Danh sách nhân sự
        work_date: Ngày cần xếp
        shift_type: Loại ca (L01, L02, L03, L04)

    Returns:
        Danh sách staff_id có thể xếp
    """
    available = []

    for staff in staff_list:
        if not staff.is_active:
            continue

        can_assign, _ = can_assign_shift(
            schedule,
            compensation_days,
            staff.id,
            work_date,
            shift_type,
            leave_requests,
            staff_max_shifts
        )

        if can_assign:
            available.append(staff.id)

    return available


def generate_compensation_days_from_schedule(
    schedule: Dict[Tuple[int, date], str]
) -> Set[Tuple[int, date]]:
    """
    Tạo danh sách ngày nghỉ bù từ lịch trực L01

    Args:
        schedule: Dict {(staff_id, date): shift_type}

    Returns:
        Set các tuple (staff_id, compensation_date)
    """
    compensation_days: Set[Tuple[int, date]] = set()

    for (staff_id, shift_date), shift in schedule.items():
        if shift == "L01":
            comp_date = calculate_compensation_date(shift_date)
            compensation_days.add((staff_id, comp_date))

    return compensation_days


def _normalize_shifts(value) -> Set[str]:
    if value is None:
        return set()
    if isinstance(value, str):
        return {value}
    return set(value)

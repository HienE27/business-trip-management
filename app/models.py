from dataclasses import dataclass, field
from datetime import date, timedelta
from typing import List, Optional
from enum import Enum


class ShiftType(Enum):
    """Cac loai lich trong he thong"""
    L01_TRUC_24 = ("L01", "Truc 24/24", 5)
    L02_THONG_TAM = ("L02", "Thong Tam", 3)
    L03_PHONG_KHAM_DICH_VU = ("L03", "Phong Kham Dich Vu", 2)
    L04_PHONG_KHAM_CHUYEN_GIA = ("L04", "Phong Kham Chuyen Gia", 4)

    def __init__(self, code: str, display_name: str, fatigue: int):
        self.code = code
        self.display_name = display_name
        self.fatigue = fatigue

    @property
    def id(self) -> str:
        return self.code


# Mapping day of week: Monday=0, Tuesday=1, ..., Sunday=6
DAY_OF_WEEK = {
    "monday": 0,
    "tuesday": 1,
    "wednesday": 2,
    "thursday": 3,
    "friday": 4,
    "saturday": 5,
    "sunday": 6
}


@dataclass
class Staff:
    """Nhân sự trong hệ thống"""
    id: int
    name: str
    specialty: str
    max_shifts: int = 5
    is_active: bool = True


@dataclass
class LeaveRequest:
    """Đơn xin nghỉ phép"""
    staff_id: int
    date: date
    reason: str = ""
    status: str = "PENDING"


@dataclass
class ScheduleEntry:
    """Một bản ghi lịch trong ngày"""
    staff_id: int
    work_date: date
    shift_type: str  # L01, L02, L03, L04
    has_conflict: bool = False
    conflict_note: str = ""


@dataclass
class CompensationDay:
    """Ngày nghỉ bù sau trực L01"""
    staff_id: int
    shift_date: date
    compensation_date: date
    period_id: int


@dataclass
class ScheduleConflict:
    """Thông tin xung đột lịch"""
    staff_id: int
    date: date
    shift1: str
    shift2: str
    conflict_type: str  # L01_L02, L03_L04, COMPENSATION_VIOLATION
    message: str


@dataclass
class ScheduleDraft:
    """Bản nháp lịch trong quá trình xếp"""
    entries: List[ScheduleEntry] = field(default_factory=list)
    compensation_days: List[CompensationDay] = field(default_factory=list)

    def get_schedule_dict(self) -> dict:
        """Chuyển thành dict dạng {(staff_id, date): shift_type}"""
        result = {}
        for entry in self.entries:
            key = (entry.staff_id, entry.work_date)
            if entry.shift_type != "OFF":
                result[key] = entry.shift_type
        return result

    def get_staff_days(self, staff_id: int) -> dict:
        """Lấy lịch của một nhân sự"""
        return {
            entry.work_date: entry.shift_type
            for entry in self.entries
            if entry.staff_id == staff_id
        }


# =====================================================
# COMPENSATION DAY CALCULATION LOGIC
# =====================================================

def calculate_compensation_date(truc_date: date) -> date:
    """
    Tính ngày nghỉ bù theo quy tắc:

    - Trực T2, T3, T4, T5 -> Nghỉ bù ngày hôm sau (N+1)
    - Trực T6 hoặc T7 -> Nghỉ bù tuần sau, bỏ qua T2 và T6 -> T3 tuần sau
    - Trực Chủ Nhật -> Nghỉ bù T2 ngay hôm sau

    Returns:
        date: Ngày nghỉ bù hợp lệ
    """
    day_of_week = truc_date.weekday()  # 0=Monday, 6=Sunday

    # Thứ 2, 3, 4, 5: nghỉ bù ngày hôm sau
    if day_of_week in [0, 1, 2, 3]:  # Mon-Thu
        return truc_date + timedelta(days=1)

    # Thứ 6 (5) hoặc Thứ 7 (6): dời sang tuần sau, bỏ qua T2 và T6
    elif day_of_week in [4, 5]:  # Fri, Sat
        # Tính T2 tuần sau
        days_until_next_monday = 7 - day_of_week
        next_monday = truc_date + timedelta(days=days_until_next_monday)

        # Bỏ qua T2 (0), kiểm tra T3 (bỏ qua T2=thứ 2, T6=thứ 6)
        # Nếu T2 tuần sau = thứ 2, bỏ qua, đến T3
        # Nếu T3 = thứ 3, OK
        # Nếu T3 rơi vào T6, tiếp tục bỏ qua

        compensation = next_monday + timedelta(days=1)  # T2 -> T3

        # Kiểm tra nếu T3 là thứ 6, bỏ qua và đến T4
        if compensation.weekday() == 4:  # Friday
            compensation = compensation + timedelta(days=1)  # T4

        return compensation

    # Chủ Nhật (6): nghỉ bù T2 hôm sau
    else:  # Sunday (6)
        return truc_date + timedelta(days=1)


def is_valid_compensation_date(compensation_date: date, holidays: List[date] = None) -> bool:
    """
    Kiểm tra ngày nghỉ bù có hợp lệ không:
    - Không được rơi vào ngày lễ
    - Không được là T2 hoặc T6 (đã xử lý trong calculate_compensation_date)
    """
    if holidays is None:
        holidays = []

    # Kiểm tra ngày lễ
    if compensation_date in holidays:
        return False

    return True


def get_next_valid_compensation_date(start_date: date, holidays: List[date] = None) -> date:
    """
    Tìm ngày nghỉ bù hợp lệ tiếp theo nếu ngày tính được không hợp lệ
    """
    if holidays is None:
        holidays = []

    date = start_date
    max_iterations = 30  # Tránh infinite loop

    for _ in range(max_iterations):
        if date not in holidays and date.weekday() not in [0, 4]:  # Không phải T2, T6
            return date
        date = date + timedelta(days=1)

    return date
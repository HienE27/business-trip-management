"""
UTILITIES MODULE - Cac ham tien ich

Chua cac ham ho tro:
- Tinh ngay
- Tinh tai
- Chuyen doi dinh dang
"""

from datetime import date, timedelta
from typing import List, Dict, Set, Tuple


# Cac loai ca lam viec
SHIFTS = ["L01", "L02", "L03", "L04", "OFF"]
WORK_SHIFTS = ["L01", "L02", "L03", "L04"]


def get_next_day(day: date) -> date:
    """Lay ngay tiep theo"""
    return day + timedelta(days=1)


def calculate_workload(
    schedule: Dict[Tuple[int, date], str],
    staff_id: int
) -> int:
    """
    Tinh so ngay lam viec cua mot nhan su

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_id: Ma nhan su

    Returns:
        So ngay lam viec
    """
    count = 0
    for (sid, _), shift in schedule.items():
        if sid == staff_id and shift != "OFF":
            count += 1
    return count


def calculate_workload_by_shift(
    schedule: Dict[Tuple[int, date], str],
    staff_id: int
) -> Dict[str, int]:
    """
    Tinh so ngay lam viec theo tung loai ca

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_id: Ma nhan su

    Returns:
        Dict {shift_type: count}
    """
    result = {"L01": 0, "L02": 0, "L03": 0, "L04": 0, "total": 0}

    for (sid, _), shift in schedule.items():
        if sid == staff_id and shift in result:
            result[shift] += 1
            result["total"] += 1

    return result


def get_days_in_month(year: int, month: int) -> List[date]:
    """
    Lay danh sach ngay trong thang

    Args:
        year: Nam
        month: Thang

    Returns:
        Danh sach cac ngay trong thang
    """
    import calendar
    num_days = calendar.monthrange(year, month)[1]
    start_date = date(year, month, 1)
    return [start_date + timedelta(days=i) for i in range(num_days)]


def is_weekend(day: date) -> bool:
    """Kiem tra co phai cuoi tuan khong (T7 hoac CN)"""
    return day.weekday() in [5, 6]


def is_monday(day: date) -> bool:
    """Kiem tra co phai thu 2 khong"""
    return day.weekday() == 0


def is_friday(day: date) -> bool:
    """Kiem tra co phai thu 6 khong"""
    return day.weekday() == 4


def get_week_number(day: date) -> int:
    """Lay so thu tu tuan trong nam"""
    return day.isocalendar()[1]


def get_weekday_name(day: date) -> str:
    """Lay ten thu trong tuan"""
    names = ["Thu 2", "Thu 3", "Thu 4", "Thu 5", "Thu 6", "Thu 7", "Chu Nhat"]
    return names[day.weekday()]


def format_date_vn(day: date) -> str:
    """Dinh dang ngay theo Viet Nam"""
    weekday = get_weekday_name(day)
    return f"{day.day:02d}/{day.month:02d}/{day.year} ({weekday})"


def get_date_range(start: date, end: date) -> List[date]:
    """
    Lay danh sach ngay trong khoang

    Args:
        start: Ngay bat dau
        end: Ngay ket thuc

    Returns:
        Danh sach cac ngay
    """
    result = []
    current = start
    while current <= end:
        result.append(current)
        current += timedelta(days=1)
    return result


def date_to_json(day: date) -> str:
    """Chuyen date thanh chuoi JSON"""
    return day.isoformat()


def json_to_date(json_str: str) -> date:
    """Chuyen chuoi JSON thanh date"""
    return date.fromisoformat(json_str)
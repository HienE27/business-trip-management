"""
STATISTICS MODULE - Tinh toan thong ke

Chua cac ham tinh toan thong ke cho lich:
- Phan bo tai
- Ty le phu hop
- Thong ke chi tiet
"""

from collections import Counter, defaultdict
from datetime import date
from typing import List, Dict, Set, Tuple, Optional

from .models import Staff


def calculate_statistics(results: List[Dict]) -> Dict[int, int]:
    """
    Tinh so lan xep lich cua tung nhan su

    Args:
        results: Danh sach ket qua tu solver

    Returns:
        Dict {staff_id: so_lan}
    """
    counter = Counter()
    for item in results:
        if item.get("shift") not in ["OFF", None]:
            counter[item["staff_id"]] += 1
    return dict(counter)


def calculate_detailed_statistics(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> Dict:
    """
    Tinh thong ke chi tiet

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Dict chua cac thong ke
    """
    # Dem theo tung loai ca
    shift_counts: Dict[str, Counter] = {
        "L01": Counter(),
        "L02": Counter(),
        "L03": Counter(),
        "L04": Counter(),
        "OFF": Counter()
    }

    for (staff_id, _), shift in schedule.items():
        if shift in shift_counts:
            shift_counts[shift][staff_id] += 1

    # Tinh tong
    total_per_staff: Dict[int, int] = {}
    for shift_type, counter in shift_counts.items():
        if shift_type != "OFF":
            for staff_id, count in counter.items():
                total_per_staff[staff_id] = total_per_staff.get(staff_id, 0) + count

    # Tim max, min
    if total_per_staff:
        max_workload = max(total_per_staff.values())
        min_workload = min(total_per_staff.values())
        avg_workload = sum(total_per_staff.values()) / len(total_per_staff)
    else:
        max_workload = min_workload = avg_workload = 0

    return {
        "by_shift_type": {
            shift: dict(counts)
            for shift, counts in shift_counts.items()
        },
        "total_per_staff": total_per_staff,
        "max_workload": max_workload,
        "min_workload": min_workload,
        "avg_workload": round(avg_workload, 2),
        "total_shifts": sum(
            count for counts in shift_counts.values()
            for count in counts.values()
        )
    }


def get_staff_with_most_work(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> Optional[Staff]:
    """
    Lay nhan su co tai nhieu nhat

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Staff co tai nhieu nhat hoac None
    """
    if not schedule:
        return None

    workload: Dict[int, int] = defaultdict(int)

    for (_, _), shift in schedule.items():
        if shift != "OFF":
            for (staff_id, _) in schedule.keys():
                if schedule[(staff_id, _)] == shift:
                    workload[staff_id] += 1

    # Dem lai dung
    workload = defaultdict(int)
    for (staff_id, _), shift in schedule.items():
        if shift != "OFF":
            workload[staff_id] += 1

    if not workload:
        return None

    max_staff_id = max(workload, key=workload.get)
    for staff in staff_list:
        if staff.id == max_staff_id:
            return staff

    return None


def get_staff_balance_report(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> List[Dict]:
    """
    Tao bao cao can bang tai

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Danh sach bao cao theo nhan su
    """
    report = []

    for staff in staff_list:
        l01_count = 0
        l02_count = 0
        l03_count = 0
        l04_count = 0

        for (staff_id, _), shift in schedule.items():
            if staff_id == staff.id:
                if shift == "L01":
                    l01_count += 1
                elif shift == "L02":
                    l02_count += 1
                elif shift == "L03":
                    l03_count += 1
                elif shift == "L04":
                    l04_count += 1

        total = l01_count + l02_count + l03_count + l04_count

        report.append({
            "staff_id": staff.id,
            "name": staff.name,
            "L01": l01_count,
            "L02": l02_count,
            "L03": l03_count,
            "L04": l04_count,
            "total": total
        })

    # Sap xep theo total giam dan
    report.sort(key=lambda x: x["total"], reverse=True)

    return report


def get_uncovered_days_report(
    schedule: Dict[Tuple[int, date], str],
    dates: List[date],
    min_staff_per_day: Optional[Dict[str, int]] = None
) -> List[Dict]:
    """
    Tao bao cao ngay chua du nhan su

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        dates: Danh sach ngay
        min_staff_per_day: So nhan su toi thieu moi ca

    Returns:
        Danh sach bao cao
    """
    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    report = []

    for work_date in dates:
        for shift_type, min_required in min_staff_per_day.items():
            count = sum(
                1 for (staff_id, d), s in schedule.items()
                if d == work_date and s == shift_type
            )

            if count < min_required:
                report.append({
                    "date": work_date.isoformat(),
                    "shift_type": shift_type,
                    "required": min_required,
                    "actual": count,
                    "missing": min_required - count
                })

    return report

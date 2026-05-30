"""
SCORING MODULE - Ham tinh diem cho lich

Dùng de danh gia chat luong lich duoc xep:
- Fairness score: Do phan bo cong bang
- Fatigue score: Do mee cua nhan su
- Preference score: Do thoa man yeu cau
- Compensation score: Danh gia ngay nghi bu

Theo tai lieu M07-F09: Thong ke can bang tai
"""

import numpy as np
from datetime import date, timedelta
from typing import List, Dict, Set, Tuple, Optional
from .models import Staff, calculate_compensation_date


def fairness_score(workloads: Dict[int, int]) -> float:
    """
    Tinh diem cong bang phan bo

    Diem cao = phan bo deu
    Diem thap = chenh lech lon

    Args:
        workloads: Dict {staff_id: so_ngay_lam_viec}

    Returns:
        Diem tu 0-100
    """
    if not workloads:
        return 0.0

    values = list(workloads.values())
    if len(values) < 2:
        return 100.0

    # Do lech chuan
    std = np.std(values)

    # Score = 100 - (std * 10)
    # std = 0 -> score = 100
    # std = 10 -> score = 0
    score = max(0, 100 - std * 10)

    return round(score, 2)


def fairness_score_by_shift(
    schedule: Dict[Tuple[int, date], str]
) -> Dict[str, float]:
    """
    Tinh diem cong bang theo tung loai ca

    Args:
        schedule: Dict {(staff_id, date): shift_type}

    Returns:
        Dict {shift_type: fairness_score}
    """
    shift_workloads: Dict[str, Dict[int, int]] = {
        "L01": {}, "L02": {}, "L03": {}, "L04": {}
    }

    # Dem so lan moi loai ca
    for (_, _), shift in schedule.items():
        if shift in shift_workloads:
            for (staff_id, _), s in schedule.items():
                if s == shift:
                    shift_workloads[shift][staff_id] = shift_workloads[shift].get(staff_id, 0) + 1

    # Reset and recount properly
    shift_workloads = {"L01": {}, "L02": {}, "L03": {}, "L04": {}}
    for (staff_id, _), shift in schedule.items():
        if shift in shift_workloads:
            shift_workloads[shift][staff_id] = shift_workloads[shift].get(staff_id, 0) + 1

    scores = {}
    for shift_type, workloads in shift_workloads.items():
        scores[shift_type] = fairness_score(workloads)

    return scores


def fatigue_score(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> float:
    """
    Tinh diem mee dua tren L01

    - Gap L01 trong vong 4 ngay lien tiep -> penalty
    - L01 lien tiep -> penalty cao

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Diem tu 0-100 (cao = tot)
    """
    penalty = 0

    for staff in staff_list:
        staff_id = staff.id

        # Lay danh sach ngay truc L01 cua nhan su nay
        l01_days: List[date] = []
        for (sid, day), shift in schedule.items():
            if sid == staff_id and shift == "L01":
                l01_days.append(day)

        l01_days.sort()

        # Kiem tra gap trong 4 ngay
        for i in range(len(l01_days) - 1):
            gap = (l01_days[i + 1] - l01_days[i]).days

            if gap < 4:
                penalty += 20
            elif gap < 7:
                penalty += 10

        # Kiem tra L01 lien tiep
        for i in range(len(l01_days) - 1):
            if (l01_days[i + 1] - l01_days[i]).days == 1:
                penalty += 30

    return max(0, 100 - penalty)


def fatigue_score_detail(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> Dict[int, float]:
    """
    Tinh diem mee chi tiet cho tung nhan su

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Dict {staff_id: fatigue_score}
    """
    result = {}

    for staff in staff_list:
        staff_id = staff.id

        l01_days: List[date] = []
        for (sid, day), shift in schedule.items():
            if sid == staff_id and shift == "L01":
                l01_days.append(day)

        l01_days.sort()

        penalty = 0
        for i in range(len(l01_days) - 1):
            gap = (l01_days[i + 1] - l01_days[i]).days
            if gap < 4:
                penalty += 20
            elif gap < 7:
                penalty += 10

        for i in range(len(l01_days) - 1):
            if (l01_days[i + 1] - l01_days[i]).days == 1:
                penalty += 30

        result[staff_id] = max(0, 100 - penalty)

    return result


def preference_score(
    schedule: Dict[Tuple[int, date], str],
    preferred_days: Dict[int, List[date]]
) -> float:
    """
    Tinh diem thoa man yeu cau uu tien ngay

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        preferred_days: Dict {staff_id: [ngay_uu_tien]}

    Returns:
        Diem tu 0-100 (cao = tot)
    """
    if not preferred_days:
        return 100.0

    total_preferences = 0
    matched = 0

    for staff_id, pref_days in preferred_days.items():
        total_preferences += len(pref_days)

        for pref_day in pref_days:
            # Kiem tra nhan su co lam viec vao ngay uu tien khong
            shift = schedule.get((staff_id, pref_day))
            if shift and shift != "OFF":
                matched += 1

    if total_preferences == 0:
        return 100.0

    return round((matched / total_preferences) * 100, 2)


def compensation_score(
    compensation_days: Set[Tuple[int, date]],
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> float:
    """
    Tinh diem nghi bu

    - Neu ngay nghi bu bi xep lich -> penalty
    - Neu nghi bu nam dung ngay -> bonus

    Args:
        compensation_days: Set {(staff_id, compensation_date)}
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Diem tu 0-100 (cao = tot)
    """
    if not compensation_days:
        return 100.0

    violations = 0

    for (staff_id, comp_date) in compensation_days:
        # Kiem tra xem co lich L02/L03/L04 vao ngay nay khong
        shift = schedule.get((staff_id, comp_date))
        if shift in ["L02", "L03", "L04"]:
            violations += 1

    # Score = 100 - (violations * 10)
    score = max(0, 100 - violations * 10)

    return round(score, 2)


def coverage_score(
    schedule: Dict[Tuple[int, date], str],
    dates: List[date],
    min_staff_per_day: Optional[Dict[str, int]] = None
) -> float:
    """
    Tinh diem phu hop (coverage)

    - Tat ca cac ca deu du nhan su -> score cao
    - Co ngay thieu nhan su -> score thap

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        dates: Danh sach ngay
        min_staff_per_day: So nhan su toi thieu moi ca

    Returns:
        Diem tu 0-100
    """
    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    total_required = 0
    total_covered = 0

    for work_date in dates:
        for shift_type, min_required in min_staff_per_day.items():
            total_required += min_required

            count = sum(
                1 for (staff_id, d), s in schedule.items()
                if d == work_date and s == shift_type
            )
            total_covered += min(count, min_required)

    if total_required == 0:
        return 100.0

    return round((total_covered / total_required) * 100, 2)


def total_score(
    schedule: Dict[Tuple[int, date], str],
    compensation_days: Set[Tuple[int, date]],
    staff_list: List[Staff],
    preferred_days: Optional[Dict[int, List[date]]] = None,
    dates: Optional[List[date]] = None,
    min_staff_per_day: Optional[Dict[str, int]] = None,
    weights: Optional[Dict[str, float]] = None
) -> Dict[str, float]:
    """
    Tinh tong hop diem

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        compensation_days: Set {(staff_id, compensation_date)}
        staff_list: Danh sach nhan su
        preferred_days: Dict {staff_id: [ngay_uu_tien]}
        dates: Danh sach ngay
        min_staff_per_day: So nhan su toi thieu moi ca
        weights: Trong so cho tung diem thanh phan

    Returns:
        Dict voi cac diem thanh phan va tong diem
    """
    if weights is None:
        weights = {
            "fairness": 0.4,
            "fatigue": 0.3,
            "coverage": 0.2,
            "compensation": 0.1
        }

    if preferred_days is None:
        preferred_days = {}

    if dates is None:
        dates = list(set(day for (_, day) in schedule.keys()))

    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    # Tinh cac diem thanh phan
    workloads = {}
    for staff in staff_list:
        workloads[staff.id] = sum(
            1 for (sid, _), s in schedule.items()
            if sid == staff.id and s != "OFF"
        )

    f_score = fairness_score(workloads)
    ft_score = fatigue_score(schedule, staff_list)
    cov_score = coverage_score(schedule, dates, min_staff_per_day)
    comp_score = compensation_score(compensation_days, schedule, staff_list)
    pref_score = preference_score(schedule, preferred_days)

    # Tinh tong diem
    total = (
        f_score * weights.get("fairness", 0.4) +
        ft_score * weights.get("fatigue", 0.3) +
        cov_score * weights.get("coverage", 0.2) +
        comp_score * weights.get("compensation", 0.1)
    )

    return {
        "fairness": f_score,
        "fatigue": ft_score,
        "coverage": cov_score,
        "compensation": comp_score,
        "preference": pref_score,
        "total": round(total, 2)
    }


def workload_distribution(
    schedule: Dict[Tuple[int, date], str],
    staff_list: List[Staff]
) -> Dict[int, Dict[str, int]]:
    """
    Thong ke phan bo tai theo tung nhan su

    Theo M07-F09

    Args:
        schedule: Dict {(staff_id, date): shift_type}
        staff_list: Danh sach nhan su

    Returns:
        Dict {staff_id: {"L01": count, "L02": count, "L03": count, "L04": count, "total": count}}
    """
    result = {}

    for staff in staff_list:
        result[staff.id] = {"L01": 0, "L02": 0, "L03": 0, "L04": 0, "total": 0}

    for (staff_id, _), shift in schedule.items():
        if shift in ["L01", "L02", "L03", "L04"]:
            if staff_id in result:
                result[staff_id][shift] += 1
                result[staff_id]["total"] += 1

    return result

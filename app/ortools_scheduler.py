"""
OR-TOOLS SCHEDULER - Su dung Google OR-Tools CP-SAT cho xep lich

Thuật toán tối ưu hoá với CP-SAT solver, cho ket qua chính xác nhat.
Phù hợp cho bài toán lớn với nhiều ràng buộc.

Theo tài liệu M07: Tự động sắp xếp lịch với đầy đủ ràng buộc

Tính năng:
- L01 không trùng L02 cùng ngày
- L03 không trùng L04 cùng ngày
- Tự động tính ngày nghỉ bù
- Cân bằng tải giữa các nhân sự
"""

from datetime import date, timedelta
from typing import List, Dict, Set, Tuple, Optional
from .models import Staff, LeaveRequest, calculate_compensation_date
from .constraints import validate_schedule

# Cac loai ca lam viec
SHIFTS = ["L01", "L02", "L03", "L04", "OFF"]
WORK_SHIFTS = ["L01", "L02", "L03", "L04"]

# Cac ngày không được nghỉ bù (Monday=0, Friday=4)
FORBIDDEN_COMPENSATION_DAYS = [0, 4]


def solve_ortools(
    staff_list: List[Staff],
    start_date: date,
    num_days: int,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None,
    min_staff_per_day: Optional[Dict[str, int]] = None,
    max_time_seconds: int = 30
) -> Tuple[Optional[List[Dict]], Set[Tuple[int, date]]]:
    """
    Giai bai toan xep lich bang OR-Tools CP-SAT

    Args:
        staff_list: Danh sach nhan su
        start_date: Ngay bat dau
        num_days: So ngay can xep
        leave_requests: Danh sach don nghi phep
        staff_max_shifts: Dict {staff_id: max_shifts_per_month}
        min_staff_per_day: Dict {shift_type: min_staff}
        max_time_seconds: Thoi gian toi da tim kiem

    Returns:
        (list_of_assignments, compensation_days)
    """
    try:
        from ortools.sat.python import cp_model
    except ImportError:
        print("ERROR: ortools chua duoc cai dat. Chay: pip install ortools")
        return None, set()

    if staff_max_shifts is None:
        staff_max_shifts = {s.id: s.max_shifts for s in staff_list}
    if min_staff_per_day is None:
        min_staff_per_day = {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

    model = cp_model.CpModel()

    # =====================================================
    # CREATE VARIABLES
    # =====================================================

    # schedule[(staff_id, day, shift)] = BoolVar
    schedule: Dict[Tuple[int, int, str], any] = {}
    fatigue_penalties: List[any] = []

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days):
            for shift in SHIFTS:
                schedule[(staff_id, day, shift)] = model.NewBoolVar(
                    f"s{staff_id}_d{day}_{shift}"
                )

    # =====================================================
    # ONLY 1 SHIFT PER DAY PER STAFF
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days):
            model.Add(
                sum(schedule[(staff_id, day, shift)] for shift in SHIFTS) == 1
            )

    # =====================================================
    # REST AFTER L01 (7h30 -> 7h30 next day)
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days - 1):
            # Nếu ngày N trực L01, ngày N+1 phải OFF
            model.Add(
                schedule[(staff_id, day, "L01")] <= schedule[(staff_id, day + 1, "OFF")]
            )

    # =====================================================
    # NO CONSECUTIVE L01 (within 4 days for fatigue)
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days - 2):
            model.Add(
                schedule[(staff_id, day, "L01")] +
                schedule[(staff_id, day + 1, "L01")] +
                schedule[(staff_id, day + 2, "L01")] <= 1
            )

    # =====================================================
    # L01 + L02 CONFLICT (khong cùng ngày)
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days):
            # Khong the co L01 và L02 cùng ngày cho cùng nhân sự
            model.Add(
                schedule[(staff_id, day, "L01")] + schedule[(staff_id, day, "L02")] <= 1
            )

    # =====================================================
    # L03 + L04 CONFLICT (khong cùng ngày)
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days):
            model.Add(
                schedule[(staff_id, day, "L03")] + schedule[(staff_id, day, "L04")] <= 1
            )

    # =====================================================
    # COMPENSATION DAYS - Cannot assign L02/L03/L04 on compensation days
    # =====================================================

    compensation_days: Set[Tuple[int, int]] = set()

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days):
            work_date = start_date + timedelta(days=day)
            comp_date = calculate_compensation_date(work_date)

            # Tính offset của ngày nghỉ bù
            if comp_date >= start_date:
                comp_day_offset = (comp_date - start_date).days
                if comp_day_offset < num_days:
                    compensation_days.add((staff_id, comp_day_offset))

                    # Không thể gán L02/L03/L04 vào ngày nghỉ bù
                    model.Add(
                        schedule[(staff_id, comp_day_offset, "OFF")] == 1
                    ).OnlyEnforceIf(schedule[(staff_id, day, "L01")])

    # =====================================================
    # FATIGUE PENALTY (gần L01 trong 4 ngày)
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        for day in range(num_days - 3):
            penalty = model.NewBoolVar(f"fatigue_{staff_id}_{day}")
            model.Add(
                schedule[(staff_id, day, "L01")] +
                schedule[(staff_id, day + 1, "L01")] +
                schedule[(staff_id, day + 2, "L01")] +
                schedule[(staff_id, day + 3, "L01")] <= 3 - penalty
            )
            fatigue_penalties.append(penalty)

    # =====================================================
    # MAX SHIFTS PER STAFF
    # =====================================================

    for staff in staff_list:
        staff_id = staff.id
        max_shifts = staff_max_shifts.get(staff_id, staff.max_shifts)
        model.Add(
            sum(
                schedule[(staff_id, day, shift)]
                for day in range(num_days)
                for shift in WORK_SHIFTS
            ) <= max_shifts
        )

    # =====================================================
    # EXACT STAFF REQUIREMENT PER DAY PER SHIFT TYPE
    # =====================================================

    for day in range(num_days):
        for shift_type in WORK_SHIFTS:
            required = min_staff_per_day.get(shift_type, 0)
            model.Add(
                sum(
                    schedule[(staff.id, day, shift_type)]
                    for staff in staff_list
                ) == required
            )

    # =====================================================
    # LEAVE REQUESTS
    # =====================================================

    if leave_requests:
        for request in leave_requests:
            if request.status != "APPROVED":
                continue

            # Tính day offset từ start_date
            day_offset = (request.date - start_date).days
            if 0 <= day_offset < num_days:
                staff_id = request.staff_id
                model.Add(schedule[(staff_id, day_offset, "OFF")] == 1)

    # =====================================================
    # WORKLOAD BALANCE VARIABLES
    # =====================================================

    workloads: Dict[int, any] = {}
    for staff in staff_list:
        staff_id = staff.id
        workloads[staff_id] = model.NewIntVar(
            0, num_days, f"workload_{staff_id}"
        )
        model.Add(
            workloads[staff_id] ==
            sum(
                schedule[(staff_id, day, shift)]
                for day in range(num_days)
                for shift in WORK_SHIFTS
            )
        )

    max_workload = model.NewIntVar(0, num_days, "max_workload")
    min_workload = model.NewIntVar(0, num_days, "min_workload")

    model.AddMaxEquality(max_workload, list(workloads.values()))
    model.AddMinEquality(min_workload, list(workloads.values()))

    # =====================================================
    # OBJECTIVE FUNCTION
    # =====================================================

    # Tối đa hóa số assignment + giảm chênh lệch tải + giảm fatigue
    objective_vars = []
    objective_coeffs = []

    for staff in staff_list:
        for day in range(num_days):
            for shift in WORK_SHIFTS:
                objective_vars.append(schedule[(staff.id, day, shift)])
                objective_coeffs.append(100)

    objective_vars.extend([max_workload, min_workload])
    objective_coeffs.extend([-10, 10])

    objective_vars.extend(fatigue_penalties)
    objective_coeffs.extend([-5] * len(fatigue_penalties))

    model.Maximize(cp_model.LinearExpr.WeightedSum(objective_vars, objective_coeffs))

    # =====================================================
    # SOLVE
    # =====================================================

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = max_time_seconds

    status = solver.Solve(model)

    results: List[Dict] = []
    result_compensation_days: Set[Tuple[int, date]] = set()

    if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
        for staff in staff_list:
            staff_id = staff.id
            for day in range(num_days):
                for shift in SHIFTS:
                    if solver.Value(schedule[(staff_id, day, shift)]) == 1:
                        work_date = start_date + timedelta(days=day)
                        results.append({
                            "staff_id": staff_id,
                            "day": day + 1,
                            "work_date": work_date.isoformat(),
                            "shift": shift
                        })

                        # Tính compensation days
                        if shift == "L01":
                            comp_date = calculate_compensation_date(work_date)
                            result_compensation_days.add((staff_id, comp_date))
    else:
        print(f"KHONG TIM THAY GIAI PHAP: status = {status}")

    return results, result_compensation_days


def solve_for_month(
    staff_list: List[Staff],
    year: int,
    month: int,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None,
    min_staff_per_day: Optional[Dict[str, int]] = None,
    max_time_seconds: int = 60
) -> Tuple[Optional[List[Dict]], Set[Tuple[int, date]]]:
    """
    Giai cho mot thang

    Args:
        staff_list: Danh sach nhan su
        year: Nam
        month: Thang
        leave_requests: Danh sach don nghi phep
        staff_max_shifts: Gioi han so ca moi nhan su
        min_staff_per_day: So nhan su toi thieu moi ngày
        max_time_seconds: Thoi gian toi da

    Returns:
        (assignments, compensation_days)
    """
    import calendar

    num_days = calendar.monthrange(year, month)[1]
    start_date = date(year, month, 1)

    return solve_ortools(
        staff_list,
        start_date,
        num_days,
        leave_requests,
        staff_max_shifts,
        min_staff_per_day,
        max_time_seconds
    )


def convert_results_to_schedule(
    results: List[Dict]
) -> Dict[Tuple[int, date], str]:
    """
    Chuyen ket qua thanh schedule dict

    Args:
        results: List of {staff_id, work_date, shift}

    Returns:
        Dict {(staff_id, date): shift_type}
    """
    schedule: Dict[Tuple[int, date], str] = {}

    for item in results:
        staff_id = item["staff_id"]
        work_date = date.fromisoformat(item["work_date"])
        shift = item["shift"]

        schedule[(staff_id, work_date)] = shift

    return schedule

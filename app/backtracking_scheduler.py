"""
BACKTRACKING SCHEDULER - Thuật toán Backtracking cho xếp lịch

Thử từng phương án, quay lui nếu vi phạm ràng buộc.
Chính xác hơn Greedy, phù hợp nhóm khá.

Theo tài liệu M07: Tự động sắp xếp lịch với đầy đủ ràng buộc
"""

from datetime import date, timedelta
from typing import List, Dict, Set, Tuple, Optional
from .models import Staff, LeaveRequest, calculate_compensation_date, ScheduleEntry
from .constraints import can_assign_shift, validate_schedule


# Cac loai ca lam viec
SHIFTS = ["L01", "L02", "L03", "L04", "OFF"]

# Thu tu uu tien khi xep lich
SHIFT_PRIORITY = ["L01", "L02", "L03", "L04"]


class BacktrackingScheduler:
    """Thu tu Backtracking voi kha nang quay lui"""

    def __init__(
        self,
        staff_list: List[Staff],
        start_date: date,
        num_days: int,
        leave_requests: Optional[List[LeaveRequest]] = None,
        staff_max_shifts: Optional[Dict[int, int]] = None,
        min_staff_per_day: Optional[Dict[str, int]] = None
    ):
        self.staff_list = staff_list
        self.start_date = start_date
        self.num_days = num_days
        self.leave_requests = leave_requests or []
        self.staff_max_shifts = staff_max_shifts or {s.id: s.max_shifts for s in staff_list}
        self.min_staff_per_day = min_staff_per_day or {"L01": 1, "L02": 1, "L03": 1, "L04": 1}

        self.schedule: Dict[Tuple[int, date], str] = {}
        self.compensation_days: Set[Tuple[int, date]] = set()
        self.dates = [start_date + timedelta(days=i) for i in range(num_days)]

        self.solution_count = 0
        self.max_solutions = 1  # Chi can 1 giai phap hop le

    def solve(self) -> Tuple[Optional[Dict[Tuple[int, date], str]], Set[Tuple[int, date]]]:
        """
        Giai bai toan xep lich bang backtracking

        Returns:
            (schedule_dict, compensation_days) hoac (None, set()) neu khong co giai phap
        """
        self.solution_count = 0
        self.schedule = {}
        self.compensation_days = set()

        # Bat dau quy trinh backtracking
        result = self._backtrack(0)

        if result:
            return result, self.compensation_days
        return None, set()

    def _backtrack(self, index: int) -> Optional[Dict[Tuple[int, date], str]]:
        """
        Ham backtracking chinh

        Args:
            index: Vi tri hien tai trong chuoi (staff * day)

        Returns:
            Schedule dict neu tim duoc giai phap, None neu can quay lui
        """
        # Neu da xep het tat ca -> kiem tra ràng buộc
        if index >= len(self.staff_list) * len(self.dates):
            return self._verify_and_finalize()

        # Tinh vi tri staff va day
        staff_idx = index % len(self.staff_list)
        day_idx = index // len(self.staff_list)

        staff = self.staff_list[staff_idx]
        work_date = self.dates[day_idx]

        # Thu cac loai ca theo thu tu uu tien
        for shift in SHIFT_PRIORITY:
            # Kiem tra co the gan ca nay khong
            can_assign, error = can_assign_shift(
                self.schedule,
                self.compensation_days,
                staff.id,
                work_date,
                shift,
                self.leave_requests,
                self.staff_max_shifts
            )

            if can_assign:
                # Gan tam thoi
                self.schedule[(staff.id, work_date)] = shift

                # Neu la L01, tinh ngay nghi bu
                if shift == "L01":
                    comp_date = calculate_compensation_date(work_date)
                    self.compensation_days.add((staff.id, comp_date))

                # De quy tiep tuc
                result = self._backtrack(index + 1)
                if result:
                    return result

                # Quay lui
                if (staff.id, work_date) in self.schedule:
                    del self.schedule[(staff.id, work_date)]
                if shift == "L01":
                    self.compensation_days.discard((staff.id, comp_date))

        # Thu OFF (khong lam viec)
        self.schedule[(staff.id, work_date)] = "OFF"
        result = self._backtrack(index + 1)
        if result:
            return result

        if (staff.id, work_date) in self.schedule:
            del self.schedule[(staff.id, work_date)]

        return None

    def _verify_and_finalize(self) -> Optional[Dict[Tuple[int, date], str]]:
        """
        Kiem tra ràng buộc cuoi cung va tra ve ket qua

        Returns:
            Schedule dict neu hop le, None neu vi phạm
        """
        # Kiem tra so nhan su toi thieu moi ngay
        for work_date in self.dates:
            for shift_type, min_required in self.min_staff_per_day.items():
                count = sum(
                    1 for (staff_id, d), s in self.schedule.items()
                    if d == work_date and s == shift_type
                )
                if count < min_required:
                    return None

        # Kiem tra ràng buộc tong hop
        is_valid, conflicts = validate_schedule(
            self.schedule,
            self.compensation_days,
            self.leave_requests,
            self.staff_max_shifts,
            self.min_staff_per_day
        )

        if is_valid:
            self.solution_count += 1
            if self.solution_count >= self.max_solutions:
                return self.schedule.copy()

        return None

    def solve_with_all_shifts(
        self,
        required_assignments: Dict[Tuple[int, date], str]
    ) -> Tuple[Optional[Dict[Tuple[int, date], str]], Set[Tuple[int, date]]]:
        """
        Giai voi mot so assignment da co san

        Args:
            required_assignments: Dict cac assignment da duoc xep truoc

        Returns:
            (schedule_dict, compensation_days)
        """
        self.schedule = required_assignments.copy()

        # Tinh compensation_days tu cac L01 da co
        for (staff_id, work_date), shift in required_assignments.items():
            if shift == "L01":
                comp_date = calculate_compensation_date(work_date)
                self.compensation_days.add((staff_id, comp_date))

        # Tien hanh backtracking cho cac vi tri con lai
        result = self._backtrack(0)

        if result:
            return result, self.compensation_days
        return None, set()


def solve(
    staff_ids: List[int],
    dates: List[date],
    leave_requests: Optional[List[LeaveRequest]] = None
) -> Optional[Dict[Tuple[int, date], str]]:
    """
    Ham giai thuat don gian goi tu ben ngoai

    Args:
        staff_ids: Danh sach ma nhan su
        dates: Danh sach ngay
        leave_requests: Danh sach don nghi phep

    Returns:
        Schedule dict hoac None
    """
    # Tao staff list don gian
    staff_list = [Staff(id=sid, name=f"Staff {sid}", specialty="GENERAL") for sid in staff_ids]

    # Chay scheduler
    scheduler = BacktrackingScheduler(
        staff_list,
        dates[0] if dates else date.today(),
        len(dates),
        leave_requests
    )

    result, _ = scheduler.solve()
    return result


def solve_for_month(
    staff_list: List[Staff],
    year: int,
    month: int,
    leave_requests: Optional[List[LeaveRequest]] = None,
    staff_max_shifts: Optional[Dict[int, int]] = None,
    min_staff_per_day: Optional[Dict[str, int]] = None
) -> Tuple[Optional[Dict[Tuple[int, date], str]], Set[Tuple[int, date]]]:
    """
    Giai cho mot thang

    Args:
        staff_list: Danh sach nhan su
        year: Nam
        month: Thang
        leave_requests: Danh sach don nghi phep
        staff_max_shifts: Gioi han so ca moi nhan su
        min_staff_per_day: So nhan su toi thieu moi ngay

    Returns:
        (schedule_dict, compensation_days)
    """
    import calendar

    # Tinh so ngay trong thang
    num_days = calendar.monthrange(year, month)[1]
    start_date = date(year, month, 1)

    scheduler = BacktrackingScheduler(
        staff_list,
        start_date,
        num_days,
        leave_requests,
        staff_max_shifts,
        min_staff_per_day
    )

    return scheduler.solve()

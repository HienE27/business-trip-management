"""
MAIN MODULE - Integration cho he thong xep lich

Ket hop tat ca cac thanh phan:
- Models: Cau truc du lieu
- Constraints: Rang buoc nghiep vu
- Schedulers: Cac thuat toan xep lich
- Scoring: Danh gia chat luong lich

Su dung:
    python main.py
"""

import json
from datetime import date, timedelta
from typing import List, Dict, Set, Tuple, Optional

from .models import (
    Staff, LeaveRequest, ScheduleEntry, CompensationDay,
    ScheduleConflict, ScheduleDraft, calculate_compensation_date
)
from .constraints import (
    validate_schedule, can_assign_shift, get_available_staff_for_day,
    generate_compensation_days_from_schedule
)
from .greedy_scheduler import (
    generate_greedy_schedule, generate_schedule_with_unassigned_days,
    suggest_replacement, calculate_workload_balance, get_uncovered_days
)
from .backtracking_scheduler import BacktrackingScheduler, solve_for_month as backtrack_for_month
from .ortools_scheduler import solve_ortools, solve_for_month as ortools_for_month, convert_results_to_schedule
from .scoring import (
    fairness_score, fatigue_score, compensation_score,
    coverage_score, total_score, workload_distribution
)
from .input_loader import load_input
from .output_writer import write_output


class ScheduleManager:
    """Quan ly lich - Interface chinh cua he thong"""

    def __init__(
        self,
        staff_list: List[Staff],
        year: int,
        month: int,
        leave_requests: Optional[List[LeaveRequest]] = None
    ):
        self.staff_list = staff_list
        self.year = year
        self.month = month
        self.leave_requests = leave_requests or []

        import calendar
        self.num_days = calendar.monthrange(year, month)[1]
        self.start_date = date(year, month, 1)
        self.dates = [self.start_date + timedelta(days=i) for i in range(self.num_days)]

        self.schedule: Dict[Tuple[int, date], str] = {}
        self.compensation_days: Set[Tuple[int, date]] = set()
        self.conflicts: List[ScheduleConflict] = []
        self.errors: List[str] = []

    def auto_schedule_greedy(self) -> bool:
        """Tu dong xep lich bang thuat toan Greedy"""
        self.schedule, self.compensation_days, self.errors = generate_greedy_schedule(
            self.staff_list,
            self.start_date,
            self.num_days,
            self.leave_requests
        )
        return len(self.errors) == 0

    def auto_schedule_backtracking(self) -> bool:
        """Tu dong xep lich bang thuat toan Backtracking"""
        scheduler = BacktrackingScheduler(
            self.staff_list,
            self.start_date,
            self.num_days,
            self.leave_requests
        )

        result, comp_days = scheduler.solve()

        if result:
            self.schedule = result
            self.compensation_days = comp_days
            return True

        self.errors = ["Khong tim thay giai phap hop le"]
        return False

    def auto_schedule_ortools(self, max_time: int = 60) -> bool:
        """Tu dong xep lich bang OR-Tools"""
        results, comp_days = solve_ortools(
            self.staff_list,
            self.start_date,
            self.num_days,
            self.leave_requests,
            max_time_seconds=max_time
        )

        if results:
            self.schedule = convert_results_to_schedule(results)
            self.compensation_days = comp_days
            return True

        self.errors = ["Khong tim thay giai phap hop le voi OR-Tools"]
        return False

    def validate(self) -> bool:
        """Kiem tra rang buoc"""
        is_valid, conflicts = validate_schedule(
            self.schedule,
            self.compensation_days,
            self.leave_requests
        )

        self.conflicts = conflicts
        return is_valid

    def get_score(self) -> Dict[str, float]:
        """Tinh diem chat luong lich"""
        staff_max_shifts = {s.id: s.max_shifts for s in self.staff_list}

        return total_score(
            self.schedule,
            self.compensation_days,
            self.staff_list,
            dates=self.dates
        )

    def get_workload(self) -> Dict[int, Dict[str, int]]:
        """Thong ke phan bo tai"""
        return workload_distribution(self.schedule, self.staff_list)

    def get_uncovered(self) -> List[Tuple[date, str]]:
        """Lay danh sach ngay chua du nhan su"""
        return get_uncovered_days(self.schedule, self.dates)

    def assign_shift(
        self,
        staff_id: int,
        work_date: date,
        shift_type: str
    ) -> Tuple[bool, Optional[str]]:
        """Gan mot ca cho nhan su"""
        can_assign, error = can_assign_shift(
            self.schedule,
            self.compensation_days,
            staff_id,
            work_date,
            shift_type,
            self.leave_requests
        )

        if can_assign:
            self.schedule[(staff_id, work_date)] = shift_type

            if shift_type == "L01":
                comp_date = calculate_compensation_date(work_date)
                self.compensation_days.add((staff_id, comp_date))

            return True, None

        return False, error

    def remove_shift(
        self,
        staff_id: int,
        work_date: date
    ) -> bool:
        """Xoa mot ca cua nhan su"""
        key = (staff_id, work_date)

        if key in self.schedule:
            # Neu la L01, xoa luon ngay nghi bu
            if self.schedule[key] == "L01":
                comp_date = calculate_compensation_date(work_date)
                self.compensation_days.discard((staff_id, comp_date))

            del self.schedule[key]
            return True

        return False

    def suggest_swap(self, staff_id: int, work_date: date) -> Optional[int]:
        """De xuat nguoi thay the"""
        shift_type = self.schedule.get((staff_id, work_date))

        if not shift_type:
            return None

        return suggest_replacement(
            self.schedule,
            self.staff_list,
            self.compensation_days,
            staff_id,
            work_date,
            shift_type,
            self.leave_requests
        )

    def export(self) -> Dict:
        """Xuat ket qua"""
        return {
            "month": f"{self.year}-{self.month:02d}",
            "staffs": [
                {"id": s.id, "name": s.name, "specialty": s.specialty}
                for s in self.staff_list
            ],
            "assignments": [
                {
                    "staff_id": staff_id,
                    "day": d.day,
                    "work_date": d.isoformat(),
                    "shift": shift
                }
                for (staff_id, d), shift in self.schedule.items()
            ],
            "compensation_days": [
                {
                    "staff_id": staff_id,
                    "compensation_date": d.isoformat()
                }
                for (staff_id, d) in self.compensation_days
            ],
            "conflicts": [
                {
                    "staff_id": c.staff_id,
                    "date": c.date.isoformat() if c.date else None,
                    "type": c.conflict_type,
                    "message": c.message
                }
                for c in self.conflicts
            ],
            "statistics": {
                "score": self.get_score(),
                "workload": self.get_workload(),
                "uncovered": [
                    {"date": d.isoformat(), "shift": s}
                    for d, s in self.get_uncovered()
                ]
            }
        }


def run_demo():
    """Chay demo voi du lieu mau"""
    print("=" * 60)
    print("HE THONG XEP LICH CONG TAC - DEMO")
    print("=" * 60)

    # Tao du lieu mau
    staff_list = [
        Staff(id=1, name="Dr. An", specialty="NOI", max_shifts=5),
        Staff(id=2, name="Dr. Binh", specialty="NGOAI", max_shifts=5),
        Staff(id=3, name="Dr. Cuong", specialty="ICU", max_shifts=4),
        Staff(id=4, name="Dr. Dung", specialty="SAN", max_shifts=5),
        Staff(id=5, name="Dr. Hoa", specialty="NHI", max_shifts=5),
    ]

    leave_requests = [
        LeaveRequest(staff_id=1, date=date(2026, 5, 5), reason="Nghi phep", status="APPROVED"),
        LeaveRequest(staff_id=2, date=date(2026, 5, 10), reason="Nghi phep", status="APPROVED"),
    ]

    # Khoi tao manager
    manager = ScheduleManager(staff_list, 2026, 5, leave_requests)

    print("\n1. Xep lich bang thuat toan GREEDY...")
    success = manager.auto_schedule_greedy()
    print(f"   Ket qua: {'Thanh cong' if success else 'That bai'}")

    if success:
        print("\n2. Kiem tra rang buoc...")
        is_valid = manager.validate()
        print(f"   Lich hop le: {is_valid}")

        if not is_valid:
            print("   Xung dot:")
            for conflict in manager.conflicts:
                print(f"   - {conflict.message}")

        print("\n3. Diem chat luong lich:")
        score = manager.get_score()
        for key, value in score.items():
            print(f"   - {key}: {value}")

        print("\n4. Phan bo tai:")
        workload = manager.get_workload()
        for staff_id, counts in workload.items():
            print(f"   Staff {staff_id}: {counts}")

        print("\n5. Ngay nghi bu (automatic):")
        for (staff_id, comp_date) in manager.compensation_days:
            print(f"   Staff {staff_id} -> Nghi bu ngay {comp_date}")

        print("\n6. Xuat ket qua JSON:")
        output = manager.export()
        print(json.dumps(output, indent=2, default=str))

    print("\n" + "=" * 60)
    print("DEMO HOAN TAT")
    print("=" * 60)


def run_from_file(input_path: str, output_path: str, algorithm: str = "greedy"):
    """Chay tu file input"""
    print(f"Loading input from: {input_path}")
    data = load_input(input_path)

    # Chuyen doi sang model
    staff_list = [
        Staff(
            id=s["id"],
            name=s.get("name", f"Staff {s['id']}"),
            specialty=s.get("specialty", "GENERAL"),
            max_shifts=s.get("max_shifts", 5)
        )
        for s in data["staffs"]
    ]

    leave_requests = [
        LeaveRequest(
            staff_id=r["staff_id"],
            date=date.fromisoformat(r["date"]),
            reason=r.get("reason", ""),
            status=r.get("status", "APPROVED")
        )
        for r in data.get("leave_requests", [])
    ]

    # Parse month
    month_str = data.get("month", "2026-05")
    year, month = map(int, month_str.split("-"))

    # Khoi tao va chay
    manager = ScheduleManager(staff_list, year, month, leave_requests)

    if algorithm == "greedy":
        manager.auto_schedule_greedy()
    elif algorithm == "backtracking":
        manager.auto_schedule_backtracking()
    elif algorithm == "ortools":
        manager.auto_schedule_ortools()
    else:
        print(f"Unknown algorithm: {algorithm}")
        return

    # Kiem tra va xuat
    manager.validate()
    output = manager.export()

    print(f"Writing output to: {output_path}")
    write_output(output, output_path)

    print("Hoan tat!")


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1:
        input_file = sys.argv[1]
        output_file = sys.argv[2] if len(sys.argv) > 2 else "output.json"
        algo = sys.argv[3] if len(sys.argv) > 3 else "greedy"
        run_from_file(input_file, output_file, algo)
    else:
        run_demo()

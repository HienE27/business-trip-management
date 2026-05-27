import argparse
import calendar
import json
from datetime import date, timedelta
from typing import Dict, List, Optional, Set, Tuple

from .backtracking_scheduler import BacktrackingScheduler
from .constraints import validate_schedule
from .greedy_scheduler import generate_greedy_schedule, get_uncovered_days
from .input_loader import load_input
from .models import LeaveRequest, ScheduleConflict, Staff
from .ortools_scheduler import convert_results_to_schedule, solve_ortools
from .output_writer import write_output
from .scoring import total_score, workload_distribution

WORK_SHIFTS = {"L01", "L02", "L03", "L04"}


def run_engine(data: Dict, algorithm: Optional[str] = None) -> Dict:
    algorithm = (algorithm or data.get("algorithm") or "ortools").lower()
    staff_list = _parse_staff(data)
    leave_requests = _parse_leave_requests(data)
    start_date, num_days, period_label = _parse_period(data)
    dates = [start_date + timedelta(days=i) for i in range(num_days)]
    min_staff_per_day = data.get("min_staff_per_day") or {"L01": 1}
    max_time_seconds = int(data.get("max_time_seconds", 30))

    errors: List[str] = []
    schedule: Dict[Tuple[int, date], str] = {}
    compensation_days: Set[Tuple[int, date]] = set()

    if algorithm == "ortools":
        results, compensation_days = solve_ortools(
            staff_list,
            start_date,
            num_days,
            leave_requests=leave_requests,
            min_staff_per_day=min_staff_per_day,
            max_time_seconds=max_time_seconds,
        )
        if not results:
            errors.append("OR-Tools could not find a feasible schedule")
        else:
            schedule = convert_results_to_schedule(results)
    elif algorithm == "greedy":
        schedule, compensation_days, errors = generate_greedy_schedule(
            staff_list,
            start_date,
            num_days,
            leave_requests=leave_requests,
            min_staff_per_day=min_staff_per_day,
        )
    elif algorithm == "backtracking":
        scheduler = BacktrackingScheduler(
            staff_list,
            start_date,
            num_days,
            leave_requests=leave_requests,
            min_staff_per_day=min_staff_per_day,
        )
        schedule, compensation_days = scheduler.solve()
        schedule = schedule or {}
        if not schedule:
            errors.append("Backtracking could not find a feasible schedule")
    else:
        raise ValueError(f"Unsupported algorithm: {algorithm}")

    is_valid = False
    conflicts: List[ScheduleConflict] = []
    if schedule:
        is_valid, conflicts = validate_schedule(
            schedule,
            compensation_days,
            leave_requests=leave_requests,
            staff_max_shifts={staff.id: staff.max_shifts for staff in staff_list},
            required_per_day=min_staff_per_day,
        )

    uncovered = get_uncovered_days(schedule, dates, min_staff_per_day) if schedule else []
    status = _status(schedule, is_valid, conflicts, uncovered, errors)

    return {
        "schema_version": "1.0",
        "algorithm": algorithm,
        "period": {
            "label": period_label,
            "start_date": start_date.isoformat(),
            "end_date": dates[-1].isoformat(),
            "num_days": num_days,
        },
        "status": status,
        "valid": is_valid,
        "assignments": _serialize_assignments(schedule),
        "compensation_days": _serialize_compensation_days(compensation_days),
        "conflicts": _serialize_conflicts(conflicts),
        "metrics": {
            "score": total_score(
                schedule,
                compensation_days,
                staff_list,
                dates=dates,
                min_staff_per_day=min_staff_per_day,
            ) if schedule else {},
            "workload": workload_distribution(schedule, staff_list) if schedule else {},
            "uncovered": [
                {"date": work_date.isoformat(), "shift": shift}
                for work_date, shift in sorted(uncovered)
            ],
        },
        "errors": errors,
    }


def _parse_staff(data: Dict) -> List[Staff]:
    return [
        Staff(
            id=int(item["id"]),
            name=item.get("name", f"Staff {item['id']}"),
            specialty=item.get("specialty", "GENERAL"),
            max_shifts=int(item.get("max_shifts", 5)),
            is_active=bool(item.get("is_active", True)),
        )
        for item in data.get("staffs", [])
    ]


def _parse_leave_requests(data: Dict) -> List[LeaveRequest]:
    return [
        LeaveRequest(
            staff_id=int(item["staff_id"]),
            date=date.fromisoformat(item["date"]),
            reason=item.get("reason", ""),
            status=item.get("status", "APPROVED"),
        )
        for item in data.get("leave_requests", [])
    ]


def _parse_period(data: Dict) -> Tuple[date, int, str]:
    if "start_date" in data and "end_date" in data:
        start = date.fromisoformat(data["start_date"])
        end = date.fromisoformat(data["end_date"])
        if end < start:
            raise ValueError("end_date must be greater than or equal to start_date")
        return start, (end - start).days + 1, f"{start.isoformat()}..{end.isoformat()}"

    month = data.get("month")
    if not month:
        raise ValueError("Input must include either month or start_date/end_date")
    year, month_number = map(int, month.split("-"))
    num_days = calendar.monthrange(year, month_number)[1]
    return date(year, month_number, 1), num_days, month


def _serialize_assignments(schedule: Dict[Tuple[int, date], str]) -> List[Dict]:
    rows = []
    for (staff_id, work_date), shift in sorted(schedule.items(), key=lambda item: (item[0][1], item[0][0])):
        if shift not in WORK_SHIFTS:
            continue
        rows.append({
            "staff_id": staff_id,
            "work_date": work_date.isoformat(),
            "shift_type": shift,
        })
    return rows


def _serialize_compensation_days(compensation_days: Set[Tuple[int, date]]) -> List[Dict]:
    return [
        {"staff_id": staff_id, "compensation_date": comp_date.isoformat()}
        for staff_id, comp_date in sorted(compensation_days, key=lambda item: (item[1], item[0]))
    ]


def _serialize_conflicts(conflicts: List[ScheduleConflict]) -> List[Dict]:
    return [
        {
            "staff_id": conflict.staff_id,
            "date": conflict.date.isoformat() if conflict.date else None,
            "shift1": conflict.shift1,
            "shift2": conflict.shift2,
            "type": conflict.conflict_type,
            "message": conflict.message,
        }
        for conflict in conflicts
    ]


def _status(schedule, is_valid, conflicts, uncovered, errors) -> str:
    if errors and not schedule:
        return "infeasible"
    if is_valid and not conflicts and not uncovered:
        return "success"
    return "warning"


def main() -> None:
    parser = argparse.ArgumentParser(description="Hospital scheduling engine")
    parser.add_argument("input", help="Path to input JSON")
    parser.add_argument("output", nargs="?", help="Path to output JSON")
    parser.add_argument("--algorithm", choices=["ortools", "greedy", "backtracking"], default=None)
    parser.add_argument("--pretty", action="store_true", help="Print formatted JSON to stdout")
    args = parser.parse_args()

    output = run_engine(load_input(args.input), args.algorithm)
    if args.output:
        write_output(output, args.output)
    if args.pretty or not args.output:
        print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

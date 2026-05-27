from datetime import date

from app.constraints import (
    can_assign_shift,
    check_l01_l02_conflict,
    check_l03_l04_conflict,
    check_leave_requests,
    check_max_shift_limit,
    check_minimum_staff,
    check_on_compensation_day,
    check_rest_after_24h,
    generate_compensation_days_from_schedule,
    get_available_staff_for_day,
    validate_schedule,
)
from app.models import LeaveRequest, Staff, calculate_compensation_date


def test_compensation_date_rules():
    assert calculate_compensation_date(date(2026, 6, 1)) == date(2026, 6, 2)
    assert calculate_compensation_date(date(2026, 6, 4)) == date(2026, 6, 5)
    assert calculate_compensation_date(date(2026, 6, 5)) == date(2026, 6, 9)
    assert calculate_compensation_date(date(2026, 6, 6)) == date(2026, 6, 9)
    assert calculate_compensation_date(date(2026, 6, 7)) == date(2026, 6, 8)


def test_l01_l02_conflict_detection_for_multi_shift_value():
    schedule = {(1, date(2026, 6, 1)): ["L01", "L02"]}
    assert check_l01_l02_conflict(schedule, 1, date(2026, 6, 1)) is True


def test_l03_l04_conflict_detection_for_multi_shift_value():
    schedule = {(1, date(2026, 6, 1)): ["L03", "L04"]}
    assert check_l03_l04_conflict(schedule, 1, date(2026, 6, 1)) is True


def test_can_assign_blocks_l01_l02_same_day():
    schedule = {(1, date(2026, 6, 1)): "L01"}
    can_assign, message = can_assign_shift(schedule, set(), 1, date(2026, 6, 1), "L02")
    assert can_assign is False
    assert "Truc 24/24" in message


def test_can_assign_blocks_l03_l04_same_day():
    schedule = {(1, date(2026, 6, 1)): "L03"}
    can_assign, message = can_assign_shift(schedule, set(), 1, date(2026, 6, 1), "L04")
    assert can_assign is False
    assert "Dich Vu" in message


def test_can_assign_blocks_compensation_day():
    compensation_days = {(1, date(2026, 6, 2))}
    can_assign, message = can_assign_shift({}, compensation_days, 1, date(2026, 6, 2), "L02")
    assert can_assign is False
    assert "nghi bu" in message


def test_rest_after_l01_requires_next_day_off():
    schedule = {
        (1, date(2026, 6, 1)): "L01",
        (1, date(2026, 6, 2)): "L02",
    }
    assert check_rest_after_24h(schedule) is False


def test_rest_after_l01_allows_missing_or_off_next_day():
    assert check_rest_after_24h({(1, date(2026, 6, 1)): "L01"}) is True
    assert check_rest_after_24h({
        (1, date(2026, 6, 1)): "L01",
        (1, date(2026, 6, 2)): "OFF",
    }) is True


def test_max_shift_limit():
    schedule = {
        (1, date(2026, 6, 1)): "L01",
        (1, date(2026, 6, 2)): "L02",
    }
    assert check_max_shift_limit(schedule, {1: 1}) is False
    assert check_max_shift_limit(schedule, {1: 2}) is True


def test_leave_requests_block_approved_leave_only():
    schedule = {(1, date(2026, 6, 1)): "L01"}
    approved = [LeaveRequest(1, date(2026, 6, 1), status="APPROVED")]
    pending = [LeaveRequest(1, date(2026, 6, 1), status="PENDING")]

    assert check_leave_requests(schedule, approved) is False
    assert check_leave_requests(schedule, pending) is True


def test_minimum_staff_requirement():
    schedule = {
        (1, date(2026, 6, 1)): "L01",
        (2, date(2026, 6, 1)): "L02",
    }
    assert check_minimum_staff(schedule, {"L01": 1, "L02": 1}) is True
    assert check_minimum_staff(schedule, {"L01": 2}) is False


def test_validate_schedule_reports_core_conflicts():
    schedule = {
        (1, date(2026, 6, 1)): "L01",
        (1, date(2026, 6, 2)): "L02",
        (2, date(2026, 6, 1)): "L03",
    }
    compensation_days = {(2, date(2026, 6, 1))}
    leave_requests = [LeaveRequest(2, date(2026, 6, 1), status="APPROVED")]

    is_valid, conflicts = validate_schedule(
        schedule,
        compensation_days,
        leave_requests=leave_requests,
        staff_max_shifts={1: 1, 2: 5},
        required_per_day={"L01": 1},
    )

    assert is_valid is False
    conflict_types = {conflict.conflict_type for conflict in conflicts}
    assert "REST_VIOLATION" in conflict_types
    assert "COMPENSATION_VIOLATION" in conflict_types
    assert "LEAVE_VIOLATION" in conflict_types
    assert "MAX_SHIFT_VIOLATION" in conflict_types


def test_get_available_staff_for_day_filters_unavailable_staff():
    staff = [
        Staff(1, "A", "NOI", is_active=True),
        Staff(2, "B", "NOI", is_active=False),
        Staff(3, "C", "NOI", is_active=True),
    ]
    schedule = {(1, date(2026, 6, 1)): "L01"}
    compensation_days = {(3, date(2026, 6, 1))}

    assert get_available_staff_for_day(
        schedule,
        compensation_days,
        staff,
        date(2026, 6, 1),
        "L02",
    ) == []


def test_generate_compensation_days_from_schedule():
    schedule = {(1, date(2026, 6, 5)): "L01"}
    assert generate_compensation_days_from_schedule(schedule) == {(1, date(2026, 6, 9))}


def test_check_on_compensation_day():
    assert check_on_compensation_day({(1, date(2026, 6, 2))}, 1, date(2026, 6, 2), "L02") is True
    assert check_on_compensation_day({(1, date(2026, 6, 2))}, 1, date(2026, 6, 3), "L02") is False

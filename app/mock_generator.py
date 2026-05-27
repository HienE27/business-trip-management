"""
MOCK DATA GENERATOR - Tao du lieu mau

Dung de test he thong voi du lieu mau:
- Schedule ngau nhien
- Du lieu nhan su mau
"""

import random
from datetime import date, timedelta
from typing import List, Dict, Tuple

from .models import Staff, LeaveRequest


# Cac loai ca lam viec
SHIFTS = ["L01", "L02", "L03", "L04", "OFF"]
WORK_SHIFTS = ["L01", "L02", "L03", "L04"]


def generate_mock_schedule(
    num_staff: int = 5,
    num_days: int = 7,
    max_shifts_per_staff: int = 3
) -> Dict[Tuple[int, int], str]:
    """
    Tao schedule ngau nhien

    Args:
        num_staff: So nhan su
        num_days: So ngay
        max_shifts_per_staff: So ca toi da moi nhan su

    Returns:
        Dict {(staff_id, day): shift_type}
    """
    schedule = {}

    for day in range(1, num_days + 1):
        for staff_id in range(1, num_staff + 1):
            # Xac suat co viec lam
            if random.random() < 0.5:
                # Chi cho phep so ca gioi han
                current_shifts = sum(
                    1 for (sid, d), s in schedule.items()
                    if sid == staff_id and s != "OFF"
                )

                if current_shifts < max_shifts_per_staff:
                    schedule[(staff_id, day)] = random.choice(WORK_SHIFTS)
                else:
                    schedule[(staff_id, day)] = "OFF"
            else:
                schedule[(staff_id, day)] = "OFF"

    return schedule


def generate_staff_list(num_staff: int = 20) -> List[Staff]:
    """
    Tao danh sach nhan su mau

    Args:
        num_staff: So nhan su

    Returns:
        Danh sach Staff
    """
    specialties = ["NOI", "NGOAI", "SAN", "NHI", "MATT", "RANG"]
    names = [
        "An", "Binh", "Cuong", "Dung", "Hoa", "Hieu", "Lan", "Mai",
        "Nam", "Ngoc", "Phuong", "Quan", "Son", "Thanh", "Tien",
        "Trang", "Tung", "Viet", "Xuan", "Yen"
    ]

    staff_list = []
    for i in range(1, num_staff + 1):
        staff = Staff(
            id=i,
            name=f"Dr. {names[i % len(names)]}",
            specialty=specialties[i % len(specialties)],
            max_shifts=5
        )
        staff_list.append(staff)

    return staff_list


def generate_leave_requests(
    staff_list: List[Staff],
    num_days: int,
    leave_probability: float = 0.1
) -> List[LeaveRequest]:
    """
    Tao don nghi phep ngau nhien

    Args:
        staff_list: Danh sach nhan su
        num_days: So ngay
        leave_probability: Xac suat nghi phep moi ngay

    Returns:
        Danh sach LeaveRequest
    """
    leave_requests = []

    for staff in staff_list:
        for day in range(1, num_days + 1):
            if random.random() < leave_probability:
                request = LeaveRequest(
                    staff_id=staff.id,
                    date=date(2026, 5, day),
                    reason="Nghi phep ngau nhien",
                    status="APPROVED"
                )
                leave_requests.append(request)

    return leave_requests


def generate_test_data(
    num_staff: int = 20,
    year: int = 2026,
    month: int = 5
) -> Dict:
    """
    Tao du lieu test day du

    Args:
        num_staff: So nhan su
        year: Nam
        month: Thang

    Returns:
        Dict chua staff, leave_requests, schedule
    """
    import calendar
    num_days = calendar.monthrange(year, month)[1]

    staff_list = generate_staff_list(num_staff)
    leave_requests = generate_leave_requests(staff_list, num_days)

    return {
        "staffs": [
            {
                "id": s.id,
                "name": s.name,
                "specialty": s.specialty,
                "max_shifts": s.max_shifts
            }
            for s in staff_list
        ],
        "leave_requests": [
            {
                "staff_id": r.staff_id,
                "date": r.date.isoformat(),
                "status": r.status
            }
            for r in leave_requests
        ],
        "month": f"{year}-{month:02d}"
    }


if __name__ == "__main__":
    # Test mock generator
    print("=== TEST MOCK GENERATOR ===")

    schedule = generate_mock_schedule(num_staff=5, num_days=7)
    print("Schedule:", schedule)

    staff_list = generate_staff_list(num_staff=10)
    print("\nStaff count:", len(staff_list))

    leave_requests = generate_leave_requests(staff_list, 30)
    print("Leave requests:", len(leave_requests))

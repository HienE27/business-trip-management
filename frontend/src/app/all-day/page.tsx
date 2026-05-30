import { DashboardShell } from "@/components/layout/DashboardShell";
import { ScheduleCrudPanel, type ScheduleRecord } from "@/components/operations/ScheduleCrudPanel";

const allDayRows: ScheduleRecord[] = [
  {
    id: "all-day-1",
    date: "2026-05-27",
    staff: "Tran Duc Huy",
    specialty: "Ngoại",
    location: "Khoa khám",
    note: "Không nghỉ trưa",
    status: "Hợp lệ",
    shiftType: "L02",
  },
  {
    id: "all-day-2",
    date: "2026-05-31",
    staff: "Nguyen Minh Anh",
    specialty: "Nội tổng hợp",
    location: "Khoa khám",
    note: "Cần đối chiếu lịch trực",
    status: "Chặn lưu",
    shiftType: "L02",
  },
];

export default function AllDayPage() {
  return (
    <DashboardShell
      activeCode="M03"
      description="Xếp lịch làm liên tục không nghỉ trưa, chỉ chọn ngày và nhân sự."
      primaryAction="Lưu lịch thông tầm"
      secondaryAction="Quét xung đột"
      title="M03 - Lịch thông tầm"
    >
      <ScheduleCrudPanel
        defaultRows={allDayRows}
        description="Dữ liệu trực 24/24 và nghỉ bù được đối chiếu để tránh xung đột"
        locationLabel="Khu vực"
        shiftType="L02"
        submitLabel="Tạo lịch thông tầm"
        title="Bảng lịch thông tầm"
      />
    </DashboardShell>
  );
}

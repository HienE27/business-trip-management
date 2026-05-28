import { DashboardShell } from "@/components/layout/DashboardShell";
import { ScheduleCrudPanel, type ScheduleRecord } from "@/components/operations/ScheduleCrudPanel";

const clinicServiceRows: ScheduleRecord[] = [
  {
    id: "service-1",
    date: "2026-05-27",
    staff: "Le Bao Chau",
    specialty: "Nhi",
    location: "PK dịch vụ 01",
    note: "Ca khám dịch vụ",
    status: "Hợp lệ",
    shiftType: "L03",
  },
  {
    id: "service-2",
    date: "2026-05-31",
    staff: "Le Bao Chau",
    specialty: "Nhi",
    location: "PK dịch vụ 01",
    note: "Cần đối chiếu chuyên gia",
    status: "Cần kiểm tra",
    shiftType: "L03",
  },
];

export default function ServiceClinicPage() {
  return (
    <DashboardShell
      activeCode="M04"
      description="Gán nhân sự phụ trách phòng khám dịch vụ theo ngày và kiểm tra trùng lịch chuyên gia."
      primaryAction="Lưu lịch dịch vụ"
      secondaryAction="Kiểm tra chuyên gia"
      title="M04 - Lịch phòng khám dịch vụ"
    >
      <ScheduleCrudPanel
        defaultRows={clinicServiceRows}
        description="Có thể thêm, sửa, xóa lịch dịch vụ và kiểm tra trùng lịch chuyên gia"
        locationLabel="Phòng khám"
        shiftType="L03"
        submitLabel="Tạo lịch dịch vụ"
        title="Bảng phân công phòng khám dịch vụ"
      />
    </DashboardShell>
  );
}

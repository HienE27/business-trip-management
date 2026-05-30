import { DashboardShell } from "@/components/layout/DashboardShell";
import { ScheduleCrudPanel, type ScheduleRecord } from "@/components/operations/ScheduleCrudPanel";

const expertClinicRows: ScheduleRecord[] = [
  {
    id: "expert-1",
    date: "2026-05-27",
    staff: "Do Lan Phuong",
    specialty: "Mắt",
    location: "Khám chuyên sâu",
    note: "Lịch chuyên gia",
    status: "Hợp lệ",
    shiftType: "L04",
  },
  {
    id: "expert-2",
    date: "2026-05-31",
    staff: "Tran Minh Khoa",
    specialty: "Răng hàm mặt",
    location: "Khám chuyên sâu",
    note: "Chờ phân công",
    status: "Cần kiểm tra",
    shiftType: "L04",
  },
];

export default function ExpertClinicPage() {
  return (
    <DashboardShell
      activeCode="M05"
      description="Lọc chuyên khoa, gán chuyên gia khám chuyên sâu và tránh trùng lịch dịch vụ."
      primaryAction="Lưu lịch chuyên gia"
      secondaryAction="Lọc chuyên khoa"
      title="M05 - Lịch phòng khám chuyên gia"
    >
      <ScheduleCrudPanel
        defaultRows={expertClinicRows}
        description="Có thể thêm, sửa, xóa lịch chuyên gia và lọc chuyên khoa khi tích hợp API"
        locationLabel="Nội dung"
        shiftType="L04"
        submitLabel="Tạo lịch chuyên gia"
        title="Bảng lịch chuyên gia"
      />
    </DashboardShell>
  );
}

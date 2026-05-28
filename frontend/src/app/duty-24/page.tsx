import { DashboardShell } from "@/components/layout/DashboardShell";
import { ScheduleCrudPanel, type ScheduleRecord } from "@/components/operations/ScheduleCrudPanel";

const dutyRows: ScheduleRecord[] = [
  {
    id: "duty-1",
    date: "2026-05-27",
    staff: "Nguyen Minh Anh",
    specialty: "Nội tổng hợp",
    location: "Khoa cấp cứu",
    note: "Trực 7h30 đến 7h30 hôm sau",
    status: "Hợp lệ",
    shiftType: "L01",
    compensationDate: "2026-05-28",
  },
  {
    id: "duty-2",
    date: "2026-05-29",
    staff: "Do Lan Phuong",
    specialty: "Mắt",
    location: "Khoa trực",
    note: "Nghỉ bù dời sang thứ 3 tuần sau",
    status: "Cần kiểm tra",
    shiftType: "L01",
    compensationDate: "2026-06-02",
  },
];

export default function Duty24Page() {
  return (
    <DashboardShell
      activeCode="M02"
      description="Xếp lịch trực cả tháng, tự tính nghỉ bù và kiểm tra xung đột hàng loạt."
      primaryAction="Lưu & công bố"
      secondaryAction="Kiểm tra xung đột"
      title="M02 - Lịch trực 24/24"
    >
      <ScheduleCrudPanel
        defaultRows={dutyRows}
        description="Mỗi dòng là ngày trực; ngày nghỉ bù được tính tự động khi thêm hoặc sửa lịch L01"
        locationLabel="Vị trí trực"
        shiftType="L01"
        submitLabel="Tạo lịch trực"
        title="Bảng trực đã gán"
      />
    </DashboardShell>
  );
}

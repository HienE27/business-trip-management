import { DashboardShell } from "@/components/layout/DashboardShell";
import { StaffCrudPanel } from "@/components/operations/StaffCrudPanel";

export default function StaffPage() {
  return (
    <DashboardShell
      activeCode="M01"
      description="Quản lý 20 nhân sự, phân quyền và trạng thái hoạt động."
      primaryAction="Thêm nhân sự"
      secondaryAction="Nhập danh sách"
      title="M01 - Quản lý nhân sự"
    >
      <StaffCrudPanel />
    </DashboardShell>
  );
}

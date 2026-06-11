import { DashboardShell } from "@/components/layout/DashboardShell";
import { StaffCrudPanel } from "@/components/operations/StaffCrudPanel";

export default function StaffPage() {
  return (
    <DashboardShell
      activeSection="staff"
      description="Quản lý cơ sở dữ liệu nhân viên, chức vụ và trạng thái hoạt động trong hệ thống."
      title="Quản lý nhân sự"
    >
      <StaffCrudPanel />
    </DashboardShell>
  );
}

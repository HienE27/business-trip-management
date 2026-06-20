import { RoleGuard } from "@/components/auth/RoleGuard";
import { StaffCrudPanel } from "@/components/operations/StaffCrudPanel";

export default function StaffPage() {
  return (
    <RoleGuard
      activeSection="staff"
      title="Quản lý nhân sự"
      description="Quản lý cơ sở dữ liệu nhân viên, chức vụ và trạng thái hoạt động trong hệ thống."
      allow={["ADMIN", "MANAGER"]}
    >
      <StaffCrudPanel />
    </RoleGuard>
  );
}

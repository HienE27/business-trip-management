"use client";

import { useRef } from "react";
import { useRouter } from "next/navigation";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { StaffCrudPanel, StaffCrudPanelRef } from "@/components/operations/StaffCrudPanel";

export default function StaffPage() {
  const router = useRouter();
  const crudRef = useRef<StaffCrudPanelRef>(null);

  return (
    <DashboardShell
      activeCode="M01"
      description="Quản lý nhân sự, phân quyền và trạng thái hoạt động thực tế từ Backend."
      primaryAction="Thêm nhân sự"
      onPrimaryAction={() => router.push("/staff/create")}
      secondaryAction="Xuất excel"
      onSecondaryAction={() => crudRef.current?.exportExcel()}
      title="M01 - Quản lý nhân sự"
    >
      <StaffCrudPanel ref={crudRef} isReadOnlyView={true} />
    </DashboardShell>
  );
}

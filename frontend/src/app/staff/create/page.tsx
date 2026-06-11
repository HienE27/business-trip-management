"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { StaffCrudPanel } from "@/components/operations/StaffCrudPanel";

function StaffCreateForm() {
  const searchParams = useSearchParams();
  const idParam = searchParams.get("id");
  const editingId = idParam ? parseInt(idParam, 10) : null;

  return (
    <DashboardShell
      activeSection="staff"
      description={editingId ? "Cập nhật thông tin nhân sự hệ thống." : "Thêm mới tài khoản nhân sự và phân quyền."}
      title={editingId ? "Cập nhật nhân sự" : "Tạo mới nhân sự"}
    >
      <StaffCrudPanel />
    </DashboardShell>
  );
}

export default function StaffCreatePage() {
  return (
    <Suspense fallback={
      <div className="flex h-64 items-center justify-center">
        <svg className="h-8 w-8 animate-spin text-slate-400" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
          <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
        </svg>
      </div>
    }>
      <StaffCreateForm />
    </Suspense>
  );
}

"use client";

import { ToastProvider } from "@/components/ui/ToastProvider";
import { RoleGuard } from "@/components/auth/RoleGuard";
import { PermissionMatrixContent } from "./PermissionMatrixContent";

export default function RolesPage() {
  return (
    <ToastProvider>
      <RoleGuard
        activeSection="settings"
        title="Phân quyền hệ thống"
        description="Ma trận vai trò × quyền hệ thống"
        allow={["ADMIN"]}
      >
        <PermissionMatrixContent />
      </RoleGuard>
    </ToastProvider>
  );
}

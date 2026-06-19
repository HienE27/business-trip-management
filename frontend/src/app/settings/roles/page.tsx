"use client";

import { ToastProvider } from "@/components/ui/ToastProvider";
import { PermissionMatrixContent } from "./PermissionMatrixContent";

export default function RolesPage() {
  return (
    <ToastProvider>
      <PermissionMatrixContent />
    </ToastProvider>
  );
}

"use client";

import { PermissionMatrixContent } from "./PermissionMatrixContent";
import { BackButton } from "@/components/ui/BackButton";

export default function RolesPage() {
  return (
    <div className="flex flex-col gap-4 pb-6">
      <BackButton href="/settings" variant="full" label="Quay lại cài đặt" className="mb-2" />
      <PermissionMatrixContent />
    </div>
  );
}

"use client";

export function AccessDeniedCard() {
  return (
    <div className="rounded-2xl border border-tertiary-container bg-tertiary-container/20 p-8 flex flex-col items-center gap-3 text-center">
      <span className="material-symbols-outlined text-tertiary text-[40px]" aria-hidden="true">lock</span>
      <h2 className="text-title-lg font-semibold text-on-surface">Không có quyền truy cập</h2>
      <p className="text-body-sm text-on-surface-variant max-w-md">
        Chỉ <strong>Quản trị viên</strong> mới có quyền quản lý cấu hình thuật toán.
      </p>
    </div>
  );
}
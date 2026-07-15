"use client";

import { memo } from "react";

export type DemoAccount = {
  id: string;
  label: string;
  username: string;
  password: string;
  /** Mô tả ngắn về quyền hạn — hiện trong chip subtitle. */
  description: string;
  /** Tailwind bg gradient cho avatar circle. */
  avatarClass: string;
  /** Material Symbol cho icon role. */
  icon: string;
  /** Material Symbol cho trend/role chip. */
  roleIcon: string;
  /** Role tag text */
  role: string;
};

export const DEMO_ACCOUNTS: DemoAccount[] = [
  {
    id: "admin",
    label: "Quản trị viên",
    username: "admin",
    password: "admin123",
    description: "Toàn quyền hệ thống",
    role: "ADMIN",
    icon: "admin_panel_settings",
    roleIcon: "shield_person",
    avatarClass:
      "bg-gradient-to-br from-primary to-primary-fixed text-on-primary",
  },
  {
    id: "manager",
    label: "Quản lý lịch",
    username: "manager",
    password: "manager123",
    description: "Xếp & duyệt lịch",
    role: "MANAGER",
    icon: "event_available",
    roleIcon: "supervisor_account",
    avatarClass:
      "bg-gradient-to-br from-emerald-500 to-teal-700 text-white",
  },
  {
    id: "staff",
    label: "Nhân viên",
    username: "teststaff",
    password: "p88LMcrNhc",
    description: "Chỉ xem lịch cá nhân",
    role: "STAFF",
    icon: "person",
    roleIcon: "badge",
    avatarClass:
      "bg-gradient-to-br from-orange-500 to-amber-700 text-white",
  },
];

type DemoAccountsProps = {
  onPick: (account: DemoAccount) => void;
  disabled?: boolean;
};

/**
 * Băng "Đăng nhập nhanh — dùng thử" cho môi trường dev/demo.
 *
 * <p>Liệt kê 3 tài khoản mẫu (admin / manager / staff). Click vào một
 * chip sẽ điền luôn username/password vào form chính và <b>tự động
 * submit</b> để trải nghiệm nhanh.
 *
 * <p>Trong production sẽ bị ẩn qua env flag (TODO: hiện đang hiện cho cả
 * dev lẫn prod vì dự án đang ở giai đoạn pilot/demonstration).
 */
export const DemoAccounts = memo(function DemoAccounts({
  onPick,
  disabled = false,
}: DemoAccountsProps) {
  return (
    <div className="space-y-2.5">
      <div className="flex items-center gap-2">
        <span className="material-symbols-outlined text-[18px] text-primary">
          auto_fix
        </span>
        <span className="text-label-md font-semibold uppercase tracking-wide text-primary">
          Đăng nhập nhanh
        </span>
        <span className="rounded bg-secondary-container px-1.5 py-0.5 text-label-sm font-medium text-secondary-on">
          DEMO
        </span>
      </div>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
        {DEMO_ACCOUNTS.map((acc) => (
          <button
            key={acc.id}
            type="button"
            disabled={disabled}
            onClick={() => onPick(acc)}
            className="group flex items-center gap-2.5 rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-left transition-all hover:-translate-y-0.5 hover:border-primary hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0"
            aria-label={`Đăng nhập nhanh với tài khoản ${acc.label} (${acc.username})`}
          >
            <div
              className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${acc.avatarClass}`}
            >
              <span className="material-symbols-outlined text-[20px]">
                {acc.roleIcon}
              </span>
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1">
                <span className="text-label-lg font-semibold text-on-surface">
                  {acc.label}
                </span>
                <span className="rounded bg-surface-container-high px-1.5 py-0 text-label-sm font-mono font-medium text-on-surface-variant">
                  {acc.role}
                </span>
              </div>
              <p className="truncate text-label-sm text-on-surface-variant">
                {acc.description}
              </p>
            </div>
            <span className="material-symbols-outlined text-[18px] text-outline opacity-0 transition-opacity group-hover:opacity-100">
              arrow_forward
            </span>
          </button>
        ))}
      </div>
    </div>
  );
});

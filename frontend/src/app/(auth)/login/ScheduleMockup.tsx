"use client";

import { memo } from "react";

/**
 * Mock-up "ma trận lịch 5 nhân sự × 7 ngày" hiển thị ở panel phải của trang
 * login. Mục đích: truyền tải trực quan "sản phẩm này là về quản lý lịch trực"
 * ngay từ trang đăng nhập — thay vì pattern lịch nền quá generic.
 *
 * <p>Các ô được tô màu theo ca:
 * <ul>
 *   <li>Xanh dương = ca trực 24/24</li>
 *   <li>Cam = ca thông tầm</li>
 *   <li>Tím = phòng khám dịch vụ</li>
 *   <li>Hồng = phòng khám chuyên gia</li>
 *   <li>Xám nhạt = ngày nghỉ</li>
 * </ul>
 * Component là <i>decorative</i> (aria-hidden=true) và không tương tác.
 */

type CellKind = "duty24" | "allday" | "service" | "expert" | "off";

type Cell = { kind: CellKind; label?: string };

const SHIFTS_BY_DAY: Cell[][] = [
  // CN ... T2 ... T7 → theo thứ tự Mon-Sun cho dễ đọc
  [
    { kind: "duty24", label: "24/24" },
    { kind: "off" },
    { kind: "service", label: "DV" },
    { kind: "off" },
    { kind: "expert", label: "CG" },
    { kind: "allday", label: "TT" },
    { kind: "off" },
  ],
  [
    { kind: "off" },
    { kind: "duty24", label: "24/24" },
    { kind: "off" },
    { kind: "service", label: "DV" },
    { kind: "off" },
    { kind: "expert", label: "CG" },
    { kind: "allday", label: "TT" },
  ],
  [
    { kind: "service", label: "DV" },
    { kind: "off" },
    { kind: "duty24", label: "24/24" },
    { kind: "allday", label: "TT" },
    { kind: "off" },
    { kind: "expert", label: "CG" },
    { kind: "off" },
  ],
  [
    { kind: "off" },
    { kind: "service", label: "DV" },
    { kind: "off" },
    { kind: "duty24", label: "24/24" },
    { kind: "expert", label: "CG" },
    { kind: "off" },
    { kind: "allday", label: "TT" },
  ],
  [
    { kind: "expert", label: "CG" },
    { kind: "off" },
    { kind: "allday", label: "TT" },
    { kind: "off" },
    { kind: "service", label: "DV" },
    { kind: "duty24", label: "24/24" },
    { kind: "off" },
  ],
];

const CELL_BG: Record<CellKind, string> = {
  duty24: "bg-shift-24 text-white shadow-sm shadow-red-500/30",
  allday: "bg-secondary text-white shadow-sm shadow-secondary/30",
  service: "bg-shift-service text-white shadow-sm shadow-tertiary/30",
  expert: "bg-shift-expert text-white shadow-sm shadow-purple-500/30",
  off: "bg-surface-container-highest",
};

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const STAFF = [
  { initials: "NA", name: "Nguyễn Văn A" },
  { initials: "TB", name: "Trần Văn B" },
  { initials: "LC", name: "Lê Văn C" },
  { initials: "PD", name: "Phạm Văn D" },
  { initials: "HH", name: "Hoàng Văn H" },
];

function StaffAvatar({ initials }: { initials: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-800">
        {initials}
      </div>
    </div>
  );
}

export const ScheduleMockup = memo(function ScheduleMockup() {
  return (
    <div className="absolute inset-0 flex flex-col overflow-hidden">
      {/* Background gradient + glow blobs */}
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-primary/95 via-primary/80 to-primary/60" />
      <div className="pointer-events-none absolute -top-20 -right-20 h-72 w-72 rounded-full bg-secondary/40 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-24 -left-16 h-80 w-80 rounded-full bg-blue-100/40 blur-3xl" />
      <div className="pointer-events-none absolute inset-0 opacity-20 [background-image:radial-gradient(circle_at_center,rgba(255,255,255,0.6)_1px,transparent_1px)] [background-size:24px_24px]" />

      {/* Top hero text */}
      <div className="relative z-10 px-10 pt-12">
        <div className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 backdrop-blur-md">
          <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-300" />
          <span className="text-label-sm font-medium text-white">
            Trực 24/7 · Auto-Scheduling
          </span>
        </div>
        <h2 className="mt-4 max-w-md font-display-md text-display-md font-bold leading-tight tracking-tight text-white">
          Xếp lịch thông minh.
          <br />
          Vận hành trơn tru.
        </h2>
        <p className="mt-3 max-w-sm text-body-md text-white/80">
          Thuật toán CSP-MRV-FC phân bổ ca tối ưu, giảm xung đột cho{" "}
          <span className="font-semibold text-white">hơn 200 nhân sự</span>.
        </p>
      </div>

      {/* Mini matrix calendar */}
      <div className="relative z-10 mx-10 mt-8 rounded-2xl border border-white/15 bg-white/95 p-4 shadow-2xl backdrop-blur-md">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-title-md text-title-md font-semibold text-on-surface">
            Tuần 24 – 30/06/2026
          </h3>
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-label-sm font-medium text-emerald-700">
            <span className="material-symbols-outlined text-[14px]">check_circle</span>
            Auto-published
          </span>
        </div>

        {/* Header row */}
        <div className="grid grid-cols-[110px_repeat(7,minmax(0,1fr))] items-center gap-1.5 pb-2">
          <div />
          {DAYS.map((d) => (
            <div
              key={d}
              className="text-center text-label-sm font-semibold text-on-surface-variant"
            >
              {d}
            </div>
          ))}
        </div>

        {/* Body rows */}
        <div className="space-y-1.5">
          {STAFF.map((s, row) => (
            <div
              key={s.initials}
              className="grid grid-cols-[110px_repeat(7,minmax(0,1fr))] items-center gap-1.5"
            >
              <div className="flex items-center gap-2">
                <StaffAvatar initials={s.initials} />
                <span className="truncate text-label-sm font-medium text-on-surface">
                  {s.name}
                </span>
              </div>
              {SHIFTS_BY_DAY[row]?.map((cell, day) => (
                <div
                  key={day}
                  className={`flex h-7 items-center justify-center rounded-md text-label-sm font-bold transition-transform ${CELL_BG[cell.kind]}`}
                >
                  {cell.label}
                </div>
              ))}
            </div>
          ))}
        </div>

        {/* Footer legend */}
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-outline-variant pt-2 text-label-sm text-on-surface-variant">
          <span className="inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-blue-100" /> 24/24
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-secondary" /> Thông tầm
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-purple-500" /> PK DV
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-pink-500" /> PK CG
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-surface-container-highest" />{" "}
            Nghỉ
          </span>
        </div>
      </div>

      {/* Floating KPI cards at bottom */}
      <div className="relative z-10 mt-auto px-10 pb-10">
        <div className="grid grid-cols-3 gap-3">
          <div className="rounded-xl border border-white/15 bg-white/12 p-3 backdrop-blur-md">
            <div className="text-headline-sm font-bold text-white">96.4%</div>
            <div className="text-label-sm text-white/80">Tỉ lệ xếp tự động</div>
          </div>
          <div className="rounded-xl border border-white/15 bg-white/12 p-3 backdrop-blur-md">
            <div className="text-headline-sm font-bold text-white">−42%</div>
            <div className="text-label-sm text-white/80">Xung đột ca</div>
          </div>
          <div className="rounded-xl border border-white/15 bg-white/12 p-3 backdrop-blur-md">
            <div className="text-headline-sm font-bold text-white">3.2s</div>
            <div className="text-label-sm text-white/80">Thời gian xếp</div>
          </div>
        </div>
      </div>
    </div>
  );
});

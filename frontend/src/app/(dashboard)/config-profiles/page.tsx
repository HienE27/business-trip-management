"use client";

import Link from "next/link";
import { BackButton } from "@/components/ui/BackButton";

/**
 * Config Profiles hub page.
 *
 * ROU-001 fix: Previously this route 404'd because the file did not exist.
 * The actual profile CRUD lives under `/auto-scheduling/algorithm-config`
 * (and is owned by the Algorithm Config surface). This hub acts as a
 * navigation entry that surfaces the three related sub-pages so the menu
 * item introduced in PR-11-04 resolves correctly.
 */
export default function ConfigProfilesHubPage() {
  return (
    <div className="space-y-5">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-1" />

      <div>
        <h1 className="text-headline-lg font-bold text-on-surface tracking-tight">Cấu hình</h1>
        <p className="text-label-sm text-on-surface-variant mt-0.5">
          Quản lý thông số runtime, profile và lịch sử thay đổi.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <ConfigLink
          href="/auto-scheduling/algorithm-config"
          icon="tune"
          title="Thông số thuật toán"
          description="RuntimeConfig, AutoGenConfig, custom params. Đây là nơi điều chỉnh các tham số ảnh hưởng trực tiếp đến kết quả xếp lịch."
        />
        <ConfigLink
          href="/auto-scheduling/history"
          icon="history"
          title="Lịch sử xếp lịch"
          description="MetricsHistory và AuditLog của các lần chạy thuật toán trước đó."
        />
        <ConfigLink
          href="/auto-scheduling/algorithm-config"
          icon="compare"
          title="Profile & Compare"
          description="So sánh và áp dụng các bộ tham số. Hiện đang được quản lý chung trong trang Thông số thuật toán."
        />
      </div>

      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant p-5">
        <div className="flex items-start gap-3">
          <span className="material-symbols-outlined text-primary text-[20px] mt-0.5" aria-hidden="true">info</span>
          <div className="space-y-1">
            <p className="text-body-sm font-semibold text-on-surface">Lưu ý</p>
            <p className="text-label-md text-on-surface-variant">
              Các thao tác CRUD đầy đủ cho Config Profile (tạo, sửa, xóa, duplicate, favorite, import/export,
              compare) hiện nằm trong tab <strong>Thông số thuật toán</strong>. Hub này chỉ cung cấp lối tắt điều hướng
              cho menu <strong>Cấu hình</strong> trên sidebar.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function ConfigLink({ href, icon, title, description }: { href: string; icon: string; title: string; description: string }) {
  return (
    <Link
      href={href}
      className="bg-surface-container-lowest rounded-2xl border border-outline-variant p-5 hover:bg-surface-container-low transition-colors group flex flex-col gap-3"
    >
      <div className="flex items-center gap-3">
        <span className="material-symbols-outlined text-primary bg-primary-fixed p-2 rounded-lg text-[22px]" aria-hidden="true">
          {icon}
        </span>
        <h3 className="text-title-md font-semibold text-on-surface">{title}</h3>
        <span className="material-symbols-outlined text-outline ml-auto group-hover:text-primary transition-colors" aria-hidden="true">
          chevron_right
        </span>
      </div>
      <p className="text-body-sm text-on-surface-variant">{description}</p>
    </Link>
  );
}
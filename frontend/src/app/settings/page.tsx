'use client';

import Link from 'next/link';
import { DashboardShell } from '@/components/layout/DashboardShell';

const plannedItems = [
  {
    title: 'Tùy chọn giao diện',
    description: 'Dark mode, mật độ hiển thị và cá nhân hóa dashboard chưa được kết nối với backend hoặc persistent storage.',
    icon: 'palette',
  },
  {
    title: 'Thông báo cá nhân',
    description: 'Hệ thống thông báo chính đã hoạt động, nhưng phần cấu hình nhận thông báo theo kênh vẫn đang được hoàn thiện.',
    icon: 'notifications_active',
  },
  {
    title: 'Thiết lập tài khoản',
    description: 'Các thao tác như đổi mật khẩu, ngôn ngữ và múi giờ sẽ được gom vào luồng hồ sơ cá nhân ở bước sau.',
    icon: 'manage_accounts',
  },
];

export default function SettingsPage() {
  return (
    <DashboardShell
      activeCode="SETTINGS"
      title="Cài đặt"
      description="Trang này đang được tinh gọn để tránh hiển thị các cấu hình chưa hoạt động thực tế."
    >
      <div className="flex flex-col gap-6 pb-8">
        <section className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-6 shadow-sm">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-tertiary-fixed/50 text-tertiary">
              <span className="material-symbols-outlined text-[24px]">construction</span>
            </div>
            <div className="space-y-3">
              <div>
                <h2 className="text-title-lg font-semibold text-on-surface">Khu vực cài đặt đang được hoàn thiện</h2>
                <p className="mt-1 text-body-md leading-relaxed text-on-surface-variant">
                  Hiện tại hệ thống ưu tiên hoàn thiện các luồng nghiệp vụ xếp lịch, kiểm tra xung đột và công bố kỳ lịch.
                  Những cấu hình dưới đây chưa được mở chính thức để tránh gây hiểu nhầm rằng dữ liệu đã được lưu thật.
                </p>
              </div>

              <div className="flex flex-wrap gap-3">
                <Link
                  href="/staff/profile"
                  className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90"
                >
                  <span className="material-symbols-outlined text-[18px]">person</span>
                  Đi tới hồ sơ cá nhân
                </Link>
                <Link
                  href="/notifications"
                  className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low"
                >
                  <span className="material-symbols-outlined text-[18px]">notifications</span>
                  Xem thông báo hệ thống
                </Link>
              </div>
            </div>
          </div>
        </section>

        <section className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          {plannedItems.map((item) => (
            <article
              key={item.title}
              className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm"
            >
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-primary-fixed/40 text-primary">
                <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
              </div>
              <h3 className="text-title-md font-semibold text-on-surface">{item.title}</h3>
              <p className="mt-2 text-body-sm leading-relaxed text-on-surface-variant">{item.description}</p>
            </article>
          ))}
        </section>
      </div>
    </DashboardShell>
  );
}

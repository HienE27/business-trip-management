"use client";

import Link from "next/link";
import { DashboardShell } from "@/components/layout/DashboardShell";

const REPORT_CARDS = [
  {
    title: "Báo cáo kỳ lịch",
    description: "Tổng hợp phân bổ lịch, trạng thái và xung đột của kỳ lịch được chọn.",
    icon: "calendar_month",
    href: "/reports/monthly",
    cta: "Xem báo cáo kỳ lịch",
    accent: "border-l-primary",
  },
  {
    title: "Khối lượng nhân sự",
    description: "Xem nhanh tải phân công theo nhân sự, so sánh với giới hạn ca/tháng.",
    icon: "groups",
    href: "/reports/staff",
    cta: "Xem tải nhân sự",
    accent: "border-l-secondary",
  },
  {
    title: "Báo cáo xung đột",
    description: "Phân tích xung đột lịch trực theo kỳ, nguyên nhân và mức độ ảnh hưởng.",
    icon: "warning",
    href: "/reports/conflicts",
    cta: "Kiểm tra xung đột",
    accent: "border-l-error",
  },
];

export default function ReportsPage() {
  return (
    <DashboardShell
      activeSection="reports"
      title="Báo cáo"
      description="Điểm vào trung tâm báo cáo vận hành: kỳ lịch, khối lượng nhân sự và xung đột."
    >
      <div className="space-y-6">
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 md:p-6 shadow-sm">
          <p className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-variant">Reports hub</p>
          <h2 className="mt-2 text-headline-md text-on-surface">Trung tâm báo cáo</h2>
          <p className="mt-2 max-w-3xl text-body-md leading-6 text-on-surface-variant">
            Chọn loại báo cáo để xem chi tiết. Các báo cáo sử dụng dữ liệu thực từ backend.
          </p>
        </section>

        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {REPORT_CARDS.map((card) => (
            <article
              key={card.title}
              className={`flex h-full flex-col rounded-xl border border-l-4 ${card.accent} bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low`}
            >
              <span className="material-symbols-outlined w-fit rounded-lg bg-primary-fixed px-2 py-2 text-[22px] text-primary">
                {card.icon}
              </span>
              <h3 className="mt-4 text-title-lg font-semibold text-on-surface">{card.title}</h3>
              <p className="mt-2 flex-1 text-body-sm leading-6 text-on-surface-variant">{card.description}</p>
              <Link
                href={card.href}
                className="mt-5 inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90"
              >
                {card.cta}
                <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
              </Link>
            </article>
          ))}
        </section>
      </div>
    </DashboardShell>
  );
}

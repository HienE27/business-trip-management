"use client";

import Link from "next/link";

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
    <div className="space-y-4">
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-3 shadow-sm">
        <p className="text-label-sm font-medium text-on-surface-variant">Trung tâm báo cáo</p>
        <h2 className="mt-1 text-headline-md text-on-surface">Báo cáo vận hành</h2>
        <p className="mt-1 text-label-sm leading-5 text-on-surface-variant max-w-3xl">
          Chọn loại báo cáo để xem chi tiết. Các báo cáo sử dụng dữ liệu thực từ backend.
        </p>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {REPORT_CARDS.map((card) => (
          <article
            key={card.title}
            className={`flex h-full flex-col rounded-xl border border-l-4 ${card.accent} bg-surface-container-lowest p-4 shadow-sm transition-colors hover:bg-surface-container-low`}
          >
            <span className="material-symbols-outlined w-fit rounded-lg bg-primary-fixed px-1.5 py-1.5 text-[18px] text-primary">
              {card.icon}
            </span>
            <h3 className="mt-3 text-title-lg font-semibold text-on-surface leading-tight">{card.title}</h3>
            <p className="mt-1 flex-1 text-label-sm leading-5 text-on-surface-variant">{card.description}</p>
            <Link
              href={card.href}
              className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-label-sm font-medium text-on-primary transition-colors hover:bg-primary/90"
            >
              {card.cta}
              <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
            </Link>
          </article>
        ))}
      </section>
    </div>
  );
}

"use client";

import Link from "next/link";
import { BackButton } from "@/components/ui/BackButton";

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
    title: "Thống kê nhân sự",
    description: "Phân bổ ca trực theo nhân sự, số ca mỗi loại (L01-L04), tổng giờ và tỷ lệ tải công việc.",
    icon: "assessment",
    href: "/reports/statistics",
    cta: "Xem thống kê",
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
    <div className="space-y-6">
      <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />
      {/* Page Header */}
      <section className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <p className="text-label-sm text-on-surface-variant flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">dashboard</span>
            Trung tâm báo cáo
          </p>
          <h1 className="mt-1 text-headline-lg font-semibold text-on-surface">Báo cáo vận hành</h1>
          <p className="mt-1 text-body-sm text-on-surface-variant max-w-2xl">
            Chọn loại báo cáo để xem chi tiết. Các báo cáo sử dụng dữ liệu thực từ backend.
          </p>
        </div>
      </section>

      {/* Report Cards */}
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {REPORT_CARDS.map((card) => (
          <article
            key={card.title}
            className={`group relative flex flex-col rounded-xl border border-l-4 ${card.accent} bg-surface-container-lowest p-5 shadow-sm transition-all duration-200 hover:bg-surface-container-low hover:shadow-md hover:-translate-y-0.5`}
          >
            {/* Icon */}
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary-fixed text-primary shadow-sm transition-transform duration-200 group-hover:scale-105">
              <span className="material-symbols-outlined text-[22px]" aria-hidden="true">
                {card.icon}
              </span>
            </div>

            {/* Content */}
            <div className="mt-4 flex flex-1 flex-col">
              <h3 className="text-title-lg font-semibold text-on-surface leading-tight">
                {card.title}
              </h3>
              <p className="mt-2 flex-1 text-label-sm leading-5 text-on-surface-variant">
                {card.description}
              </p>
            </div>

            {/* CTA Button */}
            <Link
              href={card.href}
              className="mt-4 inline-flex w-fit items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary shadow-sm transition-all duration-200 hover:bg-primary/90 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            >
              {card.cta}
              <span className="material-symbols-outlined text-[16px] transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden="true">
                arrow_forward
              </span>
            </Link>
          </article>
        ))}
      </section>
    </div>
  );
}

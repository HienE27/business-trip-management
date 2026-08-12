"use client";

import { memo, useId } from "react";

type CoverageInspectorProps = {
  coverageGaps: string[];
  hasCoverageGaps: boolean;
  totalCoverageGaps: number;
  totalDaysInPeriod?: number;
};

// Material Symbols uses ligature: rendering depends on the font.
// Wrap icon in a fixed-width box so the text content never escapes.
const Icon = memo(function Icon({ name, className = "" }: { name: string; className?: string }) {
  return (
    <span
      className={`relative inline-flex h-5 w-5 shrink-0 items-center justify-center overflow-hidden text-[18px] leading-none ${className}`}
      aria-hidden="true"
    >
      <i className="material-symbols-outlined absolute inset-0 m-auto text-[18px] leading-none">
        {name}
      </i>
    </span>
  );
});

export const CoverageInspector = memo(function CoverageInspector({
  coverageGaps,
  hasCoverageGaps,
  totalCoverageGaps,
  totalDaysInPeriod,
}: CoverageInspectorProps) {
  const labelId = useId();
  const totalDays = totalDaysInPeriod ?? 0;
  const isFullCoverage = totalDaysInPeriod !== undefined && totalCoverageGaps >= totalDaysInPeriod;
  const hasScheduledDays = hasCoverageGaps;
  const remaining = totalDays - totalCoverageGaps;
  const badgeLabel = `${totalCoverageGaps}/${totalDays}`;
  return (
    <section
      className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm"
      aria-labelledby={labelId}
    >
      <header className="flex items-center gap-2.5 border-b border-outline-variant bg-surface-container-low px-4 py-3">
        <Icon name="event_available" className="text-primary" />
        <div className="min-w-0 flex-1">
          <h3 id={labelId} className="text-[15px] font-semibold leading-tight text-on-surface">
            Ngày có lịch trực
          </h3>
          <p className="mt-0.5 text-[11px] leading-snug text-on-surface-variant">
            Danh sách ngày đã xếp lịch trong kỳ.
          </p>
        </div>
        {hasScheduledDays && !isFullCoverage && (
          <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-primary text-on-primary text-[11px] font-bold leading-none">
            {badgeLabel}
          </span>
        )}
        {isFullCoverage && (
          <span className="material-symbols-outlined text-[18px] text-emerald-600" aria-hidden="true">check_circle</span>
        )}
      </header>

      <div className="divide-y divide-outline-variant/50">
        {!hasScheduledDays ? (
          <div className="flex items-center gap-2 px-4 py-5 text-[13px] leading-tight text-on-surface-variant">
            <Icon name="event_note" className="text-on-surface-variant" />
            Chưa có lịch trực cho loại ca đang chọn.
          </div>
        ) : isFullCoverage ? (
          <div className="flex items-center gap-2 px-4 py-5 text-[13px] leading-tight text-emerald-700">
            <Icon name="check_circle" className="text-emerald-600" />
            Đã phủ đủ {badgeLabel} ngày trong kỳ.
          </div>
        ) : (
          coverageGaps.slice(0, 5).map((gap, index) => (
            <div key={index} className="flex items-start gap-2.5 px-4 py-3">
              <Icon name="event_available" className="mt-0.5 text-primary" />
              <p className="min-w-0 flex-1 text-[12px] leading-5 text-on-surface">
                {gap}
              </p>
            </div>
          ))
        )}
      </div>
    </section>
  );
});

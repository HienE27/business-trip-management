"use client";

import { memo, useId } from "react";

type CoverageInspectorProps = {
  coverageGaps: string[];
  hasCoverageGaps: boolean;
  totalCoverageGaps: number;
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
}: CoverageInspectorProps) {
  const labelId = useId();
  return (
    <section
      className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm"
      aria-labelledby={labelId}
    >
      <header className="flex items-center gap-2.5 border-b border-outline-variant bg-tertiary-fixed px-4 py-3">
        <Icon name="event_busy" className="text-tertiary" />
        <div className="min-w-0 flex-1">
          <h3 id={labelId} className="text-[15px] font-semibold leading-tight text-on-tertiary-fixed">
            Khoảng trống phủ
          </h3>
          <p className="mt-0.5 text-[11px] leading-snug text-on-tertiary-fixed/80">
            Thiếu nhân sự so với yêu cầu ca trực trong kỳ lịch.
          </p>
        </div>
        {hasCoverageGaps && (
          <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-tertiary px-2 text-[11px] font-bold leading-none text-on-tertiary">
            {totalCoverageGaps}
          </span>
        )}
      </header>

      <div className="divide-y divide-outline-variant/50">
        {!hasCoverageGaps ? (
          <div className="flex items-center gap-2 px-4 py-5 text-[13px] leading-tight text-secondary">
            <Icon name="check_circle" className="text-secondary" />
            Đã đủ nhân sự cho mọi ca trực.
          </div>
        ) : (
          coverageGaps.slice(0, 5).map((gap, index) => (
            <div key={index} className="flex items-start gap-2.5 px-4 py-3">
              <Icon name="help" className="mt-0.5 text-tertiary" />
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

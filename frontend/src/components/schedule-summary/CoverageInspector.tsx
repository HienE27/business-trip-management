"use client";

type CoverageInspectorProps = {
  coverageGaps: string[];
  hasCoverageGaps: boolean;
  totalCoverageGaps: number;
};

export function CoverageInspector({
  coverageGaps,
  hasCoverageGaps,
  totalCoverageGaps,
}: CoverageInspectorProps) {
  return (
    <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)]">
      <div className="border-b border-outline-variant bg-tertiary-fixed/40 px-4 py-4">
        <h3 className="flex items-center gap-2 text-title-lg font-semibold text-on-surface">
          <span className="material-symbols-outlined text-tertiary" style={{ fontVariationSettings: "'FILL' 1" }}>empty_half_bottom_stalk</span>
          Coverage gaps
          {hasCoverageGaps && (
            <span className="ml-auto rounded-full bg-tertiary px-2 py-0.5 text-[11px] font-bold text-on-tertiary">
              {totalCoverageGaps}
            </span>
          )}
        </h3>
        <p className="mt-1 text-label-sm text-on-surface-variant">
          Thiếu nhân sự so với yêu cầu ca trực trong kỳ lịch.
        </p>
      </div>

      <div className="divide-y divide-outline-variant/50">
        {!hasCoverageGaps ? (
          <div className="flex items-center gap-2 px-5 py-6 text-label-md text-secondary">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
            Đã đủ nhân sự cho mọi ca trực.
          </div>
        ) : (
          coverageGaps.map((gap, index) => (
            <div
              key={index}
              className="flex items-start gap-3 px-5 py-4"
            >
              <span className="material-symbols-outlined mt-0.5 text-[18px] text-tertiary">help</span>
              <p className="min-w-0 flex-1 text-label-sm leading-5 text-on-surface">
                {gap}
              </p>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

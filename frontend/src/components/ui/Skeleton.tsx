"use client";

type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className = "" }: SkeletonProps) {
  return (
    <div
      className={`animate-pulse bg-surface-container rounded ${className}`}
      aria-hidden="true"
    />
  );
}

export function SkeletonCard() {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-lg p-3 space-y-2">
      <div className="flex items-center gap-3">
        <Skeleton className="size-8 rounded-full" />
        <div className="flex-1 space-y-1.5">
          <Skeleton className="h-2.5 w-3/4 rounded" />
          <Skeleton className="h-2 w-1/2 rounded" />
        </div>
      </div>
      <Skeleton className="h-2.5 w-full rounded" />
      <Skeleton className="h-2.5 w-5/6 rounded" />
    </div>
  );
}

export function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-lg overflow-hidden" aria-busy="true">
      <table className="w-full">
        <thead>
          <tr className="border-b border-outline-variant">
            {Array.from({ length: cols }).map((_, i) => (
              <th key={i} className="p-3">
                <Skeleton className="h-3 w-full rounded" />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }).map((_, rowIdx) => (
            <tr key={rowIdx} className="border-b border-outline-variant/50 last:border-0">
              {Array.from({ length: cols }).map((_, colIdx) => (
                <td key={colIdx} className="p-3">
                  <Skeleton className="h-3 w-full rounded" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function SkeletonCalendar() {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-lg overflow-hidden">
      {/* Header */}
      <div className="p-3 border-b border-outline-variant flex items-center justify-between">
        <Skeleton className="h-5 w-32 rounded" />
        <div className="flex gap-2">
          <Skeleton className="h-7 w-16 rounded-lg" />
          <Skeleton className="h-7 w-16 rounded-lg" />
          <Skeleton className="h-7 w-16 rounded-lg" />
        </div>
      </div>
      {/* Day headers */}
      <div className="grid grid-cols-7 border-b border-outline-variant">
        {["CN", "T2", "T3", "T4", "T5", "T6", "T7"].map((d) => (
          <div key={d} className="p-1.5 text-center">
            <Skeleton className="h-3 w-5 mx-auto rounded" />
          </div>
        ))}
      </div>
      {/* Cells */}
      <div className="grid grid-cols-7">
        {Array.from({ length: 42 }).map((_, i) => (
          <div key={i} className="min-h-[80px] border-r border-b border-outline-variant p-1">
            <Skeleton className="h-3 w-4 rounded mb-1.5" />
            <Skeleton className="h-6 w-full rounded mb-0.5" />
            <Skeleton className="h-6 w-3/4 rounded" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function SkeletonKPI() {
  return (
    <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <div
          key={i}
          className="bg-surface-container-lowest border border-outline-variant rounded-lg p-4 shadow-sm"
        >
          <Skeleton className="h-3 w-20 rounded mb-2" />
          <Skeleton className="h-6 w-12 rounded mb-1" />
          <Skeleton className="h-2 w-16 rounded" />
        </div>
      ))}
    </div>
  );
}

export function SkeletonDashboardKPIGrid() {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: 8 }).map((_, i) => (
        <div
          key={i}
          className="bg-surface-container-lowest border border-outline-variant rounded-lg p-5 shadow-sm"
        >
          <Skeleton className="h-3 w-28 rounded mb-3" />
          <Skeleton className="h-8 w-16 rounded mb-2" />
          <Skeleton className="h-2 w-20 rounded" />
        </div>
      ))}
    </div>
  );
}

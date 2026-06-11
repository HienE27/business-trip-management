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
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4 space-y-3">
      <div className="flex items-center gap-3">
        <Skeleton className="size-10 rounded-full" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-3 w-3/4 rounded" />
          <Skeleton className="h-2 w-1/2 rounded" />
        </div>
      </div>
      <Skeleton className="h-3 w-full rounded" />
      <Skeleton className="h-3 w-5/6 rounded" />
    </div>
  );
}

export function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden" aria-busy="true">
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
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
      {/* Header */}
      <div className="p-4 border-b border-outline-variant flex items-center justify-between">
        <Skeleton className="h-6 w-40 rounded" />
        <div className="flex gap-2">
          <Skeleton className="h-8 w-20 rounded-lg" />
          <Skeleton className="h-8 w-20 rounded-lg" />
          <Skeleton className="h-8 w-20 rounded-lg" />
        </div>
      </div>
      {/* Day headers */}
      <div className="grid grid-cols-7 border-b border-outline-variant">
        {["CN", "T2", "T3", "T4", "T5", "T6", "T7"].map((d) => (
          <div key={d} className="p-2 text-center">
            <Skeleton className="h-3 w-6 mx-auto rounded" />
          </div>
        ))}
      </div>
      {/* Cells */}
      <div className="grid grid-cols-7">
        {Array.from({ length: 42 }).map((_, i) => (
          <div key={i} className="min-h-[100px] border-r border-b border-outline-variant p-1.5">
            <Skeleton className="h-4 w-6 rounded mb-2" />
            <Skeleton className="h-8 w-full rounded mb-1" />
            <Skeleton className="h-8 w-3/4 rounded" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function SkeletonKPI() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <div
          key={i}
          className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 shadow-sm"
        >
          <Skeleton className="h-3 w-24 rounded mb-3" />
          <Skeleton className="h-8 w-16 rounded mb-2" />
          <Skeleton className="h-3 w-20 rounded" />
        </div>
      ))}
    </div>
  );
}

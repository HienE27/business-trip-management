import type { ConflictItem } from "@/types/schedule";

type ConflictPanelProps = {
  conflicts: ConflictItem[];
  maxItems?: number;
  className?: string;
  onResolve?: (conflict: ConflictItem) => void;
};

export function ConflictPanel({ conflicts, maxItems = 5, className = "", onResolve }: ConflictPanelProps) {
  const displayConflicts = conflicts.slice(0, maxItems);

  return (
    <section className={`bg-surface-container-lowest border-error-container rounded-lg shadow-sm overflow-hidden ${className}`}>
      {/* Header */}
      <div className="p-4 border-b border-error-container bg-error/5 flex items-center gap-2">
          <span aria-hidden="true" className="material-symbols-outlined text-[20px] text-error" style={{ fontVariationSettings: "'FILL' 1" }}>
          warning
        </span>
        <h3 className="text-title-lg text-error">
          Cảnh báo xung đột ({conflicts.length})
        </h3>
      </div>

      {/* List */}
      <div className="p-2 flex flex-col gap-2">
        {displayConflicts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 gap-3">
            <span aria-hidden="true" className="material-symbols-outlined text-[40px] text-secondary/30">
              check_circle
            </span>
            <p className="text-label-md text-on-surface-variant">Không có xung đột</p>
          </div>
        ) : (
          displayConflicts.map((conflict) => (
            <div
              className="p-3 bg-surface border border-outline-variant rounded-lg hover:bg-surface-container-high transition-colors cursor-pointer"
              key={conflict.id}
              onClick={() => onResolve?.(conflict)}
            >
              <p className="text-label-md text-on-surface font-medium">
                {conflict.staffName}
              </p>
              <p className="text-body-sm text-on-surface-variant mt-1 leading-relaxed line-clamp-2">
                {conflict.detail}
              </p>
              <button
                className="mt-2 text-error text-label-sm font-medium hover:underline"
                onClick={(e) => { e.stopPropagation(); onResolve?.(conflict); }}
              >
                Xử lý ngay
              </button>
            </div>
          ))
        )}

        {conflicts.length > maxItems && (
          <button type="button" className="mt-2 text-center text-label-sm text-primary hover:underline font-medium">
            Xem tất cả ({conflicts.length})
          </button>
        )}
      </div>
    </section>
  );
}

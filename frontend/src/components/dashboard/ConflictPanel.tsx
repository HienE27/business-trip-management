import type { ConflictItem } from "@/types/schedule";

type ConflictPanelProps = {
  conflicts: ConflictItem[];
  maxItems?: number;
  className?: string;
};

export function ConflictPanel({ conflicts, maxItems = 5, className = "" }: ConflictPanelProps) {
  const displayConflicts = conflicts.slice(0, maxItems);

  return (
    <section className={`flex flex-col rounded-xl border border-error-container bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex items-center gap-2 p-4 border-b border-error-container bg-error/5">
        <span aria-hidden="true" className="material-symbols-outlined fill text-error text-[20px]">
          warning
        </span>
        <h3 className="font-title-lg text-error">
          Canh bao xung dot ({conflicts.length})
        </h3>
      </div>

      {/* List */}
      <div className="p-2 flex flex-col gap-2">
        {displayConflicts.map((conflict) => (
          <article
            className="p-3 bg-surface border border-outline-variant rounded-lg hover:bg-surface-container-high transition-colors cursor-pointer"
            key={conflict.id}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1 min-w-0">
                <p className="font-label-md text-on-surface truncate">
                  {conflict.staffName}
                </p>
                <p className="mt-1 font-body-sm text-on-surface-variant leading-relaxed line-clamp-2">
                  {conflict.detail}
                </p>
              </div>
              <button className="shrink-0 text-error text-[14px] font-medium hover:underline">
                Xu ly ngay
              </button>
            </div>
          </article>
        ))}

        {conflicts.length > maxItems && (
          <button className="mt-2 text-center text-[14px] text-primary hover:underline font-medium">
            Xem tat ca ({conflicts.length})
          </button>
        )}
      </div>
    </section>
  );
}

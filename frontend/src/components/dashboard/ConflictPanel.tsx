import { memo, useState, useMemo } from "react";
import type { ConflictItem } from "@/types/schedule";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  getConflictType,
  getConflictSeverityBadgeTone,
  sortConflictsBySeverity,
} from "@/lib/conflict-utils";

type ConflictPanelProps = {
  conflicts: ConflictItem[];
  maxItems?: number;
  className?: string;
  onResolve?: (conflict: ConflictItem) => void;
  onRemove?: (conflict: ConflictItem) => void;
  onSwap?: (conflict: ConflictItem) => void;
};

/**
 * Panel hiển thị danh sách xung đột lịch trực.
 * Tự động sort theo severity (Chặn lưu → Cảnh báo).
 * Supports expand/collapse khi số lượng > maxItems.
 */
export const ConflictPanel = memo(function ConflictPanel({ 
  conflicts, 
  maxItems = 5, 
  className = "", 
  onResolve,
  onRemove,
  onSwap 
}: ConflictPanelProps) {
  const [expanded, setExpanded] = useState(false);
  
  const sortedConflicts = useMemo(
    () => sortConflictsBySeverity(conflicts),
    [conflicts]
  );
  
  const displayConflicts = expanded ? sortedConflicts : sortedConflicts.slice(0, maxItems);

  return (
    <section 
      aria-labelledby="conflict-panel-title"
      className={`bg-surface-container-lowest border-red-300 rounded-lg shadow-sm overflow-hidden ${className}`}
    >
      {/* Header */}
      <div className="p-4 border-b border-red-300 bg-red-100 text-red-800 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className="material-symbols-outlined text-[20px] text-red-800" style={{ fontVariationSettings: "'FILL' 1" }}>
            warning
          </span>
          <h3 id="conflict-panel-title" className="text-title-lg text-red-800">
            Cảnh báo xung đột ({conflicts.length})
          </h3>
        </div>
        <Badge tone={conflicts.length === 0 ? "success" : "error"} size="sm" aria-live="polite">
          {conflicts.length === 0 ? "OK" : conflicts.length}
        </Badge>
      </div>

      {/* List */}
      <div className="p-2 flex flex-col gap-2" role="list" aria-label="Danh sách xung đột">
        {displayConflicts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 gap-3">
            <span aria-hidden="true" className="material-symbols-outlined text-[40px] text-emerald-800/30">
              check_circle
            </span>
            <p className="text-label-md text-on-surface-variant">Không có xung đột</p>
          </div>
        ) : (
          displayConflicts.map((conflict) => {
            const conflictType = getConflictType(conflict.detail);
            return (
              <div
                className="p-3 bg-surface border border-outline-variant rounded-lg hover:bg-surface-container-high transition-colors"
                key={conflict.id}
                role="listitem"
              >
                {/* Header row */}
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span 
                      aria-hidden="true" 
                      className={`material-symbols-outlined text-[16px] ${conflictType.color}`}
                    >
                      {conflictType.icon}
                    </span>
                    <p className="text-label-md text-on-surface font-medium truncate">
                      {conflict.staffName || "Nhân sự"}
                    </p>
                  </div>
                  <Badge 
                    tone={getConflictSeverityBadgeTone(conflict.severity)} 
                    size="sm"
                    className="shrink-0"
                  >
                    {conflict.severity}
                  </Badge>
                </div>

                {/* Conflict type */}
                <p className="text-label-xs text-on-surface-variant mt-1">
                  Loại: <span className={conflictType.color}>{conflictType.type}</span>
                </p>

                {/* Conflict detail */}
                {conflict.detail && (
                  <p className="text-body-sm text-on-surface-variant mt-2 leading-relaxed line-clamp-2">
                    {conflict.detail}
                  </p>
                )}

                {/* Date info */}
                {conflict.workDate && (
                  <p className="text-label-sm text-on-surface-variant mt-1 flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]" aria-hidden="true">event</span>
                    {conflict.workDate}
                  </p>
                )}

                {/* Action buttons */}
                {conflict.severity === "Chặn lưu" && (
                  <div className="flex items-center gap-2 mt-3 pt-2 border-t border-outline-variant" role="group" aria-label="Hành động xử lý">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onRemove?.(conflict)}
                      className="text-red-800 hover:text-red-800/80"
                      aria-label={`Xóa lịch của ${conflict.staffName}`}
                    >
                      <span className="material-symbols-outlined text-[14px] mr-1" aria-hidden="true">delete</span>
                      Xóa lịch
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onSwap?.(conflict)}
                      aria-label={`Đổi ca cho ${conflict.staffName}`}
                    >
                      <span className="material-symbols-outlined text-[14px] mr-1" aria-hidden="true">swap_horiz</span>
                      Đổi ca
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onResolve?.(conflict)}
                      className="text-blue-800"
                      aria-label={`Xử lý xung đột của ${conflict.staffName}`}
                    >
                      <span className="material-symbols-outlined text-[14px] mr-1" aria-hidden="true">check</span>
                      Xử lý
                    </Button>
                  </div>
                )}
              </div>
            );
          })
        )}

        {conflicts.length > maxItems && (
          <button 
            type="button" 
            className="mt-2 text-center text-label-sm text-blue-800 hover:underline font-medium"
            onClick={() => setExpanded(!expanded)}
            aria-expanded={expanded}
          >
            {expanded ? "Thu gọn" : `Xem tất cả (${conflicts.length})`}
          </button>
        )}
      </div>
    </section>
  );
}, (prevProps, nextProps) => {
  // Custom comparison: only re-render if conflicts actually changed
  if (prevProps.conflicts !== nextProps.conflicts) return false;
  if (prevProps.maxItems !== nextProps.maxItems) return false;
  if (prevProps.className !== nextProps.className) return false;
  if (prevProps.onResolve !== nextProps.onResolve) return false;
  if (prevProps.onRemove !== nextProps.onRemove) return false;
  if (prevProps.onSwap !== nextProps.onSwap) return false;
  return true;
});

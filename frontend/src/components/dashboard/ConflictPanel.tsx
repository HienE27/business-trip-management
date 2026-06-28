import { useState } from "react";
import type { ConflictItem } from "@/types/schedule";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

type ConflictPanelProps = {
  conflicts: ConflictItem[];
  maxItems?: number;
  className?: string;
  onResolve?: (conflict: ConflictItem) => void;
  onRemove?: (conflict: ConflictItem) => void;
  onSwap?: (conflict: ConflictItem) => void;
};

export function ConflictPanel({ 
  conflicts, 
  maxItems = 5, 
  className = "", 
  onResolve,
  onRemove,
  onSwap 
}: ConflictPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const displayConflicts = expanded ? conflicts : conflicts.slice(0, maxItems);

  // Get conflict type from detail message
  const getConflictType = (detail: string | undefined): { type: string; color: string; icon: string } => {
    if (!detail) return { type: "Khác", color: "text-gray-600", icon: "warning" };
    if (detail.includes("trực 24/24") || detail.includes("L01")) {
      return { type: "Lịch trực", color: "text-red-600", icon: "emergency" };
    }
    if (detail.includes("nghỉ phép") || detail.includes("Leave")) {
      return { type: "Nghỉ phép", color: "text-amber-600", icon: "event_busy" };
    }
    if (detail.includes("nghỉ bù") || detail.includes("compensation")) {
      return { type: "Ngày nghỉ bù", color: "text-orange-600", icon: "calendar_month" };
    }
    if (detail.includes("liền kề") || detail.includes("back-to-back")) {
      return { type: "Ca liền kề", color: "text-purple-600", icon: "schedule" };
    }
    return { type: "Khác", color: "text-gray-600", icon: "warning" };
  };

  return (
    <section className={`bg-surface-container-lowest border-error-container rounded-lg shadow-sm overflow-hidden ${className}`}>
      {/* Header */}
      <div className="p-4 border-b border-error-container bg-error/5 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className="material-symbols-outlined text-[20px] text-error" style={{ fontVariationSettings: "'FILL' 1" }}>
            warning
          </span>
          <h3 className="text-title-lg text-error">
            Cảnh báo xung đột ({conflicts.length})
          </h3>
        </div>
        <Badge tone={conflicts.length === 0 ? "success" : "error"} size="sm">
          {conflicts.length === 0 ? "OK" : conflicts.length}
        </Badge>
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
          displayConflicts.map((conflict) => {
            const conflictType = getConflictType(conflict.detail);
            return (
              <div
                className="p-3 bg-surface border border-outline-variant rounded-lg hover:bg-surface-container-high transition-colors"
                key={conflict.id}
              >
                {/* Header row */}
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className={`material-symbols-outlined text-[16px] ${conflictType.color}`}>
                      {conflictType.icon}
                    </span>
                    <p className="text-label-md text-on-surface font-medium truncate">
                      {conflict.staffName || "Nhân sự"}
                    </p>
                  </div>
                  <Badge 
                    tone={conflict.severity === "Chặn lưu" ? "error" : "warning"} 
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
                    <span className="material-symbols-outlined text-[14px]">event</span>
                    {conflict.workDate}
                  </p>
                )}

                {/* Action buttons */}
                {conflict.severity === "Chặn lưu" && (
                  <div className="flex items-center gap-2 mt-3 pt-2 border-t border-outline-variant">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onRemove?.(conflict)}
                      className="text-error hover:text-error/80"
                    >
                      <span className="material-symbols-outlined text-[14px] mr-1">delete</span>
                      Xóa lịch
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onSwap?.(conflict)}
                    >
                      <span className="material-symbols-outlined text-[14px] mr-1">swap_horiz</span>
                      Đổi ca
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onResolve?.(conflict)}
                      className="text-primary"
                    >
                      <span className="material-symbols-outlined text-[14px] mr-1">check</span>
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
            className="mt-2 text-center text-label-sm text-primary hover:underline font-medium"
            onClick={() => setExpanded(!expanded)}
          >
            {expanded ? "Thu gọn" : `Xem tất cả (${conflicts.length})`}
          </button>
        )}
      </div>
    </section>
  );
}

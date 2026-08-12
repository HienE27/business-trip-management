"use client";

import { memo, useCallback, useMemo, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";

export interface BulkDatePickerModalProps {
  open: boolean;
  onClose: () => void;
  onDatesSelected: (dates: string[]) => void;
  periodId: number;
  periodStart: string;
  periodEnd: string;
}

const DAY_LABELS: Record<number, string> = {
  0: "CN",
  1: "T2",
  2: "T3",
  3: "T4",
  4: "T5",
  5: "T6",
  6: "T7",
};

// Format a Date as YYYY-MM-DD using LOCAL time (matches the user's timezone).
// toISOString() returns UTC which can shift by 1 day for users in non-UTC zones.
function toIso(date: Date): string {
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}


export const BulkDatePickerModal = memo(function BulkDatePickerModal({
  open,
  onClose,
  onDatesSelected,
  periodStart,
  periodEnd,
}: BulkDatePickerModalProps) {
  // Parse YYYY-MM-DD as local calendar dates to avoid UTC timezone shifts
  // (e.g. "2026-07-31T00:00:00" parsed as UTC becomes Jul 30 in ICT).
  const startDate = useMemo(() => {
    const [y, m, d] = periodStart.split("-").map(Number);
    return new Date(y!, m! - 1, d!);
  }, [periodStart]);

  const endDate = useMemo(() => {
    const [y, m, d] = periodEnd.split("-").map(Number);
    return new Date(y!, m! - 1, d!);
  }, [periodEnd]);

  const today = useMemo(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }, []);

  const allDays = useMemo(() => {
    const days: Date[] = [];
    const cur = new Date(startDate);
    while (cur <= endDate) {
      days.push(new Date(cur));
      cur.setDate(cur.getDate() + 1);
    }
    return days;
  }, [startDate, endDate]);

  const futureDays = useMemo(
    () => allDays.filter((d) => d >= today),
    [allDays, today]
  );

  const [selected, setSelected] = useState<Set<string>>(new Set());

  const toggle = useCallback((iso: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(iso)) {
        next.delete(iso);
      } else {
        next.add(iso);
      }
      return next;
    });
  }, []);

  const handleSelectWeekdays = useCallback(() => {
    setSelected((prev) => {
      const next = new Set(prev);
      for (const d of futureDays) {
        const dow = d.getDay();
        if (dow >= 1 && dow <= 5) next.add(toIso(d));
      }
      return next;
    });
  }, [futureDays]);

  const handleClear = useCallback(() => {
    setSelected(new Set());
  }, []);

  const handleSubmit = () => {
    onDatesSelected(Array.from(selected).sort());
    setSelected(new Set());
    onClose();
  };

  const handleClose = () => {
    setSelected(new Set());
    onClose();
  };

  const weekDays = futureDays.filter((d) => {
    const dow = d.getDay();
    return dow >= 1 && dow <= 5;
  });
  const weekendDays = futureDays.filter((d) => {
    const dow = d.getDay();
    return dow === 0 || dow === 6;
  });

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Chọn ngày gán lịch"
      description={`Chọn một hoặc nhiều ngày trong kỳ lịch. Đã chọn ${selected.size} ngày.`}
      size="lg"
    >
      <div className="space-y-5">
        {/* Quick actions */}
        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={handleSelectWeekdays}
            icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">work_history</span>}
          >
            Chọn ngày trong tuần
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleClear}
            icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">deselect</span>}
          >
            Bỏ chọn tất cả
          </Button>
          <span className="ml-auto text-label-sm text-on-surface-variant">
            {selected.size}/{futureDays.length} ngày
          </span>
        </div>

        {/* Weekdays grid */}
        {weekDays.length > 0 && (
          <div>
            <p className="text-label-sm text-on-surface-variant mb-2 font-semibold">
              Ngày trong tuần
            </p>
            <div className="grid grid-cols-5 gap-2">
              {weekDays.map((d) => {
                const iso = toIso(d);
                const is = selected.has(iso);
                return (
                  <button
                    key={iso}
                    type="button"
                    aria-pressed={is}
                    onClick={() => toggle(iso)}
                    className={[
                      "flex flex-col items-center justify-center rounded-lg border py-2.5 px-1 transition-all text-center",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                      is
                        ? "border-primary bg-blue-100 text-blue-800 shadow-sm"
                        : "border-outline-variant bg-surface-container-lowest text-on-surface hover:bg-surface-container-low",
                    ].join(" ")}
                  >
                    <span className="text-[10px] font-semibold uppercase opacity-70 leading-none mb-1">
                      {DAY_LABELS[d.getDay()]}
                    </span>
                    <span className="text-label-md font-bold leading-none">{d.getDate()}</span>
                    <span className="text-[10px] opacity-70 leading-none mt-0.5">
                      {d.toLocaleDateString("vi-VN", { month: "short" })}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Weekend grid */}
        {weekendDays.length > 0 && (
          <div>
            <p className="text-label-sm text-on-surface-variant mb-2 font-semibold">
              Cuối tuần
            </p>
            <div className="grid grid-cols-5 gap-2">
              {weekendDays.map((d) => {
                const iso = toIso(d);
                const is = selected.has(iso);
                return (
                  <button
                    key={iso}
                    type="button"
                    aria-pressed={is}
                    onClick={() => toggle(iso)}
                    className={[
                      "flex flex-col items-center justify-center rounded-lg border py-2.5 px-1 transition-all text-center",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                      is
                        ? "border-tertiary bg-amber-100 text-amber-800 bg-amber-100 text-amber-800 shadow-sm"
                        : "border-outline-variant bg-surface-container-lowest text-on-surface hover:bg-surface-container-low",
                    ].join(" ")}
                  >
                    <span className="text-[10px] font-semibold uppercase opacity-70 leading-none mb-1">
                      {DAY_LABELS[d.getDay()]}
                    </span>
                    <span className="text-label-md font-bold leading-none">{d.getDate()}</span>
                    <span className="text-[10px] opacity-70 leading-none mt-0.5">
                      {d.toLocaleDateString("vi-VN", { month: "short" })}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {futureDays.length === 0 && (
          <div className="flex flex-col items-center justify-center py-12 gap-3">
            <span className="material-symbols-outlined text-5xl text-outline" aria-hidden="true">
              event_busy
            </span>
            <p className="text-label-md text-on-surface-variant">
              Không có ngày nào trong kỳ lịch.
            </p>
          </div>
        )}

        <ModalFooter>
          <Button type="button" variant="secondary" onClick={handleClose}>
            Hủy
          </Button>
          <Button
            type="button"
            variant="primary"
            onClick={handleSubmit}
            disabled={selected.size === 0}
            icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">playlist_add</span>}
          >
            Gán lịch cho {selected.size > 0 ? `${selected.size} ` : ""}ngày
          </Button>
        </ModalFooter>
      </div>
    </Modal>
  );
});
